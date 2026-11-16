package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.goddessrpg.rules.InquiryData.Answer;
import dev.goddessrpg.rules.InquiryData.Question;
import dev.goddessrpg.rules.InquiryData.Subtype;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InquiryScorerTest {
    private static Answer score(Map<String, Integer> s) {
        return new Answer("k", s, Map.of(), Map.of());
    }

    private static Answer stars(Map<Field, Integer> talent, Map<Field, Integer> apt) {
        return new Answer("k", Map.of(), talent, apt);
    }

    private static final List<Subtype> SUBS = List.of(
            new Subtype("balance", RpgClass.VANGUARD, true),
            new Subtype("archer", RpgClass.REAR_DEALER, true),
            new Subtype("buffer", RpgClass.REAR_SUPPORTER, true));

    @Test
    void pickClassHighest() {
        Question q = new Question("q1", "t", List.of(
                score(Map.of("vanguard", 1)),
                score(Map.of("rear_dealer", 2, "vanguard", 1))));
        assertEquals(RpgClass.REAR_DEALER, InquiryScorer.pickClass(List.of(q), List.of(1), SUBS));
        assertEquals(RpgClass.VANGUARD, InquiryScorer.pickClass(List.of(q), List.of(0), SUBS));
    }

    @Test
    void tiePicksFirstDeclared() {
        Question q = new Question("q1", "t", List.of(
                score(new java.util.LinkedHashMap<>(Map.of("rear_supporter", 2))),
                score(Map.of("vanguard", 2))));
        // both a single answer each; build an explicit tie within one answer with ordered map
        java.util.LinkedHashMap<String, Integer> ordered = new java.util.LinkedHashMap<>();
        ordered.put("rear_dealer", 1);
        ordered.put("vanguard", 1);
        Question q2 = new Question("q2", "t", List.of(score(ordered)));
        assertEquals(RpgClass.REAR_DEALER, InquiryScorer.pickClass(List.of(q2), List.of(0), SUBS));
        assertEquals(RpgClass.REAR_SUPPORTER, InquiryScorer.pickClass(List.of(q), List.of(0), SUBS));
    }

    @Test
    void pickSubtypeTieFirstInList() {
        List<Subtype> subs = List.of(
                new Subtype("a", RpgClass.VANGUARD, true),
                new Subtype("b", RpgClass.VANGUARD, true));
        Question q = new Question("q", "t", List.of(score(Map.of("a", 1, "b", 1))));
        assertEquals("a", InquiryScorer.pickSubtype(List.of(q), List.of(0), subs).id());
    }

    @Test
    void unavailableSubtypesExcluded() {
        List<Subtype> subs = List.of(
                new Subtype("balance", RpgClass.VANGUARD, true),
                new Subtype("archer", RpgClass.REAR_DEALER, false));
        Question q = new Question("q", "t", List.of(score(Map.of("rear_dealer", 9, "vanguard", 1))));
        assertEquals(RpgClass.VANGUARD, InquiryScorer.pickClass(List.of(q), List.of(0), subs));
    }

    @Test
    void starsClamped() {
        Question q = new Question("q", "t", List.of(
                stars(Map.of(Field.SWORD, 5, Field.DEFENSE, -7), Map.of(Field.SWORD, 5, Field.DEFENSE, -7)),
                stars(Map.of(Field.ARCHERY, 1), Map.of())));
        StarResult r = InquiryScorer.stars(List.of(q), List.of(0), RpgClass.VANGUARD);
        assertEquals(5, r.talent().get(Field.SWORD));
        assertEquals(1, r.talent().get(Field.DEFENSE));
        assertEquals(5, r.aptitude().get(Field.SWORD));
        assertEquals(1, r.aptitude().get(Field.DEFENSE));
    }

    @Test
    void starsOnlyOwnFieldsAndDefaultThree() {
        Question q = new Question("q", "t", List.of(stars(Map.of(Field.ARCHERY, 2), Map.of())));
        StarResult r = InquiryScorer.stars(List.of(q), List.of(0), RpgClass.VANGUARD);
        assertEquals(java.util.Set.of(Field.SWORD, Field.DEFENSE), r.talent().keySet());
        assertEquals(java.util.Set.of(Field.SWORD, Field.DEFENSE), r.aptitude().keySet());
        assertEquals(3, r.talent().get(Field.SWORD));
    }

    @Test
    void badPicksRejected() {
        Question q = new Question("q", "t", List.of(score(Map.of("vanguard", 1))));
        assertThrows(IllegalArgumentException.class, () -> InquiryScorer.pickClass(List.of(q), List.of(5), SUBS));
        assertThrows(IllegalArgumentException.class, () -> InquiryScorer.pickClass(List.of(q), List.of(-1), SUBS));
        assertThrows(IllegalArgumentException.class, () -> InquiryScorer.pickClass(List.of(q), List.of(), SUBS));
        assertThrows(IllegalArgumentException.class,
                () -> InquiryScorer.stars(List.of(q), List.of(0, 0), RpgClass.VANGUARD));
    }
}
