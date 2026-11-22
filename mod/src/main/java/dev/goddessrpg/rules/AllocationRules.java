package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.goddessrpg.rules.CreationChoice.Allocation;
import java.util.Map;
import java.util.Optional;

public final class AllocationRules {
    private AllocationRules() {}

    public static final String ERROR_SUM = "goddessrpg.create.error.sum";
    public static final String ERROR_RANGE = "goddessrpg.create.error.range";

    public record AllocationTuning(int budget, int min, int max) {
        public static final Codec<AllocationTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("budget").forGetter(AllocationTuning::budget),
                Codec.INT.fieldOf("min").forGetter(AllocationTuning::min),
                Codec.INT.fieldOf("max").forGetter(AllocationTuning::max)
        ).apply(i, AllocationTuning::new));

        public static final AllocationTuning DEFAULT = new AllocationTuning(12, 1, 5);
    }

    /** Returns a translation key for the first problem found, or empty when the allocation is valid. */
    public static Optional<String> validate(AllocationTuning t, Allocation a) {
        int[] v = {a.talentFirst(), a.aptitudeFirst(), a.talentSecond(), a.aptitudeSecond()};
        int sum = 0;
        for (int s : v) {
            if (s < t.min() || s > t.max()) {
                return Optional.of(ERROR_RANGE);
            }
            sum += s;
        }
        return sum == t.budget() ? Optional.empty() : Optional.of(ERROR_SUM);
    }

    public static StarResult toStars(RpgClass c, Allocation a) {
        return new StarResult(
                Map.of(c.first(), a.talentFirst(), c.second(), a.talentSecond()),
                Map.of(c.first(), a.aptitudeFirst(), c.second(), a.aptitudeSecond()));
    }
}
