package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

public record GrowthTuning(
        Map<Integer, Integer> talentStartLevel,
        Map<Integer, Double> aptitudeMultiplier,
        double knowledgePerTome,
        double knowledgeCap,
        double xpBase,
        double xpExponent,
        int maxLevel,
        double hitXp,
        double guardXpBase,
        double guardXpCap,
        double offClassMultiplier) {

    private static final Codec<Integer> INT_KEY = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not an integer key: " + s);
        }
    }, String::valueOf);

    public static final Codec<GrowthTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(INT_KEY, Codec.INT).fieldOf("talent_start_level").forGetter(GrowthTuning::talentStartLevel),
            Codec.unboundedMap(INT_KEY, Codec.DOUBLE).fieldOf("aptitude_multiplier").forGetter(GrowthTuning::aptitudeMultiplier),
            Codec.DOUBLE.fieldOf("knowledge_per_tome").forGetter(GrowthTuning::knowledgePerTome),
            Codec.DOUBLE.fieldOf("knowledge_cap").forGetter(GrowthTuning::knowledgeCap),
            Codec.DOUBLE.fieldOf("xp_base").forGetter(GrowthTuning::xpBase),
            Codec.DOUBLE.fieldOf("xp_exponent").forGetter(GrowthTuning::xpExponent),
            Codec.INT.fieldOf("max_level").forGetter(GrowthTuning::maxLevel),
            Codec.DOUBLE.fieldOf("hit_xp").forGetter(GrowthTuning::hitXp),
            Codec.DOUBLE.fieldOf("guard_xp_base").forGetter(GrowthTuning::guardXpBase),
            Codec.DOUBLE.fieldOf("guard_xp_cap").forGetter(GrowthTuning::guardXpCap),
            Codec.DOUBLE.fieldOf("off_class_multiplier").forGetter(GrowthTuning::offClassMultiplier)
    ).apply(i, GrowthTuning::new));

    public static final GrowthTuning DEFAULT = new GrowthTuning(
            ordered(1, 0, 2, 2, 3, 4, 4, 6, 5, 8),
            ordered(1, 0.6, 2, 0.8, 3, 1.0, 4, 1.25, 5, 1.5),
            0.05, 0.50, 40.0, 1.6, 50, 3.0, 2.0, 10.0, 0.3);

    @SuppressWarnings("unchecked")
    private static <V> Map<Integer, V> ordered(Object... kv) {
        Map<Integer, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Integer) kv[i], (V) kv[i + 1]);
        }
        return Map.copyOf(m);
    }
}
