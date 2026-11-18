package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ManaDataFileTest {
    @Test
    void manaJsonEqualsDefault() throws Exception {
        InputStream in = ManaDataFileTest.class.getResourceAsStream("/data/goddessrpg/tuning/mana.json");
        assertNotNull(in, "missing mana.json");
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            var parsed = ManaRules.ManaTuning.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(r)).getOrThrow();
            assertEquals(ManaRules.ManaTuning.DEFAULT, parsed);
        }
    }
}
