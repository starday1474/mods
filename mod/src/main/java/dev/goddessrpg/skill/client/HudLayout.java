package dev.goddessrpg.skill.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout of the skill HUD (GUI-scaled coordinates). The vanilla hotbar is 182px wide, centered, 22px tall at the
 * bottom. The four slots sit to its right in one row when there is room, otherwise in a 2x2 block; the mana bar sits
 * just above the slots.
 */
public final class HudLayout {
    private HudLayout() {}

    public record Rect(int x, int y, int w, int h) {}

    private static final int HOTBAR_HALF = 91;
    /** Leaves room for the off-hand slot that vanilla draws right of the hotbar for left-handed players. */
    private static final int GAP_FROM_HOTBAR = 32;
    private static final int MIN_GAP = 4;
    private static final int ROW_SIZE = 20;
    private static final int GRID_SIZE = 14;
    private static final int SPACING = 2;
    private static final int BOTTOM = 1;
    public static final int MANA_HEIGHT = 3;

    private static int left(int w, int need) {
        int hotbarRight = w / 2 + HOTBAR_HALF;
        int room = w - hotbarRight;
        int gap = room - need >= GAP_FROM_HOTBAR + MIN_GAP ? GAP_FROM_HOTBAR : MIN_GAP;
        return hotbarRight + gap;
    }

    private static boolean row(int w) {
        int need = 4 * ROW_SIZE + 3 * SPACING;
        return w - (w / 2 + HOTBAR_HALF) >= need + GAP_FROM_HOTBAR + MIN_GAP;
    }

    public static List<Rect> slots(int screenW, int screenH) {
        List<Rect> out = new ArrayList<>(4);
        if (row(screenW)) {
            int need = 4 * ROW_SIZE + 3 * SPACING;
            int x0 = left(screenW, need);
            int y = screenH - BOTTOM - ROW_SIZE;
            for (int i = 0; i < 4; i++) {
                out.add(new Rect(x0 + i * (ROW_SIZE + SPACING), y, ROW_SIZE, ROW_SIZE));
            }
        } else {
            int need = 2 * GRID_SIZE + SPACING;
            int x0 = left(screenW, need);
            int y0 = screenH - BOTTOM - 2 * GRID_SIZE - SPACING;
            for (int i = 0; i < 4; i++) {
                out.add(new Rect(x0 + (i % 2) * (GRID_SIZE + SPACING), y0 + (i / 2) * (GRID_SIZE + SPACING),
                        GRID_SIZE, GRID_SIZE));
            }
        }
        return out;
    }

    /** Mana bar: as wide as the slot block, just above it. */
    public static Rect manaBar(int screenW, int screenH) {
        List<Rect> s = slots(screenW, screenH);
        int x = s.get(0).x();
        int right = 0;
        int top = Integer.MAX_VALUE;
        for (Rect r : s) {
            right = Math.max(right, r.x() + r.w());
            top = Math.min(top, r.y());
        }
        return new Rect(x, top - SPACING - MANA_HEIGHT, right - x, MANA_HEIGHT);
    }
}
