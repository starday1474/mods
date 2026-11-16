package dev.goddessrpg.item;

import dev.goddessrpg.inquiry.client.InquiryClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The goddess's tome: right-click shows your stats (a temporary screen until the book UI arrives). */
public class GoddessTomeItem extends Item {
    public GoddessTomeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            InquiryClient.openStats();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
