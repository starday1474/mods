package dev.goddessrpg.item;

import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.rules.SkillRules;
import dev.goddessrpg.skill.SkillRegistry;
import dev.goddessrpg.skill.SkillService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A skill book (data component goddessrpg:skill_id). Using it learns the skill; consumed only on success. */
public class SkillBookItem extends Item {
    public SkillBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = stack.get(RpgItems.SKILL_ID.get());
        return id == null ? super.getName(stack)
                : Component.translatable("item.goddessrpg.skill_book.named", SkillService.skillName(id));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        String id = stack.get(RpgItems.SKILL_ID.get());
        if (id == null) {
            return;
        }
        tip.add(SkillService.skillName(id).copy().withStyle(ChatFormatting.GRAY));
        SkillDef def = SkillRegistry.get(id).orElse(null);
        if (def != null) {
            tip.add(Component.translatable("goddessrpg.class." + def.rpgClass().id()).withStyle(ChatFormatting.DARK_GRAY));
            Player local = localPlayer();
            if (local != null && !SkillRules.canLearn(def, RpgAttachments.get(local))) {
                tip.add(Component.translatable("goddessrpg.skillbook.not_your_path").withStyle(ChatFormatting.RED));
            }
        }
    }

    /** Client-only lookup, kept out of the server class path by going through a holder. */
    private static Player localPlayer() {
        return net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? ClientPlayerHolder.get() : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            use(sp, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Server side: learns the skill in the given hand's book. Consumed only when the skill was newly learned. */
    public static void use(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String id = stack.get(RpgItems.SKILL_ID.get());
        if (id == null) {
            tell(player, Component.translatable("goddessrpg.skillbook.unknown"));
            return;
        }
        switch (SkillService.learn(player, id)) {
            case OK -> {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            case ALREADY -> tell(player, Component.translatable("goddessrpg.skillbook.already"));
            case NOT_ALLOWED -> tell(player, Component.translatable("goddessrpg.skillbook.not_your_path"));
            case UNKNOWN -> tell(player, Component.translatable("goddessrpg.skillbook.unknown"));
        }
    }

    private static void tell(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendSystemMessage(message, true);
        }
    }

    /** Separate class so the client Minecraft class is only loaded on the client. */
    private static final class ClientPlayerHolder {
        static Player get() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }
}
