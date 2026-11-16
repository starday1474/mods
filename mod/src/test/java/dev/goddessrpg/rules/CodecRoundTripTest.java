package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.rules.InquiryData.Answer;
import dev.goddessrpg.rules.InquiryData.Subtype;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodecRoundTripTest {
    @Test
    void growthTuningRoundTrips() {
        JsonElement json = GrowthTuning.CODEC.encodeStart(JsonOps.INSTANCE, GrowthTuning.DEFAULT).getOrThrow();
        assertEquals(GrowthTuning.DEFAULT, GrowthTuning.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void inquiryRecordsRoundTrip() {
        Answer a = new Answer("k", Map.of("vanguard", 2), Map.of(Field.SWORD, 1), Map.of(Field.RELIC, -1));
        JsonElement json = Answer.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
        assertEquals(a, Answer.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals("rear_dealer", Subtype.CODEC.encodeStart(JsonOps.INSTANCE,
                new Subtype("x", RpgClass.REAR_DEALER, true)).getOrThrow().getAsJsonObject().get("class").getAsString());
    }
}
