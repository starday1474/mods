package dev.goddessrpg.compat.epicfight;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import yesman.epicfight.registry.entries.EpicFightSkills;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillSlot;
import yesman.epicfight.skill.SkillSlots;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/**
 * Only dodge and guard are used from Epic Fight; skill books are blocked. Epic Fight 21.17.3.1 API used:
 * EpicFightCapabilities.getServerPlayerPatch(ServerPlayer), PlayerPatch.getSkill(SkillSlot) -> SkillContainer,
 * SkillContainer.setSkill(Skill) / getSkill() / createSyncPacketToLocalPlayer() (SPChangeSkill),
 * EpicFightSkills.ROLL / GUARD (ids epicfight:roll, epicfight:guard), skill book item id epicfight:skillbook.
 */
public final class EfSkillPolicy {
    private EfSkillPolicy() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation SKILLBOOK = ResourceLocation.fromNamespaceAndPath("epicfight", "skillbook");

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                ensureBaseSkills(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                ensureBaseSkills(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener(EfSkillPolicy::onRightClickItem);
    }

    /** Puts roll in the dodge slot and guard in the guard slot (always overwriting) and syncs the client. */
    public static void ensureBaseSkills(ServerPlayer player) {
        ServerPlayerPatch patch = EpicFightCapabilities.getServerPlayerPatch(player);
        if (patch == null) {
            LOG.debug("No Epic Fight patch on {}; skipping base skills", player.getGameProfile().getName());
            return;
        }
        ensure(player, patch, SkillSlots.DODGE, EpicFightSkills.ROLL.get());
        ensure(player, patch, SkillSlots.GUARD, EpicFightSkills.GUARD.get());
    }

    private static void ensure(ServerPlayer player, ServerPlayerPatch patch, SkillSlot slot, Skill skill) {
        SkillContainer container = patch.getSkill(slot);
        if (container.getSkill() == skill) {
            return;
        }
        container.setSkill(skill);
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, container.createSyncPacketToLocalPlayer());
        }
    }

    public static boolean isBlockedSkillbook(ItemStack stack) {
        return !stack.isEmpty() && SKILLBOOK.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isBlockedSkillbook(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer sp && sp.connection != null) {
            sp.displayClientMessage(Component.translatable("goddessrpg.skillbook.blocked"), true);
        }
    }
}
