package dev.goddessrpg.tome.client;

/** Pure page arithmetic for the goddess tome (no Minecraft classes, so it is unit-testable). */
public final class TomePaging {
    private TomePaging() {}

    /** 0 stats, 1 skill mind map, 2 knowledge, 3 goddess's words; each page is one open spread (two sides). */
    public static final int PAGE_COUNT = 4;

    public static int clamp(int page) {
        return Math.max(0, Math.min(PAGE_COUNT - 1, page));
    }

    public static int prev(int page) {
        return clamp(page - 1);
    }

    public static int next(int page) {
        return clamp(page + 1);
    }

    /** Mouse wheel: scrolling down (negative delta) turns forward, up turns back. */
    public static int scroll(int page, double delta) {
        if (delta < 0) {
            return next(page);
        }
        if (delta > 0) {
            return prev(page);
        }
        return page;
    }

    /** First visible line of a list of {@code total} lines shown {@code visible} at a time. */
    public static int clampScroll(int offset, int total, int visible) {
        return Math.max(0, Math.min(offset, Math.max(0, total - visible)));
    }
}
