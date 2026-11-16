package dev.goddessrpg.inquiry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goddessrpg.inquiry.client.InquiryLayout;
import dev.goddessrpg.inquiry.client.InquiryLayout.Rect;
import java.util.List;
import org.junit.jupiter.api.Test;

class InquiryLayoutTest {
    private static final int[][] SIZES = {{320, 240}, {640, 360}, {854, 480}, {1920, 1080}};

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    @Test
    void buttonsAreInsideScreenAndDisjoint() {
        for (int[] s : SIZES) {
            for (int n = 1; n <= 4; n++) {
                List<Rect> rects = InquiryLayout.layout(s[0], s[1], n);
                assertEquals(n, rects.size(), s[0] + "x" + s[1] + " n=" + n);
                for (Rect r : rects) {
                    assertTrue(r.w() > 0 && r.h() > 0, "positive size " + r);
                    assertTrue(r.x() >= 0 && r.y() >= 0 && r.x() + r.w() <= s[0] && r.y() + r.h() <= s[1],
                            "inside screen " + r + " of " + s[0] + "x" + s[1]);
                }
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        assertTrue(!overlaps(rects.get(i), rects.get(j)), "overlap " + rects.get(i) + rects.get(j));
                    }
                }
            }
        }
    }

    @Test
    void buttonsLeaveRoomForQuestionText() {
        for (int[] s : SIZES) {
            List<Rect> rects = InquiryLayout.layout(s[0], s[1], 4);
            assertTrue(rects.get(0).y() >= s[1] * 2 / 5, "text area kept free on " + s[0] + "x" + s[1]);
        }
    }

    @Test
    void buttonsAreOrderedTopToBottom() {
        List<Rect> rects = InquiryLayout.layout(320, 240, 3);
        assertTrue(rects.get(0).y() < rects.get(1).y() && rects.get(1).y() < rects.get(2).y());
    }
}
