package dev.goddessrpg.compat.epicfight;

import dev.goddessrpg.growth.GrowthEvents;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.event.types.entity.TakeDamageEvent;
import yesman.epicfight.api.utils.AttackResult;

/**
 * Epic Fight guard success. GuardSkill.dealEvent cancels EpicFightEventHooks.Entity.TAKE_DAMAGE_INCOME and sets its
 * result to BLOCKED. Listeners run in descending priority; GuardSkill registers at priority 1, so this context-aware
 * listener (which also fires for cancelled events) at priority -10 sees the final result.
 */
public final class EfGuardHook {
    private EfGuardHook() {}

    public static void register() {
        EpicFightEventHooks.Entity.TAKE_DAMAGE_INCOME.registerContextAwareEvent((event, ctx) -> onIncoming(event), -10);
    }

    private static void onIncoming(TakeDamageEvent.Income event) {
        if (event.isCanceled() && event.getResult() == AttackResult.ResultType.BLOCKED
                && event.getEntityPatch().getOriginal() instanceof ServerPlayer player) {
            GrowthEvents.grantGuard(player, event.getDamage());
        }
    }
}
