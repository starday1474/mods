package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

public enum RpgClass {
    VANGUARD(Field.SWORD, Field.DEFENSE),
    REAR_DEALER(Field.ARCHERY, Field.ELEMENTAL),
    REAR_SUPPORTER(Field.SUMMONING, Field.RELIC);

    public static final Codec<RpgClass> CODEC = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(valueOf(s.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown class: " + s);
        }
    }, c -> c.name().toLowerCase(Locale.ROOT));

    private final Field first;
    private final Field second;

    RpgClass(Field first, Field second) {
        this.first = first;
        this.second = second;
    }

    public Field first() {
        return first;
    }

    public Field second() {
        return second;
    }

    public boolean owns(Field f) {
        return f == first || f == second;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
