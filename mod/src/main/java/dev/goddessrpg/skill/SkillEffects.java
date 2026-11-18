package dev.goddessrpg.skill;

import com.mojang.logging.LogUtils;
import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.compat.epicfight.SkillAnimations;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.rules.SkillRules;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

/**
 * Skill effects. Parameter keys per effect type are fixed (task 4 brief):
 * <ul>
 *   <li>{@code animation_strike}: {@code damage_mult}. Plays the skill animation; ATTACK_DAMAGE x damage_mult for the
 *   animation's length (+2 ticks).</li>
 *   <li>{@code knockback_stun}: {@code knockback}, {@code stun_sec}, {@code damage_mult}. As animation_strike; living
 *   targets hit by the caster during it are knocked back and get Slowness IV for stun_sec.</li>
 *   <li>{@code counter_window}: {@code window_sec}, {@code damage_mult}. The first hit from an attacker within the window
 *   is cancelled and answered with the skill animation plus the strike multiplier.</li>
 *   <li>{@code buff}: {@code duration_sec}, {@code damage_taken_mult}, {@code move_speed_mult}.</li>
 *   <li>{@code passive_attribute}: {@code attack_damage_pct} (percent), {@code armor_flat}, {@code requires}
 *   ({@code "sword"} or {@code "shield_offhand"}, a string param).</li>
 * </ul>
 * Temporary modifiers are transient and are removed by server game time ({@link ServerTickEvent}), so an interrupted
 * animation never leaves a multiplier behind. Passive modifiers use one stable id per skill and attribute, so
 * re-evaluating never stacks them.
 *
 * <p>Damage hooks are NeoForge's {@code LivingIncomingDamageEvent} (Epic Fight attacks go through
 * {@code LivingEntity.hurt} too) and {@code LivingDamageEvent.Post}. Epic Fight 21.17.3.1:
 * {@code EpicFightCapabilities.getItemStackCapability(ItemStack).getWeaponCategory()} for the "sword" requirement;
 * animation/stamina calls are in {@link SkillAnimations}.
 */
public final class SkillEffects {
    private SkillEffects() {}

