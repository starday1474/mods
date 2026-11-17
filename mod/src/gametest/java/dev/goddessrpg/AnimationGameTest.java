package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.compat.epicfight.AnimIndex;
import dev.goddessrpg.compat.epicfight.AnimPlayer;
import dev.goddessrpg.compat.epicfight.RpgAnimations;
import dev.goddessrpg.compat.epicfight.RpgWeaponTypes;
import dev.goddessrpg.item.RpgItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponTypeReloadListener;

@GameTestHolder(GoddessRpg.MODID)
public class AnimationGameTest {
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void balancedAnimationsRegistered(GameTestHelper h) {
        List<AnimationAccessor<?>> ours = List.of(RpgAnimations.BALANCED_AUTO1, RpgAnimations.BALANCED_AUTO2,
                RpgAnimations.BALANCED_AUTO3);
        for (int i = 0; i < ours.size(); i++) {
            AnimationAccessor<?> acc = ours.get(i);
            String key = "goddessrpg:biped/combat/balanced_auto" + (i + 1);
            if (acc == null || acc.get() == null) {
                h.fail("accessor not built: " + key);
                return;
            }
            AnimationAccessor<StaticAnimation> found = AnimationManager.byKey(key);
            if (found == null || found.get() == null || found.id() != acc.id()) {
                h.fail("AnimationManager has no " + key + " (got " + found + ")");
                return;
            }
            if (!acc.registryName().toString().equals(key)) {
                h.fail("registry name " + acc.registryName() + " != " + key);
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void trainingSwordUsesOurType(GameTestHelper h) {
        if (WeaponTypeReloadListener.get(RpgWeaponTypes.BALANCED_SWORD) == null) {
            h.fail("weapon type " + RpgWeaponTypes.BALANCED_SWORD + " not registered");
            return;
        }
        CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(new ItemStack(RpgItems.TRAINING_SWORD.get()));
        if (!(cap instanceof WeaponCapability weapon)) {
            h.fail("training_sword has no weapon capability: " + cap);
            return;
        }
        if (!weapon.hasMatchingTag(RpgWeaponTypes.BALANCED_SWORD)) {
            h.fail("training_sword capability is not " + RpgWeaponTypes.BALANCED_SWORD + ": tags=" + weapon.getTags());
            return;
        }
        if (weapon.getWeaponCategory() != CapabilityItem.WeaponCategories.SWORD) {
            h.fail("category " + weapon.getWeaponCategory());
            return;
        }
        List<AnimationAccessor<? extends AttackAnimation>> combo =
                weapon.getMovesetForStyle(CapabilityItem.Styles.ONE_HAND).getComboAttackAnimations();
        List<AnimationAccessor<?>> want = List.of(RpgAnimations.BALANCED_AUTO1, RpgAnimations.BALANCED_AUTO2,
                RpgAnimations.BALANCED_AUTO3, Animations.SWORD_DASH, Animations.SWORD_AIR_SLASH);
        if (combo.size() != want.size()) {
            h.fail("combo " + combo);
            return;
        }
        for (int i = 0; i < want.size(); i++) {
            if (combo.get(i).id() != want.get(i).id()) {
                h.fail("combo[" + i + "] = " + combo.get(i).registryName() + ", want " + want.get(i).registryName());
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void everyIndexedAnimationRegistered(GameTestHelper h) {
        List<AnimIndex.Entry> entries = AnimIndex.entries();
        if (entries.size() < 57) {
            h.fail("anim index has only " + entries.size() + " entries");
            return;
        }
        for (AnimIndex.Entry e : entries) {
            AnimationAccessor<StaticAnimation> acc = AnimationManager.byKey(e.key());
            if (acc == null || acc.get() == null) {
                h.fail("not registered: " + e.key());
                return;
            }
            StaticAnimation anim = acc.get();
            boolean isAttack = anim instanceof AttackAnimation;
            if (isAttack != (e.kind() == AnimIndex.Kind.ATTACK)) {
                h.fail(e.key() + " kind " + e.kind() + " but class " + anim.getClass().getSimpleName());
                return;
            }
            if (e.kind() == AnimIndex.Kind.LOOP && !anim.isRepeat()) {
                h.fail(e.key() + " is a loop but not repeating");
                return;
            }
            if (e.kind() != AnimIndex.Kind.LOOP && anim.isRepeat()) {
                h.fail(e.key() + " repeats but is " + e.kind());
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void animPlayUnknownNameFailsGracefully(GameTestHelper h) {
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "anim-fake"), ClientInformation.createDefault());
        if (AnimPlayer.play(p, "no_such_animation") != AnimPlayer.Result.UNKNOWN) {
            h.fail("unknown name should give UNKNOWN");
            return;
        }
        try {
            int r = h.getLevel().getServer().getCommands().getDispatcher()
                    .execute("goddessrpg anim play no_such_animation", p.createCommandSourceStack().withPermission(2));
            h.fail("command should fail for an unknown name, returned " + r);
            return;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            if (!expected.getRawMessage().getString().contains("goddessrpg.anim.unknown")
                    && !expected.getMessage().contains("no_such_animation")) {
                h.fail("unexpected message " + expected.getMessage());
                return;
            }
        }
        // a real name must not throw either, even if the fake player has no connection
        AnimPlayer.Result known = AnimPlayer.play(p, AnimIndex.entries().get(0).name());
        if (known == AnimPlayer.Result.UNKNOWN || known == AnimPlayer.Result.NOT_REGISTERED) {
            h.fail("known animation rejected: " + known);
            return;
        }
        h.succeed();
    }
}
