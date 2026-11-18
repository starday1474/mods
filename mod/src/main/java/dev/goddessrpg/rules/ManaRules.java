package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.goddessrpg.player.RpgPlayerData;
import java.util.Map;

public final class ManaRules {
    private ManaRules() {}

    private static final Codec<Map<RpgClass, Double>> CLASS_DOUBLES = Codec.unboundedMap(RpgClass.CODEC, Codec.DOUBLE);

    public record ManaTuning(Map<RpgClass, Double> baseMax, Map<RpgClass, Double> regenPerSec,
                             double perSecondFieldLevel) {
        public static final Codec<ManaTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                CLASS_DOUBLES.fieldOf("base_max").forGetter(ManaTuning::baseMax),
                CLASS_DOUBLES.fieldOf("regen_per_sec").forGetter(ManaTuning::regenPerSec),
                Codec.DOUBLE.fieldOf("per_second_field_level").forGetter(ManaTuning::perSecondFieldLevel)
        ).apply(i, ManaTuning::new));

        public static final ManaTuning DEFAULT = new ManaTuning(
                Map.of(RpgClass.VANGUARD, 30.0, RpgClass.REAR_DEALER, 100.0, RpgClass.REAR_SUPPORTER, 100.0),
                Map.of(RpgClass.VANGUARD, 0.5, RpgClass.REAR_DEALER, 2.0, RpgClass.REAR_SUPPORTER, 2.0),
                2.0);
    }

    /** Max mana: class base plus a bonus per level of the class's second field; 0 without a class. */
    public static double max(ManaTuning t, RpgPlayerData d) {
        return d.rpgClass()
                .map(c -> t.baseMax().getOrDefault(c, 0.0) + d.levelOf(c.second()) * t.perSecondFieldLevel())
                .orElse(0.0);
    }
}
