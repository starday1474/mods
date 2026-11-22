package dev.goddessrpg.item;

import dev.goddessrpg.creation.CreationService;
import dev.goddessrpg.player.RpgAttachments;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Consumable: wipes the character (proficiency, learned skills, slots, cooldowns; tomes and knowledge kept) and opens
 * character creation again. Only for a player who has a character. The first use only arms it and says so on the
 * action bar; a second use within {@link #CONFIRM_TICKS} does it and consumes one crystal.
 */
public class ReawakeningCrystalItem extends Item {
    public static final int CONFIRM_TICKS = 100;
    /** Item cooldown after arming, so holding the use button does not confirm by itself. */
    private static final int ARM_COOLDOWN_TICKS = 10;
    /** Game time until which a player's second use confirms. */
    private static final Map<UUID, Long> ARMED_UNTIL = new ConcurrentHashMap<>();

    public ReawakeningCrystalItem(Properties properties) {
        super(properties);
    }

    public static void forget(UUID id) {
        ARMED_UNTIL.remove(id);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!RpgAttachments.get(sp).inquiryDone()) {
            forget(sp.getUUID());
            actionBar(sp, Component.translatable("goddessrpg.crystal.not_created"));
            return InteractionResultHolder.fail(stack);
        }
        long now = level.getGameTime();
        Long until = ARMED_UNTIL.get(sp.getUUID());
        if (until == null || now > until) {
            ARMED_UNTIL.put(sp.getUUID(), now + CONFIRM_TICKS);
            if (sp.connection != null) { // the cooldown is synced to the client right away
                sp.getCooldowns().addCooldown(this, ARM_COOLDOWN_TICKS);
            }
            actionBar(sp, Component.translatable("goddessrpg.crystal.confirm"));
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        forget(sp.getUUID());
        stack.consume(1, sp);
        CreationService.reopen(sp, true);
        if (sp.connection != null) {
            sp.sendSystemMessage(Component.translatable("goddessrpg.crystal.used"));
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static void actionBar(ServerPlayer sp, Component msg) {
        if (sp.connection != null) {
            sp.displayClientMessage(msg, true);
        }
    }
}
