package dev.goddessrpg.tome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.goddessrpg.rules.RpgClass;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TomeRefTest {
    private static TomeRef.QuestionKeys q(String id, int answers) {
        return new TomeRef.QuestionKeys(id, java.util.stream.IntStream.rangeClosed(1, answers)
                .mapToObj(i -> id + ".a" + i).toList());
    }

    @Test
    void chosenAnswersFollowStagesInOrder() {
        TomeRef ref = new TomeRef(50, 0.5, Map.of(),
                List.of(q("s1.a", 3), q("s1.b", 3)),
                Map.of("vanguard", List.of(q("s2.a", 3))),
                Map.of("vanguard", List.of(q("s3.a", 2)), "rear_dealer", List.of(q("x", 2))));
        assertEquals(List.of("s1.a.a1", "s1.b.a3", "s2.a.a2", "s3.a.a2"),
                ref.chosenAnswerKeys(RpgClass.VANGUARD, List.of(0, 2, 1, 1)));
        // out-of-range or missing picks are skipped instead of crashing (data may have changed since)
        assertEquals(List.of("s1.a.a1", "s2.a.a2"), ref.chosenAnswerKeys(RpgClass.VANGUARD, List.of(0, 7, 1)));
        assertEquals(List.of(), ref.chosenAnswerKeys(RpgClass.VANGUARD, List.of()));
    }

    @Test
    void emptyRef() {
        assertEquals(List.of(), TomeRef.EMPTY.chosenAnswerKeys(RpgClass.VANGUARD, List.of(0, 1, 2)));
    }
}
