package dev.goddessrpg.inquiry.client;

import java.util.ArrayList;
import java.util.List;

/** Pure button placement for the inquiry screen (no Minecraft classes, so it is unit-testable). */
public final class InquiryLayout {
    private InquiryLayout() {}

    public record Rect(int x, int y, int w, int h) {}

    private static final int GAP = 4;
    private static final int MARGIN = 10;
    private static final int MAX_WIDTH = 360;
    private static final int MAX_HEIGHT = 36;

    /** Top of the button area; everything above is reserved for the goddess's words. */
    public static int buttonsTop(int height) {
        return height * 9 / 20;
    }

    /** Answer buttons stacked top to bottom in the lower part of the screen, centred horizontally. */
    public static List<Rect> layout(int width, int height, int answerCount) {
        List<Rect> out = new ArrayList<>(answerCount);
        if (answerCount <= 0) {
            return out;
        }
        int w = Math.max(1, Math.min(MAX_WIDTH, width - 2 * MARGIN));
        int x = (width - w) / 2;
        int top = buttonsTop(height);
        int avail = height - MARGIN - top;
        int h = Math.max(1, Math.min(MAX_HEIGHT, (avail - GAP * (answerCount - 1)) / answerCount));
        for (int i = 0; i < answerCount; i++) {
            out.add(new Rect(x, top + i * (h + GAP), w, h));
        }
        return out;
    }
}
