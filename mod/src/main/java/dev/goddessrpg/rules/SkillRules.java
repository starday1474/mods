package dev.goddessrpg.rules;

import dev.goddessrpg.player.RpgPlayerData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class SkillRules {
    private SkillRules() {}

    public static boolean canLearn(SkillDef s, RpgPlayerData d) {
        if (!d.inquiryDone() || d.rpgClass().filter(c -> c == s.rpgClass()).isEmpty()) {
            return false;
        }
        return s.subtype().isEmpty() || s.subtype().equals(d.subtype());
    }

    private static boolean meets(SkillDef.Unlock.Proficiency p, RpgPlayerData d) {
        return d.levelOf(p.field()) >= p.level();
    }

    /** Skills whose proficiency condition this change met for the first time, that are learnable and not yet learned. */
    public static List<SkillDef> newlyUnlockedByProficiency(Collection<SkillDef> all, RpgPlayerData before,
                                                           RpgPlayerData after, Set<String> learned) {
        List<SkillDef> out = new ArrayList<>();
        for (SkillDef s : all) {
            if (s.unlock() instanceof SkillDef.Unlock.Proficiency p
                    && !meets(p, before) && meets(p, after)
                    && canLearn(s, after) && !learned.contains(s.id())) {
                out.add(s);
            }
        }
        return out;
    }

    public static double cooldownRemaining(SkillDef s, long lastCastTick, long nowTick) {
        return Math.max(0.0, s.cooldownSec() - (nowTick - lastCastTick) / 20.0);
    }
}
