package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goddessrpg.rules.AllocationRules.AllocationTuning;
import dev.goddessrpg.rules.CreationChoice.Allocation;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AllocationRulesTest {
    private static final AllocationTuning T = AllocationTuning.DEFAULT;

    @Test
    void validAllocations() {
        assertTrue(AllocationRules.validate(T, new Allocation(3, 3, 3, 3)).isEmpty());
        assertTrue(AllocationRules.validate(T, new Allocation(5, 5, 1, 1)).isEmpty());
    }

    @Test
    void wrongSum() {
        assertEquals(Optional.of("goddessrpg.create.error.sum"),
                AllocationRules.validate(T, new Allocation(5, 5, 5, 5)));
    }

    @Test
    void outOfRange() {
        assertEquals(Optional.of("goddessrpg.create.error.range"),
                AllocationRules.validate(T, new Allocation(6, 2, 2, 2)));
        assertEquals(Optional.of("goddessrpg.create.error.range"),
                AllocationRules.validate(T, new Allocation(0, 4, 4, 4)));
    }

    @Test
    void toStarsMapsToClassFields() {
        StarResult r = AllocationRules.toStars(RpgClass.VANGUARD, new Allocation(4, 2, 3, 3));
        assertEquals(Map.of(Field.SWORD, 4, Field.DEFENSE, 3), r.talent());
        assertEquals(Map.of(Field.SWORD, 2, Field.DEFENSE, 3), r.aptitude());
    }
}
