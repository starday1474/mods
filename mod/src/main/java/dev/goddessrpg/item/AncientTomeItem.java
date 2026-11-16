package dev.goddessrpg.item;

import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.data.TomeData;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** An ancient tome (data component goddessrpg:tome_id). Reading it once raises knowledge; the book is consumed. */
public class AncientTomeItem extends Item {
    public AncientTomeItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = stack.get(RpgItems.TOME_ID.get());
        return id == null ? super.getName(stack) : Component.translatable("goddessrpg.tome." + id);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            read(sp, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Server side: applies the tome in the given hand. Unknown or already-read tomes are not consumed. */
    public static void read(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String id = stack.get(RpgItems.TOME_ID.get());
        Optional<TomeData> tome = id == null ? Optional.empty() : RpgDataLoader.tome(id);
        if (tome.isEmpty()) {
            tell(player, Component.translatable("goddessrpg.tome.unknown"));
            return;
        }
        RpgPlayerData data = RpgAttachments.get(player);
        if (data.tomesRead().contains(id)) {
            tell(player, Component.translatable("goddessrpg.tome.already_read"));
            return;
        }
        RpgAttachments.set(player, data.withTome(id, tome.get().knowledge(), RpgDataLoader.tuning().knowledgeCap()));
        tell(player, Component.translatable("goddessrpg.tome.read", Component.translatable("goddessrpg.tome." + id)));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static void tell(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendSystemMessage(message, true);
        }
    }
}
