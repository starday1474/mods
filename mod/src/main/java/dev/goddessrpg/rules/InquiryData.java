package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;

public final class InquiryData {
    private InquiryData() {}

    private static final Codec<Map<Field, Integer>> FIELD_INTS = Codec.unboundedMap(Field.CODEC, Codec.INT);

    public record Answer(String textKey, Map<String, Integer> scores, Map<Field, Integer> talent,
                         Map<Field, Integer> aptitude) {
        public static final Codec<Answer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Answer::textKey),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("scores", Map.of()).forGetter(Answer::scores),
                FIELD_INTS.optionalFieldOf("talent", Map.of()).forGetter(Answer::talent),
                FIELD_INTS.optionalFieldOf("aptitude", Map.of()).forGetter(Answer::aptitude)
        ).apply(i, Answer::new));
    }

    public record Question(String id, String textKey, List<Answer> answers) {
        public static final Codec<Question> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Question::id),
                Codec.STRING.fieldOf("text").forGetter(Question::textKey),
                Answer.CODEC.listOf().fieldOf("answers").forGetter(Question::answers)
        ).apply(i, Question::new));
    }

    public record Subtype(String id, RpgClass rpgClass, boolean available) {
        public static final Codec<Subtype> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Subtype::id),
                RpgClass.CODEC.fieldOf("class").forGetter(Subtype::rpgClass),
                Codec.BOOL.optionalFieldOf("available", true).forGetter(Subtype::available)
        ).apply(i, Subtype::new));
    }
}
