package dev.goddessrpg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmokeTest {
    @Test
    void modIdIsStable() {
        assertEquals("goddessrpg", GoddessRpg.MODID);
    }
}
