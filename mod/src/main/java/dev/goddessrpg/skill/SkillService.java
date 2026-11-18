package dev.goddessrpg.skill;

import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.rules.SkillRules;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-side learning and slot handling. All rules are re-checked here; clients are never trusted. */
public final class SkillService {
    private SkillService() {}

    public enum LearnResult { OK, ALREADY, NOT_ALLOWED, UNKNOWN }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                grantMetProficiencySkills(sp);
            }
        });
    }

    public static LearnResult learn(ServerPlayer player, String id) {
        Optional<SkillDef> def = SkillRegistry.get(id);
        if (def.isEmpty()) {
            return LearnResult.UNKNOWN;
        }
        RpgPlayerData data = RpgAttachments.get(player);
        if (data.learnedSkills().contains(id)) {
            return LearnResult.ALREADY;
        }
        if (!SkillRules.canLearn(def.get(), data)) {
            return LearnResult.NOT_ALLOWED;
        }
        RpgPlayerData next = data.withLearned(id);
        if (def.get().kind() == SkillDef.Kind.ACTIVE) {
            int free = next.slots().indexOf(Optional.<String>empty());
            if (free >= 0) {
                next = next.withSlot(free, Optional.of(id));
            }
        }
        RpgAttachments.set(player, next);
        if (player.connection != null) {
            player.sendSystemMessage(Component.translatable("goddessrpg.skill.learned", skillName(id)), true);
        }
        return LearnResult.OK;
    }

    public static boolean setSlot(ServerPlayer player, int index, Optional<String> id) {
        try {
            RpgAttachments.set(player, RpgAttachments.get(player).withSlot(index, id));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Learns every proficiency-unlock skill the player already meets and has not learned. Call after anything that
     * can change levels or class (inquiry result, login, level-up, level command).
     */
    public static void grantMetProficiencySkills(ServerPlayer player) {
        RpgPlayerData data = RpgAttachments.get(player);
        if (!data.inquiryDone()) {
            return;
        }
        for (SkillDef s : SkillRegistry.all().values()) {
            if (s.unlock() instanceof SkillDef.Unlock.Proficiency p && data.levelOf(p.field()) >= p.level()
                    && !data.learnedSkills().contains(s.id())) {
                learn(player, s.id());
            }
        }
    }

    public static Component skillName(String id) {
        return Component.translatable("goddessrpg.skill." + id);
    }
}
