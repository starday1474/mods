package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.player.RpgPlayerData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManaRulesTest {
    @Test
    void maxUsesSecondField() {
        RpgPlayerData d = RpgPlayerData.EMPTY
                .withResult(RpgClass.VANGUARD, "balanced", new StarResult(Map.of(), Map.of()), List.of(), GrowthTuning.DEFAULT)
                .withXp(Field.DEFENSE, new LevelProgress(5, 0));
        assertEquals(40.0, ManaRules.max(ManaRules.ManaTuning.DEFAULT, d), 1e-9);
        assertEquals(0.0, ManaRules.max(ManaRules.ManaTuning.DEFAULT, RpgPlayerData.EMPTY), 1e-9);
    }

    @Test
    void codecRoundTrips() {
        JsonElement json = ManaRules.ManaTuning.CODEC.encodeStart(JsonOps.INSTANCE, ManaRules.ManaTuning.DEFAULT).getOrThrow();
        assertEquals(ManaRules.ManaTuning.DEFAULT, ManaRules.ManaTuning.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
