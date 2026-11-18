package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The six balanced vanguard skills: numbers from the task 6 brief, mind-map placement rules. */
class BalancedSkillFilesTest {
    private static SkillDef load(String id) throws Exception {
        InputStream in = BalancedSkillFilesTest.class.getResourceAsStream("/data/goddessrpg/skills/" + id + ".json");
        assertNotNull(in, "missing skill " + id);
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            var j = JsonParser.parseReader(r).getAsJsonObject();
            j.addProperty("id", id);
            return SkillDef.CODEC.parse(JsonOps.INSTANCE, j).getOrThrow();
        }
    }

    private static Map<String, SkillDef> all() throws Exception {
        Map<String, SkillDef> m = new LinkedHashMap<>();
        for (String id : List.of("balanced_shield_bash", "balanced_dash_slash", "balanced_counter", "balanced_steadfast",
                "vanguard_sword_mastery", "vanguard_shield_mastery")) {
            m.put(id, load(id));
        }
        return m;
    }

    private static void effect(SkillDef s, String type, Map<String, Double> params) {
        assertEquals(type, s.effect().type(), s.id());
        assertEquals(params, s.effect().params(), s.id());
    }

    @Test
    void numbersMatchTheBrief() throws Exception {
        Map<String, SkillDef> m = all();
        for (SkillDef s : m.values()) {
            assertEquals(RpgClass.VANGUARD, s.rpgClass(), s.id());
            assertEquals(0.0, s.manaCost(), s.id());
        }
        SkillDef bash = m.get("balanced_shield_bash");
        assertEquals(new SkillDef.Unlock.Proficiency(Field.DEFENSE, 5), bash.unlock());
        assertEquals(SkillDef.Kind.ACTIVE, bash.kind());
        assertEquals(Optional.of("goddessrpg:biped/combat/balanced_shield_bash"), bash.animation());
        effect(bash, "knockback_stun", Map.of("knockback", 1.2, "stun_sec", 1.0, "damage_mult", 0.6));
        assertEquals(6.0, bash.cooldownSec());
        assertEquals(3.0, bash.staminaCost());
        assertEquals(Optional.empty(), bash.subtype());

        SkillDef dash = m.get("balanced_dash_slash");
        assertEquals(new SkillDef.Unlock.Proficiency(Field.SWORD, 10), dash.unlock());
        assertEquals(Optional.of("epicfightx:biped/combat/sword_dash"), dash.animation());
        effect(dash, "animation_strike", Map.of("damage_mult", 1.3));
        assertEquals(8.0, dash.cooldownSec());
        assertEquals(4.0, dash.staminaCost());
        assertEquals(Optional.empty(), dash.subtype());

        SkillDef counter = m.get("balanced_counter");
        assertEquals(new SkillDef.Unlock.Proficiency(Field.SWORD, 15), counter.unlock());
        assertEquals(Optional.of("balanced"), counter.subtype());
        assertEquals(Optional.of("goddessrpg:biped/combat/balanced_counter"), counter.animation());
        effect(counter, "counter_window", Map.of("window_sec", 0.6, "damage_mult", 1.6));
        assertEquals(10.0, counter.cooldownSec());
        assertEquals(3.0, counter.staminaCost());

        SkillDef steadfast = m.get("balanced_steadfast");
        assertEquals(new SkillDef.Unlock.Book(), steadfast.unlock());
        assertEquals(Optional.of("balanced"), steadfast.subtype());
        assertEquals(Optional.empty(), steadfast.animation());
        effect(steadfast, "buff", Map.of("duration_sec", 4.0, "damage_taken_mult", 0.7, "move_speed_mult", 0.85));
        assertEquals(20.0, steadfast.cooldownSec());
        assertEquals(2.0, steadfast.staminaCost());

        SkillDef sword = m.get("vanguard_sword_mastery");
        assertEquals(SkillDef.Kind.PASSIVE, sword.kind());
        assertEquals(new SkillDef.Unlock.Proficiency(Field.SWORD, 20), sword.unlock());
        effect(sword, "passive_attribute", Map.of("attack_damage_pct", 10.0));
        assertEquals(Optional.of("sword"), sword.effect().text("requires"));
        assertEquals(Optional.empty(), sword.subtype());

        SkillDef shield = m.get("vanguard_shield_mastery");
        assertEquals(SkillDef.Kind.PASSIVE, shield.kind());
        assertEquals(new SkillDef.Unlock.Proficiency(Field.DEFENSE, 20), shield.unlock());
        effect(shield, "passive_attribute", Map.of("armor_flat", 2.0));
        assertEquals(Optional.of("shield_offhand"), shield.effect().text("requires"));
        assertEquals(Optional.empty(), shield.subtype());
    }

    @Test
    void mindMapPlacement() throws Exception {
        Map<String, SkillDef> m = all();
        for (SkillDef s : m.values()) {
            SkillDef.MapPos p = s.map();
            if (s.subtype().isPresent()) {
                assertTrue(p.y() > 0, s.id() + " subtype-only branch goes below: " + p);
            } else if (s.field() == Field.SWORD) {
                assertTrue(p.x() > 0, s.id() + " sword branch goes right: " + p);
            } else if (s.field() == Field.DEFENSE) {
                assertTrue(p.x() < 0, s.id() + " defense branch goes left: " + p);
            }
            assertTrue(Math.hypot(p.x(), p.y()) >= 40, s.id() + " too close to the center: " + p);
            for (SkillDef o : m.values()) {
                if (o != s) {
                    double d = Math.hypot(p.x() - o.map().x(), p.y() - o.map().y());
                    assertTrue(d >= 40, s.id() + " and " + o.id() + " only " + d + " apart");
                }
            }
        }
    }
}
