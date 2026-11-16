package dev.goddessrpg.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goddessrpg.growth.WeaponFields;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeaponFieldsTest {
    @Test
    void meleeCategoriesAreSword() {
        for (String c : new String[] {"sword", "longsword", "greatsword", "tachi", "uchigatana", "dagger", "axe",
                "spear", "trident", "fist", "hoe"}) {
            assertEquals(Optional.of(Field.SWORD), WeaponFields.fieldFor(c), c);
        }
    }

    @Test
    void rangedCategoriesAreArchery() {
        assertEquals(Optional.of(Field.ARCHERY), WeaponFields.fieldFor("bow"));
        assertEquals(Optional.of(Field.ARCHERY), WeaponFields.fieldFor("crossbow"));
    }

    @Test
    void othersAreEmpty() {
        assertTrue(WeaponFields.fieldFor("shield").isEmpty());
        assertTrue(WeaponFields.fieldFor("not_weapon").isEmpty());
        assertTrue(WeaponFields.fieldFor("pickaxe").isEmpty());
    }
}
