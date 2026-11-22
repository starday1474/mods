package dev.goddessrpg.creation.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure placement for the character creation screen (no Minecraft client classes, so it is unit-testable). Step 1 is a
 * grid of weapon cards under two title lines; step 2 is a 2x2 grid of allocation slots under a three-line header, with
 * the "back" and "confirm" buttons along the bottom and an error line just above them. All values are in GUI pixels.
 */
public final class CreationLayout {
    private CreationLayout() {}

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        public boolean containsRect(Rect o) {
            return o.x >= x && o.y >= y && o.x + o.w <= x + w && o.y + o.h <= y + h;
        }

        public boolean overlaps(Rect o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }
    }

    public static final int MARGIN = 8;
    public static final int GAP = 6;
    /** Step 1: title at y 6, subtitle (or error) at y 17; cards start below. */
    public static final int CARD_TOP = 28;
    public static final int CARD_MAX_W = 150;
    public static final int CARD_MAX_H = 96;
    public static final int CARD_COLUMNS = 4;
    /** Step 2: title, chosen weapon line, stars-left line; slots start below. */
    public static final int ALLOC_TOP = 44;
    public static final int SLOT_MAX_W = 180;
    public static final int SLOT_MAX_H = 72;
    public static final int BUTTON_W = 80;
    public static final int BUTTON_H = 20;
    public static final int SMALL_BUTTON = 14;
    public static final int STAR_W = 10;
    private static final int LINE = 9;

    /** {@code n} card rectangles, left to right then top to bottom; a short last row is centred. */
    public static List<Rect> cards(int w, int h, int n) {
        if (n <= 0) {
            return List.of();
        }
        int cols = Math.min(n, CARD_COLUMNS);
        int rows = (n + cols - 1) / cols;
        int availW = w - 2 * MARGIN;
        int availH = h - CARD_TOP - MARGIN;
        int cw = Math.max(1, Math.min(CARD_MAX_W, (availW - (cols - 1) * GAP) / cols));
        int ch = Math.max(1, Math.min(CARD_MAX_H, (availH - (rows - 1) * GAP) / rows));
        int gridH = rows * ch + (rows - 1) * GAP;
        int y0 = CARD_TOP + Math.max(0, (availH - gridH) / 2);
        List<Rect> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            int inRow = Math.min(cols, n - row * cols);
            int rowW = inRow * cw + (inRow - 1) * GAP;
            int x0 = (w - rowW) / 2;
            out.add(new Rect(x0 + col * (cw + GAP), y0 + row * (ch + GAP), cw, ch));
        }
        return out;
    }

    /** Index of the card containing the point, or -1. */
    public static int hit(List<Rect> rects, double x, double y) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The four allocation slots in {@code Allocation} order: first field talent, first field aptitude, second field
     * talent, second field aptitude. Each field is a column (talent above aptitude).
     */
    public static List<Rect> allocation(int w, int h) {
        int availW = w - 2 * MARGIN;
        int availH = buttonY(h) - LINE - 4 - ALLOC_TOP;
        int sw = Math.max(1, Math.min(SLOT_MAX_W, (availW - GAP) / 2));
        int sh = Math.max(1, Math.min(SLOT_MAX_H, (availH - GAP) / 2));
        int gridW = 2 * sw + GAP;
        int gridH = 2 * sh + GAP;
        int x0 = (w - gridW) / 2;
        int y0 = ALLOC_TOP + Math.max(0, (availH - gridH) / 2);
        List<Rect> out = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            int col = i / 2;
            int row = i % 2;
            out.add(new Rect(x0 + col * (sw + GAP), y0 + row * (sh + GAP), sw, sh));
        }
        return out;
    }

    private static int buttonY(int h) {
        return h - MARGIN - BUTTON_H;
    }

    public static Rect backButton(int w, int h) {
        return new Rect(w / 2 - GAP / 2 - BUTTON_W, buttonY(h), BUTTON_W, BUTTON_H);
    }

    public static Rect confirmButton(int w, int h) {
        return new Rect(w / 2 + GAP / 2, buttonY(h), BUTTON_W, BUTTON_H);
    }

    /** Top of the error line in step 2 (between the slots and the buttons). */
    public static int errorY(int w, int h) {
        return buttonY(h) - LINE - 2;
    }

    // ---- inside one slot: label (y+4), hint (y+15), [-] stars [+] (y+28), preview (y+50) ----

    public static int labelY(Rect slot) {
        return slot.y() + 4;
    }

    public static int hintY(Rect slot) {
        return slot.y() + 15;
    }

    private static int rowY(Rect slot) {
        return slot.y() + 28;
    }

    public static Rect minusButton(Rect slot) {
        return new Rect(slot.x() + 4, rowY(slot), SMALL_BUTTON, SMALL_BUTTON);
    }

    /** Left x of star {@code i} (0-based); stars are drawn at the minus/plus row, vertically centred. */
    public static int starX(Rect slot, int i) {
        return slot.x() + 4 + SMALL_BUTTON + 4 + i * STAR_W;
    }

    public static int starY(Rect slot) {
        return rowY(slot) + (SMALL_BUTTON - LINE) / 2 + 1;
    }

    public static Rect plusButton(Rect slot) {
        return new Rect(starX(slot, 5) + 4, rowY(slot), SMALL_BUTTON, SMALL_BUTTON);
    }

    public static int previewY(Rect slot) {
        return slot.y() + 50;
    }
}
