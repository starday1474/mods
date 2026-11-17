package dev.goddessrpg.compat.epicfight;

import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.ServerAnimator;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/**
 * Test playback of our animations. Epic Fight 21.17.3.1 API: {@code AnimationManager.byKey(String)} ->
 * {@code AnimationAccessor}, {@code EpicFightCapabilities.getServerPlayerPatch(ServerPlayer)},
 * {@code ServerPlayerPatch.playAnimationSynchronized(accessor, convertTimeModifier)} (plays on the server and sends it to the clients).
 */
public final class AnimPlayer {
    private AnimPlayer() {}

    public enum Result { OK, UNKNOWN, NOT_REGISTERED, NO_PATCH }

    public enum StopResult { STOPPED, NOTHING_PLAYING, NO_PATCH }

    /** Attack speed the attack animations are authored for (RpgAnimations BASIS_ATTACK_SPEED, a sword). */
    public static final double BASIS_ATTACK_SPEED = 1.6;

    /** True when the entry is an attack animation and the player's attack speed (main-hand item included) is not ~1.6. */
    public static boolean attackSpeedMismatch(AnimIndex.Entry entry, double attackSpeed) {
        return entry.kind() == AnimIndex.Kind.ATTACK && Math.abs(attackSpeed - BASIS_ATTACK_SPEED) > 0.05;
    }

    public static boolean attackSpeedMismatch(ServerPlayer player, String name) {
        AnimIndex.Entry entry = AnimIndex.find(name);
        return entry != null && attackSpeedMismatch(entry, player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
    }

    public static Result play(ServerPlayer player, String name) {
        AnimIndex.Entry entry = AnimIndex.find(name);
        if (entry == null) {
            return Result.UNKNOWN;
        }
        AnimationAccessor<StaticAnimation> acc = AnimationManager.byKey(entry.key());
        if (acc == null || acc.get() == null) {
            return Result.NOT_REGISTERED;
        }
        ServerPlayerPatch patch = EpicFightCapabilities.getServerPlayerPatch(player);
        if (patch == null) {
            return Result.NO_PATCH;
        }
        patch.playAnimationSynchronized(acc, 0.0F);
        return Result.OK;
    }

    /**
     * Stops whatever animation the player is playing: reads the current base-layer animation from the server animator,
     * stops it, then plays the idle animation synchronized so the clients drop it too (living motions take over after).
     */
    public static StopResult stop(ServerPlayer player) {
        ServerPlayerPatch patch = EpicFightCapabilities.getServerPlayerPatch(player);
        if (patch == null) {
            return StopResult.NO_PATCH;
        }
        Object animator = patch.getAnimator();
        if (!(animator instanceof ServerAnimator server)) {
            return StopResult.NOTHING_PLAYING;
        }
        AnimationPlayer ap = server.animationPlayer;
        if (ap == null || ap.isEmpty() || ap.getRealAnimation() == null) {
            return StopResult.NOTHING_PLAYING;
        }
        yesman.epicfight.api.asset.AssetAccessor<? extends StaticAnimation> current = ap.getRealAnimation();
        if (current.get() == null || current.equals(Animations.BIPED_IDLE)) {
            return StopResult.NOTHING_PLAYING;
        }
        patch.stopPlaying(current);
        patch.playAnimationSynchronized(Animations.BIPED_IDLE, 0.0F);
        return StopResult.STOPPED;
    }
}