    private static final Logger LOG = LogUtils.getLogger();

    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, GoddessRpg.MODID);

    /** Multiplier on all damage the player takes (1 = normal). */
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_TAKEN = ATTRIBUTES.register("damage_taken",
            () -> new RangedAttribute("attribute.name.goddessrpg.damage_taken", 1.0, 0.0, 10.0).setSyncable(true));

    private static final ResourceLocation STRIKE_ID = id("skill_strike");
    private static final ResourceLocation BUFF_DAMAGE_TAKEN_ID = id("skill_buff/damage_taken");
    private static final ResourceLocation BUFF_SPEED_ID = id("skill_buff/move_speed");
    private static final String PASSIVE_PREFIX = "passive/";
    private static final int STRIKE_MARGIN_TICKS = 2;
    private static final Set<String> SWORD_CATEGORIES = Set.of("sword", "longsword", "greatsword", "tachi", "uchigatana");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, path);
    }

    /** Per-player transient combat state (not saved; lost on logout, which also drops transient modifiers). */
    private static final class Runtime {
        long strikeUntil = Long.MIN_VALUE;
        double knockback;
        double stunSec;
        long counterUntil = Long.MIN_VALUE;
        double counterMult = 1.0;
        Optional<String> counterAnimation = Optional.empty();
    }

    private record Expiry(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, long due) {}

    private static final Map<UUID, Runtime> RUNTIME = new ConcurrentHashMap<>();
    private static final Map<String, Expiry> EXPIRIES = new ConcurrentHashMap<>();

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent e) -> e.add(EntityType.PLAYER, DAMAGE_TAKEN));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tickExpiries(e.getServer()));
        NeoForge.EVENT_BUS.addListener(SkillEffects::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SkillEffects::onDamageDealt);
        NeoForge.EVENT_BUS.addListener((LivingEquipmentChangeEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                refreshPassives(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                refreshPassives(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                refreshPassives(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            RUNTIME.clear();
            EXPIRIES.clear();
        });
    }

    private static long now(ServerPlayer p) {
        return SkillCooldowns.now(p);
    }

    private static Runtime runtime(ServerPlayer p) {
        return RUNTIME.computeIfAbsent(p.getUUID(), u -> new Runtime());
    }

    private static void forget(UUID id) {
        RUNTIME.remove(id);
        EXPIRIES.keySet().removeIf(k -> k.startsWith(id + "|"));
    }

    /** Applies a validated, already-paid cast. */
    public static void apply(ServerPlayer player, SkillDef def) {
        SkillDef.Effect e = def.effect();
        switch (e.type()) {
            case "animation_strike" -> strike(player, def.animation(), e.param("damage_mult", 1.0), 0, 0);
            case "knockback_stun" -> strike(player, def.animation(), e.param("damage_mult", 1.0),
                    e.param("knockback", 0), e.param("stun_sec", 0));
            case "counter_window" -> {
                Runtime r = runtime(player);
                r.counterUntil = now(player) + Math.round(e.param("window_sec", 0) * 20.0);
                r.counterMult = e.param("damage_mult", 1.0);
                r.counterAnimation = def.animation();
            }
            case "buff" -> buff(player, def, e);
            case "passive_attribute" -> refreshPassives(player);
            default -> LOG.warn("Skill {} has unknown effect type {}", def.id(), e.type());
        }
    }

    private static void strike(ServerPlayer player, Optional<String> animation, double mult, double knockback, double stunSec) {
        int ticks = animation.map(SkillAnimations::lengthTicks).orElse(SkillAnimations.DEFAULT_TICKS);
        animation.ifPresent(a -> {
            if (!SkillAnimations.play(player, a)) {
                LOG.warn("Skill animation {} could not be played", a);
            }
        });
        long until = now(player) + ticks + STRIKE_MARGIN_TICKS;
        timedModifier(player, Attributes.ATTACK_DAMAGE, STRIKE_ID, mult - 1.0, until);
        Runtime r = runtime(player);
        r.strikeUntil = until;
        r.knockback = knockback;
        r.stunSec = stunSec;
    }

    private static void buff(ServerPlayer player, SkillDef def, SkillDef.Effect e) {
        long until = now(player) + Math.round(e.param("duration_sec", 0) * 20.0);
        double taken = e.param("damage_taken_mult", 1.0);
        double speed = e.param("move_speed_mult", 1.0);
        if (taken != 1.0) {
            timedModifier(player, DAMAGE_TAKEN, BUFF_DAMAGE_TAKEN_ID, taken - 1.0, until);
        }
        if (speed != 1.0) {
            timedModifier(player, Attributes.MOVEMENT_SPEED, BUFF_SPEED_ID, speed - 1.0, until);
        }
        def.animation().ifPresent(a -> SkillAnimations.play(player, a));
    }

    /** (Re)applies a transient ADD_MULTIPLIED_TOTAL modifier that is removed at game time {@code until}. */
    private static void timedModifier(ServerPlayer player, Holder<Attribute> attr, ResourceLocation id, double amount, long until) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        EXPIRIES.put(player.getUUID() + "|" + id, new Expiry(player, attr, id, until));
    }

    private static void tickExpiries(MinecraftServer server) {
        if (EXPIRIES.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        EXPIRIES.entrySet().removeIf(en -> {
            Expiry x = en.getValue();
            if (x.due() > now) {
                return false;
            }
            AttributeInstance inst = x.player().getAttribute(x.attribute());
            if (inst != null) {
                inst.removeModifier(x.id());
            }
            return true;
        });
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Runtime r = RUNTIME.get(player.getUUID());
        var attacker = e.getSource().getEntity();
        if (r != null && attacker != null && attacker != player && r.counterUntil >= now(player)) {
            r.counterUntil = Long.MIN_VALUE;
            e.setCanceled(true);
            strike(player, r.counterAnimation, r.counterMult, 0, 0);
            return;
        }
        AttributeInstance taken = player.getAttribute(DAMAGE_TAKEN);
        if (taken != null && taken.getValue() != 1.0) {
            e.setAmount((float) (e.getAmount() * taken.getValue()));
        }
    }

    private static void onDamageDealt(LivingDamageEvent.Post e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer player) || e.getEntity() == player) {
            return;
        }
        Runtime r = RUNTIME.get(player.getUUID());
        if (r == null || r.strikeUntil < now(player) || (r.knockback <= 0 && r.stunSec <= 0)) {
            return;
        }
        LivingEntity target = e.getEntity();
        if (r.knockback > 0) {
            target.knockback(r.knockback, player.getX() - target.getX(), player.getZ() - target.getZ());
            target.hurtMarked = true;
        }
        if (r.stunSec > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) Math.round(r.stunSec * 20.0), 3), player);
        }
    }

    // ---- passives ----

    private record Wanted(Holder<Attribute> attribute, AttributeModifier modifier) {}

    private static final List<Holder<Attribute>> PASSIVE_ATTRIBUTES = List.of(Attributes.ATTACK_DAMAGE, Attributes.ARMOR);

    /**
     * Re-evaluates every {@code passive_attribute} skill: applied while learned, still valid for the player's class and
     * its {@code requires} condition holds; removed otherwise. Idempotent.
     */
    public static void refreshPassives(ServerPlayer player) {
        RpgPlayerData data = RpgAttachments.get(player);
        Map<ResourceLocation, Wanted> want = new HashMap<>();
        for (SkillDef def : SkillRegistry.all().values()) {
            SkillDef.Effect e = def.effect();
            if (def.kind() != SkillDef.Kind.PASSIVE || !e.type().equals("passive_attribute")
                    || !data.learnedSkills().contains(def.id()) || !SkillRules.canLearn(def, data)
                    || !requirementMet(player, e.text("requires"))) {
                continue;
            }
            double pct = e.param("attack_damage_pct", 0);
            if (pct != 0) {
                ResourceLocation rid = id(PASSIVE_PREFIX + def.id() + "/attack_damage");
                want.put(rid, new Wanted(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(rid, pct / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
            }
            double armor = e.param("armor_flat", 0);
            if (armor != 0) {
                ResourceLocation rid = id(PASSIVE_PREFIX + def.id() + "/armor");
                want.put(rid, new Wanted(Attributes.ARMOR, new AttributeModifier(rid, armor, AttributeModifier.Operation.ADD_VALUE)));
            }
        }
        for (Holder<Attribute> attr : PASSIVE_ATTRIBUTES) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            for (AttributeModifier m : List.copyOf(inst.getModifiers())) {
                if (m.id().getNamespace().equals(GoddessRpg.MODID) && m.id().getPath().startsWith(PASSIVE_PREFIX)) {
                    Wanted w = want.get(m.id());
                    if (w == null || !w.modifier().equals(m)) {
                        inst.removeModifier(m.id());
                    }
                }
            }
        }
        want.forEach((rid, w) -> {
            AttributeInstance inst = player.getAttribute(w.attribute());
            if (inst != null && !inst.hasModifier(rid)) {
                inst.addTransientModifier(w.modifier());
            }
        });
    }

    private static boolean requirementMet(ServerPlayer player, Optional<String> requires) {
        if (requires.isEmpty()) {
            return true;
        }
        return switch (requires.get()) {
            case "sword" -> isSword(player.getMainHandItem());
            case "shield_offhand" -> {
                ItemStack off = player.getOffhandItem();
                yield off.getItem() instanceof ShieldItem || off.canPerformAction(ItemAbilities.SHIELD_BLOCK);
            }
            default -> false;
        };
    }

    private static boolean isSword(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof SwordItem) {
            return true;
        }
        var cap = EpicFightCapabilities.getItemStackCapability(stack);
        return cap != null && cap.getWeaponCategory() != null
                && SWORD_CATEGORIES.contains(cap.getWeaponCategory().toString().toLowerCase(Locale.ROOT));
    }
}
