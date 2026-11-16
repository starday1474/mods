package dev.goddessrpg.rules;

public final class ProficiencyRules {
    private ProficiencyRules() {}

    public static int xpToNext(GrowthTuning t, int level) {
        return (int) Math.round(t.xpBase() * Math.pow(level + 1, t.xpExponent()));
    }

    public static double multiplier(GrowthTuning t, int aptitudeStars, double knowledge) {
        int stars = Stars.clamp(aptitudeStars);
        double base = t.aptitudeMultiplier().getOrDefault(stars, 1.0);
        double k = Math.max(0.0, Math.min(knowledge, t.knowledgeCap()));
        return base + k;
    }

    public static double fieldMultiplier(GrowthTuning t, RpgClass c, Field f, int aptitudeStars, double knowledge) {
        return c.owns(f) ? multiplier(t, aptitudeStars, knowledge) : t.offClassMultiplier();
    }

    public static double guardXp(GrowthTuning t, float blocked) {
        return Math.min(t.guardXpCap(), t.guardXpBase() + blocked / 2.0);
    }

    public static LevelProgress addXp(GrowthTuning t, int level, double xp, double gained) {
        int max = t.maxLevel();
        if (level >= max) {
            return new LevelProgress(max, 0.0);
        }
        int lv = level;
        double cur = xp + Math.max(0.0, gained);
        while (lv < max) {
            int need = xpToNext(t, lv);
            if (cur < need) {
                break;
            }
            cur -= need;
            lv++;
        }
        return lv >= max ? new LevelProgress(max, 0.0) : new LevelProgress(lv, cur);
    }
}
