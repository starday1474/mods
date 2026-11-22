package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.rules.AllocationRules.AllocationTuning;
import dev.goddessrpg.rules.InquiryData.Subtype;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SubtypeFilesTest {
    private static final List<String> IDS = List.of("archer", "balanced", "elementalist", "healer", "infight",
            "relicist", "spellbow", "summoner", "tank");

    private static JsonObject read(String path) throws Exception {
        InputStream in = SubtypeFilesTest.class.getResourceAsStream(path);
        assertNotNull(in, "missing " + path);
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static Subtype load(String id) throws Exception {
        JsonObject j = read("/data/goddessrpg/subtypes/" + id + ".json");
        j.addProperty("id", id);
        return Subtype.CODEC.parse(JsonOps.INSTANCE, j).getOrThrow();
    }

    @Test
    void everyFileHasWeaponIconOrder() throws Exception {
        for (String id : IDS) {
            JsonObject j = read("/data/goddessrpg/subtypes/" + id + ".json");
            assertTrue(j.has("weapon") && j.has("icon") && j.has("order"), id);
        }
    }

    @Test
    void onlySpellbowIsHiddenAndWeaponsDistinct() throws Exception {
        Set<String> weapons = new HashSet<>();
        for (String id : IDS) {
            Subtype s = load(id);
            assertEquals(id.equals("spellbow"), s.hidden(), id);
            if (!s.hidden()) {
                assertTrue(weapons.add(s.weapon()), "duplicate weapon " + s.weapon());
            }
        }
        assertEquals(8, weapons.size());
    }

    @Test
    void allocationJsonEqualsDefault() throws Exception {
        var parsed = AllocationTuning.CODEC.parse(JsonOps.INSTANCE,
                read("/data/goddessrpg/tuning/allocation.json")).getOrThrow();
        assertEquals(AllocationTuning.DEFAULT, parsed);
    }
}
