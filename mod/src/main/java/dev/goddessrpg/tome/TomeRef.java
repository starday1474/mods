package dev.goddessrpg.tome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.goddessrpg.rules.RpgClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server reference data the goddess tome needs on the client, which has no data packs of its own: the level cap,
 * knowledge per ancient tome, and the inquiry question/answer text keys (to turn the stored answer indices back into
 * the sentences the player chose). Sent with the skill definitions on login and after {@code /reload}.
 *
 * @param stage2 class id -> questions; {@code stage3} likewise
 */
public record TomeRef(int maxLevel, double knowledgeCap, Map<String, Double> tomeKnowledge, List<QuestionKeys> stage1,
                      Map<String, List<QuestionKeys>> stage2, Map<String, List<QuestionKeys>> stage3) {

    public static final TomeRef EMPTY = new TomeRef(50, 0.5, Map.of(), List.of(), Map.of(), Map.of());

    public record QuestionKeys(String textKey, List<String> answerKeys) {
        public QuestionKeys {
            answerKeys = List.copyOf(answerKeys);
        }

        public static final Codec<QuestionKeys> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(QuestionKeys::textKey),
                Codec.STRING.listOf().fieldOf("answers").forGetter(QuestionKeys::answerKeys)
        ).apply(i, QuestionKeys::new));
    }

    private static final Codec<Map<String, List<QuestionKeys>>> BY_CLASS =
            Codec.unboundedMap(Codec.STRING, QuestionKeys.CODEC.listOf());

    public static final Codec<TomeRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("max_level").forGetter(TomeRef::maxLevel),
            Codec.DOUBLE.fieldOf("knowledge_cap").forGetter(TomeRef::knowledgeCap),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("tomes").forGetter(TomeRef::tomeKnowledge),
            QuestionKeys.CODEC.listOf().fieldOf("stage1").forGetter(TomeRef::stage1),
            BY_CLASS.fieldOf("stage2").forGetter(TomeRef::stage2),
            BY_CLASS.fieldOf("stage3").forGetter(TomeRef::stage3)
    ).apply(i, TomeRef::new));

    public TomeRef {
        tomeKnowledge = Map.copyOf(tomeKnowledge);
        stage1 = List.copyOf(stage1);
        stage2 = Map.copyOf(stage2);
        stage3 = Map.copyOf(stage3);
    }

    /**
     * The answer text keys for the stored picks (stage 1, then the class's stage 2 and 3, one pick per question, in the
     * same order the inquiry concatenates them). Picks that do not match a question or answer are skipped, since the
     * data may have changed after the inquiry.
     */
    public List<String> chosenAnswerKeys(RpgClass c, List<Integer> picks) {
        List<QuestionKeys> all = new ArrayList<>(stage1);
        all.addAll(stage2.getOrDefault(c.id(), List.of()));
        all.addAll(stage3.getOrDefault(c.id(), List.of()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < picks.size() && i < all.size(); i++) {
            List<String> answers = all.get(i).answerKeys();
            int pick = picks.get(i);
            if (pick >= 0 && pick < answers.size()) {
                out.add(answers.get(pick));
            }
        }
        return out;
    }
}
