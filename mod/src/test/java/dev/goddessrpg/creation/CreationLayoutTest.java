package dev.goddessrpg.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goddessrpg.creation.client.CreationLayout;
import dev.goddessrpg.creation.client.CreationLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CreationLayoutTest {
    private static void assertInside(Rect r, int w, int h, String what) {
        assertTrue(r.x() >= 0 && r.y() >= 0 && r.x() + r.w() <= w && r.y() + r.h() <= h,
                what + " " + r + " outside " + w + "x" + h);
        assertTrue(r.w() > 0 && r.h() > 0, what + " empty " + r);
    }

    private static void assertDisjoint(List<Rect> rs, String what) {
        for (int i = 0; i < rs.size(); i++) {
            for (int j = i + 1; j < rs.size(); j++) {
                assertFalse(rs.get(i).overlaps(rs.get(j)), what + " " + i + " and " + j + " overlap: " + rs.get(i) + " " + rs.get(j));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"320, 240", "1920, 1080", "427, 240", "640, 360"})
    void cardsFitAndDoNotOverlap(int w, int h) {
        List<Rect> cards = CreationLayout.cards(w, h, 8);
        assertEquals(8, cards.size());
        cards.forEach(r -> assertInside(r, w, h, "card"));
        assertDisjoint(cards, "cards");
        // room for icon, two name lines and the description
        cards.forEach(r -> assertTrue(r.w() >= 64 && r.h() >= 64, "card too small " + r));
        // below the title lines
        cards.forEach(r -> assertTrue(r.y() >= CreationLayout.CARD_TOP, "card under the title " + r));
    }

    @ParameterizedTest
    @CsvSource({"320, 240", "1920, 1080", "427, 240", "640, 360"})
    void allocationSlotsAndButtonsFitAndDoNotOverlap(int w, int h) {
        List<Rect> slots = CreationLayout.allocation(w, h);
        assertEquals(4, slots.size());
        List<Rect> all = new ArrayList<>(slots);
        all.add(CreationLayout.backButton(w, h));
        all.add(CreationLayout.confirmButton(w, h));
        all.forEach(r -> assertInside(r, w, h, "allocation"));
        assertDisjoint(all, "allocation");
        slots.forEach(r -> assertTrue(r.y() >= CreationLayout.ALLOC_TOP, "slot under the header " + r));
        for (Rect s : slots) {
            Rect minus = CreationLayout.minusButton(s);
            Rect plus = CreationLayout.plusButton(s);
            assertTrue(s.containsRect(minus) && s.containsRect(plus), "buttons outside slot " + s);
            assertFalse(minus.overlaps(plus), "minus and plus overlap");
            assertTrue(CreationLayout.starX(s, 0) >= minus.x() + minus.w()
                    && CreationLayout.starX(s, 4) + CreationLayout.STAR_W <= plus.x(), "stars overlap buttons");
            assertTrue(CreationLayout.previewY(s) + 9 <= s.y() + s.h(), "preview line outside slot " + s);
        }
        // the error line sits between the slots and the buttons
        int errY = CreationLayout.errorY(w, h);
        slots.forEach(s -> assertTrue(errY >= s.y() + s.h(), "error line over a slot"));
        assertTrue(errY + 9 <= CreationLayout.confirmButton(w, h).y(), "error line over the buttons");
    }

    @ParameterizedTest
    @CsvSource({"320, 240", "1920, 1080"})
    void slotOrderIsFieldMajor(int w, int h) {
        List<Rect> s = CreationLayout.allocation(w, h);
        // 0 talent first, 1 aptitude first (same column), 2 talent second, 3 aptitude second
        assertEquals(s.get(0).x(), s.get(1).x());
        assertEquals(s.get(2).x(), s.get(3).x());
        assertTrue(s.get(0).x() < s.get(2).x());
        assertTrue(s.get(0).y() < s.get(1).y());
    }

    @ParameterizedTest
    @CsvSource({"1, 320, 240", "3, 320, 240", "5, 1920, 1080", "9, 640, 360"})
    void otherCardCountsStillFit(int n, int w, int h) {
        List<Rect> cards = CreationLayout.cards(w, h, n);
        assertEquals(n, cards.size());
        cards.forEach(r -> assertInside(r, w, h, "card"));
        assertDisjoint(cards, "cards");
    }

    @ParameterizedTest
    @CsvSource({"320, 240", "1920, 1080"})
    void hitFindsTheCardUnderThePoint(int w, int h) {
        List<Rect> cards = CreationLayout.cards(w, h, 8);
        for (int i = 0; i < cards.size(); i++) {
            Rect r = cards.get(i);
            assertEquals(i, CreationLayout.hit(cards, r.x() + r.w() / 2, r.y() + r.h() / 2));
        }
        assertEquals(-1, CreationLayout.hit(cards, 0, 0));
    }
}
