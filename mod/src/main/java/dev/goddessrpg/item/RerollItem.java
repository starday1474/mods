package dev.goddessrpg.item;

import dev.goddessrpg.inquiry.InquiryService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Admin-only: wipes the inquiry result (knowledge kept) and reopens the inquiry. */
public class RerollItem extends Item {
    public RerollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (player instanceof ServerPlayer sp) {
            if (player.hasPermissions(2) || player.isCreative()) {
                InquiryService.reroll(sp);
            } else if (sp.connection != null) {
                sp.displayClientMessage(Component.translatable("goddessrpg.reroll.denied"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
