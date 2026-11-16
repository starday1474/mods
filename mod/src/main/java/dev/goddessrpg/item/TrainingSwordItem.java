package dev.goddessrpg.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * 수련용 검: an iron sword (same tier, damage and speed as vanilla's) that Epic Fight treats as
 * {@code goddessrpg:balanced_sword} via {@code data/goddessrpg/capabilities/weapons/training_sword.json}.
 * Uses the vanilla iron sword model until its own 3D model exists.
 */
public class TrainingSwordItem extends SwordItem {
    public TrainingSwordItem(Item.Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4F)));
    }
}
