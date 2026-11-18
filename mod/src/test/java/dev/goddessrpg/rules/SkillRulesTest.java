package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.SkillDef.Effect;
import dev.goddessrpg.rules.SkillDef.Kind;
import dev.goddessrpg.rules.SkillDef.MapPos;
import dev.goddessrpg.rules.SkillDef.Unlock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillRulesTest {
    private static SkillDef skill(String id, RpgClass c, Optional<String> sub, Unlock u, double cd) {
        return new SkillDef(id, c, sub, Field.SWORD, Kind.ACTIVE, u, cd, 10, 0, Optional.of("anim"),
                new Effect("damage", Map.of("amount", 4.0)), new MapPos(1, -2));
    }

    private static RpgPlayerData player(RpgClass c, String sub, int swordLevel) {
        return RpgPlayerData.EMPTY.withResult(c, sub, new StarResult(Map.of(), Map.of()), List.of(), GrowthTuning.DEFAULT)
                .withXp(Field.SWORD, new LevelProgress(swordLevel, 0));
    }

    @Test
    void canLearnRules() {
        RpgPlayerData balanced = player(RpgClass.VANGUARD, "balanced", 0);
        SkillDef plain = skill("a", RpgClass.VANGUARD, Optional.empty(), new Unlock.Book(), 6);
        SkillDef tank = skill("b", RpgClass.VANGUARD, Optional.of("tank"), new Unlock.Book(), 6);
        assertTrue(SkillRules.canLearn(plain, balanced));
        assertFalse(SkillRules.canLearn(tank, balanced));
        assertFalse(SkillRules.canLearn(plain, player(RpgClass.REAR_DEALER, "x", 0)));
        assertFalse(SkillRules.canLearn(plain, RpgPlayerData.EMPTY));
        assertTrue(SkillRules.canLearn(tank, player(RpgClass.VANGUARD, "tank", 0)));
    }

    @Test
    void newlyUnlocked() {
        SkillDef s = skill("s10", RpgClass.VANGUARD, Optional.empty(), new Unlock.Proficiency(Field.SWORD, 10), 6);
        List<SkillDef> all = List.of(s);
        RpgPlayerData l9 = player(RpgClass.VANGUARD, "balanced", 9);
        RpgPlayerData l10 = player(RpgClass.VANGUARD, "balanced", 10);
        RpgPlayerData l11 = player(RpgClass.VANGUARD, "balanced", 11);
        assertEquals(List.of(s), SkillRules.newlyUnlockedByProficiency(all, l9, l10, Set.of()));
        assertTrue(SkillRules.newlyUnlockedByProficiency(all, l10, l11, Set.of()).isEmpty());
        assertTrue(SkillRules.newlyUnlockedByProficiency(all, l9, l10, Set.of("s10")).isEmpty());
    }

    @Test
    void cooldown() {
        SkillDef s = skill("a", RpgClass.VANGUARD, Optional.empty(), new Unlock.Book(), 6);
        assertEquals(3.0, SkillRules.cooldownRemaining(s, 100, 160), 1e-9);
        assertEquals(0.0, SkillRules.cooldownRemaining(s, 100, 1000), 1e-9);
    }

    @Test
    void codecRoundTripsAllUnlocks() {
        for (Unlock u : List.of(new Unlock.Proficiency(Field.SWORD, 10), new Unlock.Book(), new Unlock.Boss("warden"))) {
            SkillDef s = skill("x", RpgClass.VANGUARD, Optional.of("tank"), u, 6);
            JsonElement json = SkillDef.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
            assertEquals(s, SkillDef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        JsonElement j = JsonOps.INSTANCE.createString("x");
        assertTrue(SkillDef.CODEC.parse(JsonOps.INSTANCE, j).result().isEmpty());
    }

    @Test
    void unknownUnlockTypeIsError() {
        var json = com.google.gson.JsonParser.parseString("{\"type\":\"bogus\",\"boss\":\"x\"}");
        assertTrue(Unlock.CODEC.parse(JsonOps.INSTANCE, json).isError());
        var ok = com.google.gson.JsonParser.parseString("{\"type\":\"boss\",\"boss\":\"x\"}");
        assertEquals(new Unlock.Boss("x"), Unlock.CODEC.parse(JsonOps.INSTANCE, ok).getOrThrow());
    }
}
