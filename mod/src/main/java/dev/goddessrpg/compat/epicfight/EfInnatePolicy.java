package dev.goddessrpg.compat.epicfight;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.event.types.player.SkillCastEvent;
import yesman.epicfight.skill.SkillSlots;

/**
 * Players cannot use Epic Fight weapon innate skills (spec 4.2 option C: only dodge and guard remain).
 *
 * Approach chosen: cancel EpicFightEventHooks.Player.CAST_SKILL (CancelableEventHook&lt;SkillCastEvent&gt;) when the
 * container's slot is SkillSlots.WEAPON_INNATE. SkillContainer.requestCasting(ServerPlayerPatch, CompoundTag), the
 * server entry point used by the CPExecuteSkill packet, calls canUse, which posts SkillCastEvent; a cancelled event
 * makes it return false before Skill.executeOnServer. Slot-based, so any innate skill (any weapon, any datapack
 * weapon type) is covered, and dodge/guard/mover are untouched.
 *
 * Not used (fallback only): clearing the WEAPON_INNATE container on equip via
 * EpicFightEventHooks.Player.CHANGE_INNATE_SKILL (non-cancelable notification event) - unnecessary since casting is
 * already refused, and it would desync the client skill UI.
 */
public final class EfInnatePolicy {
    private EfInnatePolicy() {}

    public static void register() {
        EpicFightEventHooks.Player.CAST_SKILL.registerEvent(EfInnatePolicy::onCast);
    }

    static void onCast(SkillCastEvent event) {
        if (event.getSkillContainer().getSlot() != SkillSlots.WEAPON_INNATE || event.isCanceled()) {
            return;
        }
        event.cancel();
        if (event.getPlayerPatch().getOriginal() instanceof ServerPlayer sp && sp.connection != null) {
            sp.displayClientMessage(Component.translatable("goddessrpg.innate.blocked"), true);
        }
    }
}
