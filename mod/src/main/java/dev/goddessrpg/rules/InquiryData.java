package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Subtype data (the class keeps its pre-0.3 name; the goddess inquiry questions it once held are gone). */
public final class InquiryData {
    private InquiryData() {}

    public record Subtype(String id, RpgClass rpgClass, boolean available, String weapon, String icon, int order,
                          boolean hidden) {
        public static final Codec<Subtype> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Subtype::id),
                RpgClass.CODEC.fieldOf("class").forGetter(Subtype::rpgClass),
                Codec.BOOL.optionalFieldOf("available", true).forGetter(Subtype::available),
                Codec.STRING.optionalFieldOf("weapon", "").forGetter(Subtype::weapon),
                Codec.STRING.optionalFieldOf("icon", "minecraft:barrier").forGetter(Subtype::icon),
                Codec.INT.optionalFieldOf("order", 99).forGetter(Subtype::order),
                Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Subtype::hidden)
        ).apply(i, Subtype::new));

        public Subtype(String id, RpgClass rpgClass, boolean available) {
            this(id, rpgClass, available, "", "minecraft:barrier", 99, false);
        }
    }
}
