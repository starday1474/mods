package dev.goddessrpg.compat.epicfight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MovesetLoaderTest {
    @Test
    void balancedJsonIsTheEfxCombo() {
        Optional<MovesetLoader.Config> c = MovesetLoader.read("balanced");
        assertTrue(c.isPresent(), "balanced.json missing or unreadable");
        assertEquals("goddessrpg:balanced_sword", c.get().weaponType().toString());
        assertEquals("epicfight:sword", c.get().base().toString());
        assertEquals(List.of("epicfightx:biped/combat/sword_auto1", "epicfightx:biped/combat/sword_auto2",
                "epicfightx:biped/combat/sword_auto3"), c.get().combo());
        assertEquals("epicfightx:biped/combat/sword_dash", c.get().dash());
        assertEquals("epicfightx:biped/combat/sword_airslash", c.get().air());
        assertEquals(List.of("epicfightx:biped/combat/sword_auto1", "epicfightx:biped/combat/sword_auto2",
                "epicfightx:biped/combat/sword_auto3", "epicfightx:biped/combat/sword_dash",
                "epicfightx:biped/combat/sword_airslash"), c.get().attackIds());
    }

    @Test
    void parseRejectsMissingFields() {
        assertThrows(RuntimeException.class, () -> MovesetLoader.parse("x", JsonParser.parseString(
                "{\"weapon_type\":\"goddessrpg:x\",\"base\":\"epicfight:sword\",\"combo\":[],\"dash\":\"a:b\",\"air\":\"a:c\"}")));
        assertThrows(RuntimeException.class, () -> MovesetLoader.parse("x", JsonParser.parseString(
                "{\"base\":\"epicfight:sword\",\"combo\":[\"epicfight:a\"],\"dash\":\"a:b\",\"air\":\"a:c\"}")));
        assertThrows(RuntimeException.class, () -> MovesetLoader.parse("x", JsonParser.parseString("[]")));
    }

    @Test
    void allowedNamespaces() {
        assertTrue(MovesetLoader.allowedId("epicfight:biped/combat/sword_auto1"));
        assertTrue(MovesetLoader.allowedId("epicfightx:biped/combat/sword_auto1"));
        assertTrue(MovesetLoader.allowedId("wom:biped/combat/torment_airslam"));
        assertTrue(MovesetLoader.allowedId("goddessrpg:biped/combat/balanced_auto1"));
        assertTrue(!MovesetLoader.allowedId("minecraft:biped/combat/sword_auto1"));
        assertTrue(!MovesetLoader.allowedId("sword_auto1"));
        assertTrue(!MovesetLoader.allowedId("epicfight:Bad Path"));
    }

    @Test
    void unknownReturnsEmpty() {
        assertTrue(MovesetLoader.read("no_such_moveset").isEmpty());
    }
}
