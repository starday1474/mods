package dev.goddessrpg.rules;

import dev.goddessrpg.rules.InquiryData.Answer;
import dev.goddessrpg.rules.InquiryData.Question;
import dev.goddessrpg.rules.InquiryData.Subtype;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InquiryScorer {
    private InquiryScorer() {}

    /** Resolves picks to the chosen answers; rejects length mismatch and out-of-range picks. */
    private static List<Answer> chosen(List<Question> questions, List<Integer> picks) {
        if (questions == null || picks == null || questions.size() != picks.size()) {
            throw new IllegalArgumentException("picks length does not match questions");
        }
        List<Answer> out = new ArrayList<>(picks.size());
        for (int i = 0; i < picks.size(); i++) {
            Integer p = picks.get(i);
            List<Answer> answers = questions.get(i).answers();
            if (p == null || p < 0 || p >= answers.size()) {
                throw new IllegalArgumentException("pick out of range at question " + i);
            }
            out.add(answers.get(p));
        }
        return out;
    }

    /** Sums scores per key. */
    private static Map<String, Integer> sum(List<Answer> answers) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Answer a : answers) {
            a.scores().forEach((k, v) -> totals.merge(k, v, Integer::sum));
        }
        return totals;
    }

    public static RpgClass pickClass(List<Question> stage1, List<Integer> picks, List<Subtype> subtypes) {
        Map<String, Integer> totals = sum(chosen(stage1, picks));
        List<RpgClass> candidates = new ArrayList<>();
        for (RpgClass c : RpgClass.values()) {
            if (subtypes.stream().anyMatch(s -> s.rpgClass() == c && s.available())) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("no available subtype in any class");
        }
        RpgClass best = null;
        int bestScore = Integer.MIN_VALUE;
        for (RpgClass c : candidates) {
            int s = totals.getOrDefault(c.id(), 0);
            if (s > bestScore) {
                best = c;
                bestScore = s;
            }
        }
        return best;
    }

    public static Subtype pickSubtype(List<Question> stage2, List<Integer> picks, List<Subtype> subtypesOfClass) {
        Map<String, Integer> totals = sum(chosen(stage2, picks));
        Subtype best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Subtype s : subtypesOfClass) {
            if (!s.available()) {
                continue;
            }
            int score = totals.getOrDefault(s.id(), 0);
            if (score > bestScore) {
                best = s;
                bestScore = score;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("no available subtype");
        }
        return best;
    }

    public static StarResult stars(List<Question> stage3, List<Integer> picks, RpgClass c) {
        List<Answer> answers = chosen(stage3, picks);
        Map<Field, Integer> talent = new EnumMap<>(Field.class);
        Map<Field, Integer> aptitude = new EnumMap<>(Field.class);
        for (Field f : new Field[] {c.first(), c.second()}) {
            int t = 0;
            int a = 0;
            for (Answer ans : answers) {
                t += ans.talent().getOrDefault(f, 0);
                a += ans.aptitude().getOrDefault(f, 0);
            }
            talent.put(f, Stars.clamp(Stars.BASE + t));
            aptitude.put(f, Stars.clamp(Stars.BASE + a));
        }
        return new StarResult(talent, aptitude);
    }
}
