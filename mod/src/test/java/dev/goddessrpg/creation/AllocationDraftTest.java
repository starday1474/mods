package dev.goddessrpg.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goddessrpg.creation.client.AllocationDraft;
import dev.goddessrpg.rules.AllocationRules;
import dev.goddessrpg.rules.AllocationRules.AllocationTuning;
import dev.goddessrpg.rules.CreationChoice.Allocation;
import dev.goddessrpg.rules.GrowthTuning;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AllocationDraftTest {
    private static final AllocationTuning T = AllocationTuning.DEFAULT;

    @Test
    void startsAtMinimumWithTheRestToSpend() {
        AllocationDraft d = new AllocationDraft(T);
        for (int i = 0; i < 4; i++) {
            assertEquals(1, d.stars(i));
            assertFalse(d.canDec(i));
            assertTrue(d.canInc(i));
        }
        assertEquals(8, d.remaining());
        assertFalse(d.complete());
    }

    @Test
    void incAndDecRespectRangeAndBudget() {
        AllocationDraft d = new AllocationDraft(T);
        for (int k = 0; k < 10; k++) {
            d.inc(0);
        }
        assertEquals(5, d.stars(0));
        assertFalse(d.canInc(0));
        assertEquals(4, d.remaining());
        d.inc(1);
        d.inc(1);
        d.inc(2);
        d.inc(3);
        assertEquals(0, d.remaining());
        assertTrue(d.complete());
        assertFalse(d.canInc(3), "no stars left");
        d.inc(3);
        assertEquals(2, d.stars(3));
        d.dec(0);
        assertEquals(4, d.stars(0));
        assertEquals(1, d.remaining());
        assertFalse(d.complete());
        for (int k = 0; k < 10; k++) {
            d.dec(2);
        }
        assertEquals(1, d.stars(2));
    }

    @Test
    void completeDraftIsAValidAllocation() {
        AllocationDraft d = new AllocationDraft(T);
        d.inc(0); d.inc(0); d.inc(0); // 4
        d.inc(1);                     // 2
        d.inc(2); d.inc(2);           // 3
        d.inc(3); d.inc(3);           // 3
        assertTrue(d.complete());
        Allocation a = d.toAllocation();
        assertEquals(new Allocation(4, 2, 3, 3), a);
        assertTrue(AllocationRules.validate(T, a).isEmpty());
    }

    @Test
    void oddTuningIsHonoured() {
        AllocationDraft d = new AllocationDraft(new AllocationTuning(6, 0, 3));
        assertEquals(0, d.stars(0));
        assertEquals(6, d.remaining());
        assertEquals(3, d.max());
    }

    @Test
    void previewReadsTheGrowthTables() {
        GrowthTuning g = GrowthTuning.DEFAULT;
        assertEquals(0, AllocationDraft.startLevel(g.talentStartLevel(), 1));
        assertEquals(8, AllocationDraft.startLevel(g.talentStartLevel(), 5));
        assertEquals(0, AllocationDraft.startLevel(Map.of(), 3), "missing star falls back to level 0");
        assertEquals(1.25, AllocationDraft.multiplier(g.aptitudeMultiplier(), 4));
        assertEquals(1.0, AllocationDraft.multiplier(Map.of(), 3), "missing star falls back to x1");
        assertEquals("0.6", AllocationDraft.formatMultiplier(0.6));
        assertEquals("1.0", AllocationDraft.formatMultiplier(1.0));
        assertEquals("1.25", AllocationDraft.formatMultiplier(1.25));
        assertEquals("1.5", AllocationDraft.formatMultiplier(1.5));
    }
}
