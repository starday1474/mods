package dev.goddessrpg.item;

import dev.goddessrpg.tome.client.TomeClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The goddess's tome: right-click opens the book UI (stats, skill mind map, knowledge, goddess's words). */
public class GoddessTomeItem extends Item {
    public GoddessTomeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            TomeClient.open();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
