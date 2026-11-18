package dev.goddessrpg.compat.epicfight;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/**
 * Epic Fight side of skill casting. Epic Fight 21.17.3.1 API used:
 * {@code EpicFightCapabilities.getServerPlayerPatch(ServerPlayer)};
 * stamina: {@code PlayerPatch.getStamina()}, {@code setStamina(float)} (clamped to max, synced through EF's expanded
 * entity data). Skill costs are flat, NOT passed through {@code getModifiedStaminaConsume(float)}: that scales by armor
 * weight ((weight/40 - 1) * penalty + 1) and makes casts free for an unarmored player;
 * busy: {@code LivingEntityPatch.getEntityState()} -> {@code EntityState.attacking()};
 * playback: {@code AnimationManager.byKey(String)} -> {@code AnimationAccessor},
 * {@code LivingEntityPatch.playAnimationSynchronized(AssetAccessor, float)}, {@code StaticAnimation.getTotalTime()}.
 *
 * <p>A skill's {@code animation} is either a full id ({@code epicfightx:biped/combat/sword_dash},
 * {@code goddessrpg:biped/combat/balanced_shield_bash}) or a bare name from {@link AnimIndex}.
 */
public final class SkillAnimations {
    private SkillAnimations() {}

    /** Used when the length is unknown (unregistered animation, clip not loaded on this side). */
    public static final int DEFAULT_TICKS = 20;
    private static final int MAX_TICKS = 60;

    public static Optional<ServerPlayerPatch> patch(ServerPlayer player) {
        return Optional.ofNullable(EpicFightCapabilities.getServerPlayerPatch(player));
    }

    /** True when another attack animation is playing (its state says {@code attacking}). */
    public static boolean busy(ServerPlayer player) {
        return patch(player).map(p -> {
            EntityState s = p.getEntityState();
            return s != null && s.attacking();
        }).orElse(false);
    }

    /** True when the player can pay {@code cost} stamina; players without a patch have no stamina system. */
    public static boolean hasStamina(ServerPlayer player, double cost) {
        if (cost <= 0) {
            return true;
        }
        return patch(player).map(p -> p.getStamina() >= (float) cost).orElse(true);
    }

    public static void consumeStamina(ServerPlayer player, double cost) {
        if (cost <= 0) {
            return;
        }
        patch(player).ifPresent(p -> p.setStamina(Math.max(0F, p.getStamina() - (float) cost)));
    }

    private static String key(String animation) {
        if (animation.contains(":")) {
            return animation;
        }
        AnimIndex.Entry e = AnimIndex.find(animation);
        return e == null ? null : e.key();
    }

    public static Optional<AnimationAccessor<StaticAnimation>> resolve(String animation) {
        String key = key(animation);
        if (key == null) {
            return Optional.empty();
        }
        try {
            AnimationAccessor<StaticAnimation> acc = AnimationManager.byKey(key);
            return acc == null || acc.get() == null ? Optional.empty() : Optional.of(acc);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Animation length in ticks (index length for ours, else EF's total time), clamped to 1..3 s. */
    public static int lengthTicks(String animation) {
        float sec = 0;
        if (!animation.contains(":") || animation.startsWith(dev.goddessrpg.GoddessRpg.MODID + ":")) {
            String name = animation.substring(animation.lastIndexOf('/') + 1);
            name = name.substring(name.indexOf(':') + 1);
            AnimIndex.Entry e = AnimIndex.find(name);
            if (e != null) {
                sec = e.length();
            }
        }
        if (sec <= 0) {
            sec = resolve(animation).map(a -> a.get().getTotalTime()).orElse(0F);
        }
        int ticks = sec > 0 ? Math.round(sec * 20F) : DEFAULT_TICKS;
        return Math.max(1, Math.min(MAX_TICKS, ticks));
    }

    /** Plays the animation on the server and the tracking clients; false when it does not resolve or there is no patch. */
    public static boolean play(ServerPlayer player, String animation) {
        Optional<AnimationAccessor<StaticAnimation>> acc = resolve(animation);
        Optional<ServerPlayerPatch> patch = patch(player);
        if (acc.isEmpty() || patch.isEmpty()) {
            return false;
        }
        patch.get().playAnimationSynchronized(acc.get(), 0.0F);
        return true;
    }
}
