package dev.goddessrpg.growth;

import dev.goddessrpg.compat.epicfight.EfGuardHook;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.LevelProgress;
import dev.goddessrpg.rules.ProficiencyRules;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

/** Proficiency XP from weapon hits and guards. */
public final class GrowthEvents {
    private GrowthEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GrowthEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(GrowthEvents::onShieldBlock);
        EfGuardHook.register();
    }

    /** Adds {@code baseXp} (times the field multiplier) to a field, saves, syncs and announces level-ups. */
    public static void grant(ServerPlayer player, Field field, double baseXp) {
        RpgPlayerData data = RpgAttachments.get(player);
        if (!data.inquiryDone() || data.rpgClass().isEmpty()) {
            return;
        }
        GrowthTuning t = RpgDataLoader.tuning();
        double mult = ProficiencyRules.fieldMultiplier(t, data.rpgClass().get(), field, data.aptitudeOf(field),
                data.knowledge());
        int before = data.levelOf(field);
        LevelProgress p = ProficiencyRules.addXp(t, before, data.xpOf(field), baseXp * mult);
        RpgAttachments.set(player, data.withXp(field, p));
        onLevelChange(player, field, before, p.level());
    }

    /** If the level rose: auto-learns newly met skills and shows the level-up actionbar message. */
    public static void onLevelChange(ServerPlayer player, Field field, int before, int after) {
        if (after <= before) {
            return;
        }
        dev.goddessrpg.skill.SkillService.grantMetProficiencySkills(player);
        if (player.connection != null) {
            player.sendSystemMessage(Component.translatable("goddessrpg.growth.levelup",
                    Component.translatable("goddessrpg.field." + field.name().toLowerCase(Locale.ROOT)), after), true);
        }
    }

    /** Guard success (Epic Fight guard or vanilla shield): defense XP by the damage that was blocked. */
    public static void grantGuard(ServerPlayer player, float blocked) {
        grant(player, Field.DEFENSE, ProficiencyRules.guardXp(RpgDataLoader.tuning(), blocked));
    }

    private static void onDamage(LivingDamageEvent.Post e) {
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player) || src.getDirectEntity() != player) {
            return;
        }
        LivingEntity target = e.getEntity();
        if (target instanceof Player || target instanceof ArmorStand) {
            return;
        }
        categoryName(player.getMainHandItem()).flatMap(WeaponFields::fieldFor)
                .ifPresent(f -> grant(player, f, RpgDataLoader.tuning().hitXp()));
    }

    private static Optional<String> categoryName(ItemStack stack) {
        var cap = EpicFightCapabilities.getItemStackCapability(stack);
        if (cap == null || cap.getWeaponCategory() == null) {
            return Optional.empty();
        }
        return Optional.of(cap.getWeaponCategory().toString());
    }

    private static void onShieldBlock(LivingShieldBlockEvent e) {
        if (e.getEntity() instanceof ServerPlayer player && e.getBlocked()) {
            grantGuard(player, e.getBlockedDamage());
        }
    }
}
