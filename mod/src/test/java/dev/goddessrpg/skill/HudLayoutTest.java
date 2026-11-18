package dev.goddessrpg.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goddessrpg.skill.client.HudLayout;
import dev.goddessrpg.skill.client.HudLayout.Rect;
import java.util.List;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
    private static final int[][] SIZES = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {1920, 1080}};

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    private static boolean inside(Rect r, int w, int h) {
        return r.x() >= 0 && r.y() >= 0 && r.x() + r.w() <= w && r.y() + r.h() <= h;
    }

    @Test
    void slotsInsideScreenAndClearOfHotbar() {
        for (int[] s : SIZES) {
            int w = s[0];
            int h = s[1];
            List<Rect> slots = HudLayout.slots(w, h);
            assertEquals(4, slots.size());
            Rect hotbar = new Rect(w / 2 - 91, h - 22, 182, 22);
            for (Rect r : slots) {
                assertTrue(r.w() >= 12 && r.h() >= 12, "size " + r);
                assertTrue(inside(r, w, h), r + " inside " + w + "x" + h);
                assertFalse(overlaps(r, hotbar), r + " overlaps hotbar at " + w + "x" + h);
                assertTrue(r.x() >= hotbar.x() + hotbar.w(), r + " right of hotbar at " + w + "x" + h);
            }
            for (int i = 0; i < 4; i++) {
                for (int j = i + 1; j < 4; j++) {
                    assertFalse(overlaps(slots.get(i), slots.get(j)), "slots " + i + "," + j + " at " + w + "x" + h);
                }
            }
            Rect mana = HudLayout.manaBar(w, h);
            assertTrue(inside(mana, w, h) && mana.w() > 0 && mana.h() > 0, "mana " + mana);
            assertFalse(overlaps(mana, hotbar), "mana overlaps hotbar");
            for (Rect r : slots) {
                assertFalse(overlaps(mana, r), "mana overlaps slot " + r);
                assertTrue(mana.y() + mana.h() <= r.y(), "mana above slots");
            }
        }
    }
}
