package dev.goddessrpg.growth;

import dev.goddessrpg.rules.Field;
import java.util.Locale;
import java.util.Optional;

/** Maps an Epic Fight weapon category name to the proficiency field it trains. */
public final class WeaponFields {
    private WeaponFields() {}

    public static Optional<Field> fieldFor(String efCategory) {
        if (efCategory == null) {
            return Optional.empty();
        }
        return switch (efCategory.toLowerCase(Locale.ROOT)) {
            case "sword", "longsword", "greatsword", "tachi", "uchigatana", "dagger", "axe", "spear", "trident",
                    "fist", "hoe" -> Optional.of(Field.SWORD);
            case "bow", "crossbow" -> Optional.of(Field.ARCHERY);
            default -> Optional.empty();
        };
    }
}
