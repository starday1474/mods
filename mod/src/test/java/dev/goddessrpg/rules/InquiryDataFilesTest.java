package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.rules.InquiryData.Answer;
import dev.goddessrpg.rules.InquiryData.Question;
import dev.goddessrpg.rules.InquiryData.Subtype;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Validates the shipped inquiry data files (same {"questions":[...]} shape RpgDataLoader reads). */
class InquiryDataFilesTest {
    private static final Codec<List<Question>> QUESTIONS = Question.CODEC.listOf().fieldOf("questions").codec();
    private static final Set<String> CLASS_IDS = Set.of("vanguard", "rear_dealer", "rear_supporter");
    private static final Set<String> VANGUARD_SUBTYPES = Set.of("balanced", "tank", "infight");
    private static final Set<Field> VANGUARD_FIELDS = Set.of(Field.SWORD, Field.DEFENSE);

    private static Reader open(String path) {
        InputStream in = InquiryDataFilesTest.class.getResourceAsStream(path);
        assertNotNull(in, "missing resource " + path);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    private static List<Question> questions(String file) throws IOException {
        try (Reader r = open("/data/goddessrpg/inquiry/" + file)) {
            return QUESTIONS.parse(JsonOps.INSTANCE, JsonParser.parseReader(r)).getOrThrow();
        }
    }

    private static JsonObject lang(String locale) throws IOException {
        try (Reader r = open("/assets/goddessrpg/lang/" + locale + ".json")) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test
    void stage1HasThreeQuestionsOfThreeAnswersScoringClasses() throws IOException {
        List<Question> qs = questions("stage1.json");
        assertEquals(3, qs.size());
        for (Question q : qs) {
            assertEquals(3, q.answers().size(), q.id());
            for (Answer a : q.answers()) {
                assertFalse(a.scores().isEmpty(), a.textKey());
                assertTrue(CLASS_IDS.containsAll(a.scores().keySet()), a.textKey() + " " + a.scores());
                assertTrue(a.talent().isEmpty() && a.aptitude().isEmpty(), a.textKey());
            }
        }
    }

    @Test
    void stage2VanguardHasThreeQuestionsScoringSubtypes() throws IOException {
        List<Question> qs = questions("stage2/vanguard.json");
        assertEquals(3, qs.size());
        for (Question q : qs) {
            assertFalse(q.answers().isEmpty(), q.id());
            for (Answer a : q.answers()) {
                assertFalse(a.scores().isEmpty(), a.textKey());
                assertTrue(VANGUARD_SUBTYPES.containsAll(a.scores().keySet()), a.textKey() + " " + a.scores());
                assertTrue(a.talent().isEmpty() && a.aptitude().isEmpty(), a.textKey());
            }
        }
    }

    @Test
    void stage3VanguardHasFourQuestionsUsingOnlySwordAndDefense() throws IOException {
        List<Question> qs = questions("stage3/vanguard.json");
        assertEquals(4, qs.size());
        for (Question q : qs) {
            assertFalse(q.answers().isEmpty(), q.id());
            for (Answer a : q.answers()) {
                assertTrue(a.scores().isEmpty(), a.textKey());
                assertTrue(VANGUARD_FIELDS.containsAll(a.talent().keySet()), a.textKey() + " " + a.talent());
                assertTrue(VANGUARD_FIELDS.containsAll(a.aptitude().keySet()), a.textKey() + " " + a.aptitude());
                a.talent().values().forEach(v -> assertTrue(v >= -1 && v <= 2, a.textKey()));
                a.aptitude().values().forEach(v -> assertTrue(v >= -1 && v <= 2, a.textKey()));
            }
        }
    }

    @Test
    void everyClassAndVanguardSubtypeIsReachable() throws IOException {
        List<Question> s1 = questions("stage1.json");
        List<Subtype> all = List.of(new Subtype("balanced", RpgClass.VANGUARD, true),
                new Subtype("archer", RpgClass.REAR_DEALER, true), new Subtype("healer", RpgClass.REAR_SUPPORTER, true));
        Set<RpgClass> classes = new HashSet<>();
        for (List<Integer> picks : allPicks(s1)) {
            classes.add(InquiryScorer.pickClass(s1, picks, all));
        }
        assertEquals(Set.of(RpgClass.values()), classes);

        List<Question> s2 = questions("stage2/vanguard.json");
        List<Subtype> vanguard = List.of(new Subtype("balanced", RpgClass.VANGUARD, true),
                new Subtype("tank", RpgClass.VANGUARD, true), new Subtype("infight", RpgClass.VANGUARD, true));
        Set<String> subtypes = new HashSet<>();
        for (List<Integer> picks : allPicks(s2)) {
            subtypes.add(InquiryScorer.pickSubtype(s2, picks, vanguard).id());
        }
        assertEquals(VANGUARD_SUBTYPES, subtypes);
    }

    @Test
    void everyTextKeyIsTranslatedInKoreanAndEnglish() throws IOException {
        JsonObject ko = lang("ko_kr");
        JsonObject en = lang("en_us");
        List<String> keys = new ArrayList<>();
        for (String file : List.of("stage1.json", "stage2/vanguard.json", "stage3/vanguard.json")) {
            for (Question q : questions(file)) {
                keys.add(q.textKey());
                q.answers().forEach(a -> keys.add(a.textKey()));
            }
        }
        assertEquals(keys.size(), new HashSet<>(keys).size(), "duplicate text keys");
        for (String k : keys) {
            assertTrue(ko.has(k), "ko_kr missing " + k);
            assertTrue(en.has(k), "en_us missing " + k);
        }
    }

    private static List<List<Integer>> allPicks(List<Question> qs) {
        List<List<Integer>> out = new ArrayList<>();
        out.add(List.of());
        for (Question q : qs) {
            List<List<Integer>> next = new ArrayList<>();
            for (List<Integer> prefix : out) {
                for (int i = 0; i < q.answers().size(); i++) {
                    List<Integer> p = new ArrayList<>(prefix);
                    p.add(i);
                    next.add(p);
                }
            }
            out = next;
        }
        return out;
    }
}
