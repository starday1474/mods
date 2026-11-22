package dev.goddessrpg.tome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;

/**
 * Server reference data the tome needs on the client, which has no data packs of its own: the level cap and
 * knowledge per ancient tome. Sent with the skill definitions on login and after {@code /reload}. (The player's
 * character creation pick travels with the player data itself.)
 */
public record TomeRef(int maxLevel, double knowledgeCap, Map<String, Double> tomeKnowledge) {

    public static final TomeRef EMPTY = new TomeRef(50, 0.5, Map.of());

    public static final Codec<TomeRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("max_level").forGetter(TomeRef::maxLevel),
            Codec.DOUBLE.fieldOf("knowledge_cap").forGetter(TomeRef::knowledgeCap),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("tomes").forGetter(TomeRef::tomeKnowledge)
    ).apply(i, TomeRef::new));

    public TomeRef {
        tomeKnowledge = Map.copyOf(tomeKnowledge);
    }
}
