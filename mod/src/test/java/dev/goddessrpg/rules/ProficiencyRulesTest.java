package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProficiencyRulesTest {
    private static final GrowthTuning T = GrowthTuning.DEFAULT;

    @Test
    void xpToNext() {
        assertEquals(40, ProficiencyRules.xpToNext(T, 0));
        assertEquals(1592, ProficiencyRules.xpToNext(T, 9));
    }

    @Test
    void multiplier() {
        assertEquals(1.0, ProficiencyRules.multiplier(T, 3, 0.0), 1e-9);
        assertEquals(2.0, ProficiencyRules.multiplier(T, 5, 0.5), 1e-9);
        assertEquals(0.6 + 0.5, ProficiencyRules.multiplier(T, 1, 0.9), 1e-9);
    }

    @Test
    void fieldMultiplier() {
        assertEquals(2.0, ProficiencyRules.fieldMultiplier(T, RpgClass.VANGUARD, Field.SWORD, 5, 0.5), 1e-9);
        assertEquals(0.3, ProficiencyRules.fieldMultiplier(T, RpgClass.VANGUARD, Field.ARCHERY, 5, 0.5), 1e-9);
    }

    @Test
    void guardXp() {
        assertEquals(4.0, ProficiencyRules.guardXp(T, 4f), 1e-9);
        assertEquals(10.0, ProficiencyRules.guardXp(T, 100f), 1e-9);
    }

    @Test
    void addXpLevelsUp() {
        assertEquals(new LevelProgress(1, 5.0), ProficiencyRules.addXp(T, 0, 30, 15));
    }

    @Test
    void addXpMultipleLevels() {
        // 40 (L0->1) + 40*2^1.6=121 (L1->2) = 161; 200 - 161 = 39 left
        assertEquals(new LevelProgress(2, 39.0), ProficiencyRules.addXp(T, 0, 0, 200));
    }

    @Test
    void addXpCapsAtMax() {
        assertEquals(new LevelProgress(50, 0.0), ProficiencyRules.addXp(T, 50, 0, 999));
        assertEquals(new LevelProgress(50, 0.0), ProficiencyRules.addXp(T, 49, 0, 1_000_000));
    }
}
