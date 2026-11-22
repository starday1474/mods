package dev.goddessrpg.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class LangAndAssetsTest {
    /** Every item the mod registers (RpgItems). */
    private static final List<String> ITEMS = List.of("goddess_tome", "goddess_reroll", "reawakening_crystal",
            "skill_book", "ancient_tome", "training_sword");
    /** Subtypes shown as creation cards (spellbow is hidden). */
    private static final List<String> CARDS = List.of("balanced", "tank", "infight", "archer", "elementalist",
            "summoner", "healer", "relicist");

    private static JsonObject read(String path) throws Exception {
        InputStream in = LangAndAssetsTest.class.getResourceAsStream(path);
        assertNotNull(in, "missing " + path);
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static JsonObject ko() throws Exception {
        return read("/assets/goddessrpg/lang/ko_kr.json");
    }

    private static JsonObject en() throws Exception {
        return read("/assets/goddessrpg/lang/en_us.json");
    }

    /** Korean is the base language; English has every key except the test-only skill names. */
    @Test
    void bothLanguagesHaveTheSameKeys() throws Exception {
        java.util.Set<String> ko = new java.util.TreeSet<>(ko().keySet());
        ko.removeIf(k -> k.startsWith("goddessrpg.skill.test_"));
        assertEquals(ko, new java.util.TreeSet<>(en().keySet()));
    }

    @Test
    void noGoddessWordingLeft() throws Exception {
        for (JsonObject lang : List.of(ko(), en())) {
            for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
                String v = e.getValue().getAsString().replace("/goddessrpg", ""); // the command name stays
                assertFalse(v.contains("여신") || v.toLowerCase(Locale.ROOT).contains("goddess"),
                        e.getKey() + " still says goddess: " + v);
            }
        }
    }

    @Test
    void itemNames() throws Exception {
        JsonObject ko = ko();
        JsonObject en = en();
        assertEquals("모험가의 서", ko.get("item.goddessrpg.goddess_tome").getAsString());
        assertEquals("Adventurer's Tome", en.get("item.goddessrpg.goddess_tome").getAsString());
        assertEquals("관리자용 재선택의 깃", ko.get("item.goddessrpg.goddess_reroll").getAsString());
        assertEquals("Admin Re-creation Quill", en.get("item.goddessrpg.goddess_reroll").getAsString());
        assertEquals("재각성의 결정", ko.get("item.goddessrpg.reawakening_crystal").getAsString());
        assertEquals("Reawakening Crystal", en.get("item.goddessrpg.reawakening_crystal").getAsString());
        for (String id : ITEMS) {
            assertTrue(ko.has("item.goddessrpg." + id), "no name for " + id);
        }
    }

    @Test
    void everyCardHasAWeaponNameAndTwoDescriptionLines() throws Exception {
        for (JsonObject lang : List.of(ko(), en())) {
            for (String id : CARDS) {
                for (String key : List.of("goddessrpg.create.weapon." + id, "goddessrpg.create.desc." + id + ".1",
                        "goddessrpg.create.desc." + id + ".2", "goddessrpg.subtype." + id)) {
                    assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), "missing " + key);
                }
            }
        }
    }

    @Test
    void everyItemHasAModelWhoseTexturesExist() throws Exception {
        for (String id : ITEMS) {
            JsonObject model = read("/assets/goddessrpg/models/item/" + id + ".json");
            if (!model.has("textures")) {
                assertTrue(model.has("parent"), id + " has neither textures nor parent");
                continue;
            }
            for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet()) {
                String[] loc = e.getValue().getAsString().split(":", 2);
                String path = "/assets/" + loc[0] + "/textures/" + loc[1] + ".png";
                if (loc[0].equals("goddessrpg")) {
                    assertNotNull(LangAndAssetsTest.class.getResource(path), id + ": missing texture " + path);
                }
            }
        }
    }

    @Test
    void crystalIconIs16x16WithTransparency() throws Exception {
        try (InputStream in = LangAndAssetsTest.class.getResourceAsStream(
                "/assets/goddessrpg/textures/item/reawakening_crystal.png")) {
            assertNotNull(in, "no crystal texture");
            BufferedImage img = ImageIO.read(in);
            assertEquals(16, img.getWidth());
            assertEquals(16, img.getHeight());
            assertTrue(img.getColorModel().hasAlpha(), "icon needs a transparent background");
            assertEquals(0, img.getRGB(0, 0) >>> 24, "corner should be transparent");
        }
    }
}
