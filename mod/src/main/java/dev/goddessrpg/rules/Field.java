package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

public enum Field {
    SWORD, DEFENSE, ARCHERY, ELEMENTAL, SUMMONING, RELIC;

    public static final Codec<Field> CODEC = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(valueOf(s.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown field: " + s);
        }
    }, f -> f.name().toLowerCase(Locale.ROOT));
}
