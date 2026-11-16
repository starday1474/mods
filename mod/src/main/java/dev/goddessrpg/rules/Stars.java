package dev.goddessrpg.rules;

/** Star rating helpers (1..5). */
public final class Stars {
    public static final int MIN = 1;
    public static final int MAX = 5;
    public static final int BASE = 3;

    private Stars() {}

    public static int clamp(int stars) {
        return Math.max(MIN, Math.min(MAX, stars));
    }
}
