package dev.goddessrpg.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** {@code data/goddessrpg/tomes/<id>.json}: knowledge gained by reading the tome and its title translation key. */
public record TomeData(double knowledge, String title) {
    public static final Codec<TomeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("knowledge").forGetter(TomeData::knowledge),
            Codec.STRING.fieldOf("title").forGetter(TomeData::title)
    ).apply(i, TomeData::new));
}
