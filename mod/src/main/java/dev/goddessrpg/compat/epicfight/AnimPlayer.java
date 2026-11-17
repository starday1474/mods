package dev.goddessrpg.compat.epicfight;

import net.minecraft.server.level.ServerPlayer;
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
}
