package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.compat.epicfight.RpgWeaponTypes;
import dev.goddessrpg.compat.epicfight.SkillAnimations;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.rules.StarResult;
import dev.goddessrpg.skill.SkillRegistry;
import dev.goddessrpg.skill.SkillService;
import dev.goddessrpg.skill.SkillService.CastResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponTypeReloadListener;

@GameTestHolder(GoddessRpg.MODID)
public class BalancedKitGameTest {
    static final List<String> BALANCED_SKILLS = List.of("balanced_shield_bash", "balanced_dash_slash", "balanced_counter",
            "balanced_steadfast", "vanguard_sword_mastery", "vanguard_shield_mastery");

    private static void expect(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static List<AnimationAccessor<? extends AttackAnimation>> combo(ItemStack stack) {
        CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(stack);
        if (!(cap instanceof WeaponCapability weapon)) {
            return null;
        }
        return weapon.getMovesetForStyle(CapabilityItem.Styles.ONE_HAND).getComboAttackAnimations();
    }

    private static String names(List<? extends AnimationAccessor<?>> list) {
        return list.stream().map(a -> String.valueOf(a.registryName())).toList().toString();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void balancedMovesetUsesEfxCombo(GameTestHelper h) {
        List<AnimationAccessor<? extends AttackAnimation>> combo = combo(new ItemStack(RpgItems.TRAINING_SWORD.get()));
        expect(h, combo != null && !combo.isEmpty(), "training_sword has no one-hand combo");
        AnimationAccessor<StaticAnimation> want = AnimationManager.byKey("epicfightx:biped/combat/sword_auto1");
        expect(h, want != null && want.get() != null, "epicfightx:biped/combat/sword_auto1 is not registered (EF-Extra loaded?)");
        expect(h, want.get() instanceof AttackAnimation, "sword_auto1 is not an attack animation: " + want.get());
        expect(h, combo.get(0).id() == want.id(), "combo[0] = " + combo.get(0).registryName() + ", combo " + names(combo));
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void badMovesetIdFallsBack(GameTestHelper h) {
        ResourceLocation type = ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "test_bad_sword");
        var preset = WeaponTypeReloadListener.get(type);
        expect(h, preset != null, "test-only weapon type " + type + " not registered");
        CapabilityItem cap = preset.apply(Items.IRON_SWORD).build();
        expect(h, cap instanceof WeaponCapability, "no weapon capability: " + cap);
        List<AnimationAccessor<? extends AttackAnimation>> combo =
                ((WeaponCapability) cap).getMovesetForStyle(CapabilityItem.Styles.ONE_HAND).getComboAttackAnimations();
        List<AnimationAccessor<?>> want = List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3,
                Animations.SWORD_DASH, Animations.SWORD_AIR_SLASH);
        expect(h, combo.size() == want.size(), "combo " + names(combo));
        for (int i = 0; i < want.size(); i++) {
            expect(h, combo.get(i).id() == want.get(i).id(),
                    "combo[" + i + "] = " + combo.get(i).registryName() + ", want " + want.get(i).registryName());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void balancedSkillsLoad(GameTestHelper h) {
        for (String id : BALANCED_SKILLS) {
            Optional<SkillDef> def = SkillRegistry.get(id);
            expect(h, def.isPresent(), "skill not loaded: " + id);
            expect(h, def.get().rpgClass() == RpgClass.VANGUARD, id + " class " + def.get().rpgClass());
            def.get().animation().ifPresent(a ->
                    expect(h, SkillAnimations.resolve(a).isPresent(), id + " animation does not resolve: " + a));
            expect(h, net.minecraft.locale.Language.getInstance().has("goddessrpg.skill." + id), "no name for " + id);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void shieldBashKnocksBack(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, 1, 2, 1);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-bash-player"), ClientInformation.createDefault());
        ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, p, 0, "spawnInvulnerableTime");
        p.setPos(z.getX() + 1.5, z.getY(), z.getZ()); // the zombie is in front (-x), moveTo would need a connection
        RpgPlayerData d = RpgPlayerData.EMPTY.withResult(RpgClass.VANGUARD, "balanced", new StarResult(Map.of(), Map.of()),
                List.of(), GrowthTuning.DEFAULT);
        RpgAttachments.set(p, new RpgPlayerData(d.inquiryDone(), d.rpgClass(), d.subtype(), d.talent(), d.aptitude(), d.level(),
                d.xp(), d.tomesRead(), d.knowledge(), d.inquiryAnswers(), Set.of("balanced_shield_bash"),
                List.of(Optional.of("balanced_shield_bash"), Optional.empty(), Optional.empty(), Optional.empty())));
        ServerPlayerPatch pp = EpicFightCapabilities.getServerPlayerPatch(p);
        expect(h, pp != null, "no Epic Fight patch");
        pp.setStamina(pp.getMaxStamina());
        expect(h, SkillService.cast(p, 0) == CastResult.OK, "cast");
        // the bash connects (a fake player does not tick its animation, so the hit is dealt here)
        z.setDeltaMovement(Vec3.ZERO);
        z.invulnerableTime = 0;
        z.hurt(h.getLevel().damageSources().playerAttack(p), 1.0F);
        Vec3 v = z.getDeltaMovement();
        expect(h, v.x < -0.5, "zombie not pushed away from the player: " + v);
        var slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        expect(h, slow != null && slow.getDuration() >= 19 && slow.getDuration() <= 20, "no 1 s Slowness: " + slow);
        h.succeed();
    }
}
