package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A player's character creation pick: a subtype plus the four-slot star allocation. */
public record CreationChoice(String subtype, Allocation allocation) {
    public static final Codec<CreationChoice> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("subtype").forGetter(CreationChoice::subtype),
            Allocation.CODEC.fieldOf("allocation").forGetter(CreationChoice::allocation)
    ).apply(i, CreationChoice::new));

    public static final StreamCodec<ByteBuf, CreationChoice> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CreationChoice::subtype,
            Allocation.STREAM_CODEC, CreationChoice::allocation,
            CreationChoice::new);

    /** Stars for (first field talent, first field aptitude, second field talent, second field aptitude). */
    public record Allocation(int talentFirst, int aptitudeFirst, int talentSecond, int aptitudeSecond) {
        public static final Codec<Allocation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("talent_first").forGetter(Allocation::talentFirst),
                Codec.INT.fieldOf("aptitude_first").forGetter(Allocation::aptitudeFirst),
                Codec.INT.fieldOf("talent_second").forGetter(Allocation::talentSecond),
                Codec.INT.fieldOf("aptitude_second").forGetter(Allocation::aptitudeSecond)
        ).apply(i, Allocation::new));

        public static final StreamCodec<ByteBuf, Allocation> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Allocation::talentFirst,
                ByteBufCodecs.VAR_INT, Allocation::aptitudeFirst,
                ByteBufCodecs.VAR_INT, Allocation::talentSecond,
                ByteBufCodecs.VAR_INT, Allocation::aptitudeSecond,
                Allocation::new);
    }
}
