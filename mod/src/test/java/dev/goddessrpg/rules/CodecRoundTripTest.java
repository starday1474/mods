package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.rules.InquiryData.Subtype;
import org.junit.jupiter.api.Test;

class CodecRoundTripTest {
    @Test
    void growthTuningRoundTrips() {
        JsonElement json = GrowthTuning.CODEC.encodeStart(JsonOps.INSTANCE, GrowthTuning.DEFAULT).getOrThrow();
        assertEquals(GrowthTuning.DEFAULT, GrowthTuning.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void subtypeEncodesClassId() {
        assertEquals("rear_dealer", Subtype.CODEC.encodeStart(JsonOps.INSTANCE,
                new Subtype("x", RpgClass.REAR_DEALER, true)).getOrThrow().getAsJsonObject().get("class").getAsString());
    }

    @Test
    void effectKeepsStringParams() {
        var json = com.google.gson.JsonParser.parseString(
                "{\"type\":\"passive_attribute\",\"attack_damage_pct\":10,\"requires\":\"sword\"}");
        SkillDef.Effect e = SkillDef.Effect.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(10.0, e.param("attack_damage_pct", 0));
        assertEquals("sword", e.text("requires").orElseThrow());
        assertEquals(e, SkillDef.Effect.CODEC.parse(JsonOps.INSTANCE,
                SkillDef.Effect.CODEC.encodeStart(JsonOps.INSTANCE, e).getOrThrow()).getOrThrow());
    }
}
