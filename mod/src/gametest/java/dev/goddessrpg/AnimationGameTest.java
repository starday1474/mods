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
    private static java.nio.file.Path findAnimDir() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent()) {
            java.nio.file.Path a = dir.resolve("mod/anim");
            if (java.nio.file.Files.isDirectory(a)) return a;
            a = dir.resolve("anim");
            if (java.nio.file.Files.isDirectory(a) && java.nio.file.Files.exists(dir.resolve("build.gradle"))) return a;
        }
        return java.nio.file.Path.of("anim");
    }

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
        long sources;
        try (var files = java.nio.file.Files.list(findAnimDir())) {
            sources = files.filter(f -> f.getFileName().toString().endsWith(".json")).count();
        } catch (java.io.IOException | RuntimeException ex) {
            sources = -1; // source dir not reachable from the test run dir; fall back to a non-empty check
        }
        if (entries.isEmpty() || (sources > 0 && entries.size() != sources)) {
            h.fail("anim index has " + entries.size() + " entries, sources " + sources);
            return;
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (AnimIndex.Entry e : entries) {
            if (!names.add(e.name())) {
                h.fail("duplicate index name " + e.name());
                return;
            }
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

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void animStopWithNothingPlayingDoesNotThrow(GameTestHelper h) {
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "anim-stop-fake"), ClientInformation.createDefault());
        AnimPlayer.stop(p);
        try {
            h.getLevel().getServer().getCommands().getDispatcher()
                    .execute("goddessrpg anim stop", p.createCommandSourceStack().withSource(net.minecraft.commands.CommandSource.NULL).withPermission(2));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            if (!ex.getMessage().contains("no_patch_stop")) {
                h.fail("unexpected failure " + ex.getMessage());
                return;
            }
        }
        h.succeed();
    }
}
