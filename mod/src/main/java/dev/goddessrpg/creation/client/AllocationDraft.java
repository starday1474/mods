package dev.goddessrpg.creation.client;

import dev.goddessrpg.rules.AllocationRules.AllocationTuning;
import dev.goddessrpg.rules.CreationChoice.Allocation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * The star allocation being edited on the creation screen (pure, unit-testable). Slots are in {@link Allocation} order
 * and start at the tuning minimum; a slot can rise while it is below the maximum and stars are left. It is complete
 * (and may be confirmed) when every star is spent. The server re-validates whatever is sent.
 */
public final class AllocationDraft {
    public static final int SLOTS = 4;

    private final AllocationTuning tuning;
    private final int[] stars = new int[SLOTS];

    public AllocationDraft(AllocationTuning tuning) {
        this.tuning = tuning;
        java.util.Arrays.fill(stars, tuning.min());
    }

    public AllocationTuning tuning() {
        return tuning;
    }

    public int max() {
        return tuning.max();
    }

    public int stars(int slot) {
        return stars[slot];
    }

    public int remaining() {
        int sum = 0;
        for (int s : stars) {
            sum += s;
        }
        return tuning.budget() - sum;
    }

    public boolean canInc(int slot) {
        return stars[slot] < tuning.max() && remaining() > 0;
    }

    public boolean canDec(int slot) {
        return stars[slot] > tuning.min();
    }

    public void inc(int slot) {
        if (canInc(slot)) {
            stars[slot]++;
        }
    }

    public void dec(int slot) {
        if (canDec(slot)) {
            stars[slot]--;
        }
    }

    public boolean complete() {
        return remaining() == 0;
    }

    public Allocation toAllocation() {
        return new Allocation(stars[0], stars[1], stars[2], stars[3]);
    }

    /** Whether the slot is a talent slot (start level) rather than an aptitude slot (growth speed). */
    public static boolean isTalent(int slot) {
        return slot % 2 == 0;
    }

    /** Start level for a talent star count, from the server's growth table (0 when the table has no entry). */
    public static int startLevel(Map<Integer, Integer> table, int stars) {
        return table.getOrDefault(stars, 0);
    }

    /** Proficiency XP multiplier for an aptitude star count (1.0 when the table has no entry). */
    public static double multiplier(Map<Integer, Double> table, int stars) {
        return table.getOrDefault(stars, 1.0);
    }

    /** At least one decimal, at most two: 0.6, 1.0, 1.25. */
    public static String formatMultiplier(double m) {
        BigDecimal d = BigDecimal.valueOf(m).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (d.scale() < 1) {
            d = d.setScale(1, RoundingMode.UNNECESSARY);
        }
        return d.toPlainString();
    }
}
