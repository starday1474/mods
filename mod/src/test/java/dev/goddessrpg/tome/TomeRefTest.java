package dev.goddessrpg.tome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TomeRefTest {
    @Test
    void codecRoundTrips() {
        TomeRef ref = new TomeRef(40, 0.25, Map.of("first_steps", 0.05, "old_war_record", 0.1));
        JsonElement json = TomeRef.CODEC.encodeStart(JsonOps.INSTANCE, ref).getOrThrow();
        assertEquals(ref, TomeRef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void emptyRefRoundTrips() {
        JsonElement json = TomeRef.CODEC.encodeStart(JsonOps.INSTANCE, TomeRef.EMPTY).getOrThrow();
        assertEquals(TomeRef.EMPTY, TomeRef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
