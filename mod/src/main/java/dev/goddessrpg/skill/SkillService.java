package dev.goddessrpg.skill;

import dev.goddessrpg.compat.epicfight.SkillAnimations;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.mana.ManaAttachment;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.rules.SkillRules;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-side learning and slot handling. All rules are re-checked here; clients are never trusted. */
public final class SkillService {
    private SkillService() {}

    public enum LearnResult { OK, ALREADY, NOT_ALLOWED, UNKNOWN }

    /** BUSY: another attack animation is playing. */
    public enum CastResult { OK, EMPTY_SLOT, NOT_LEARNED, WRONG_CLASS, COOLDOWN, NO_STAMINA, NO_MANA, BUSY }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                grantMetProficiencySkills(sp);
            }
        });
        // once a second: remember skill books the player holds (any way they got there), for the tome's mind map
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 20 == 0) {
                markSeenBooks(sp);
            }
        });
    }

    /** Adds the skill ids of every skill book in the player's inventory to their seen set (synced only on change). */
    public static void markSeenBooks(ServerPlayer player) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(RpgItems.SKILL_BOOK.get())) {
                String id = s.get(RpgItems.SKILL_ID.get());
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        RpgPlayerData data = RpgAttachments.get(player);
        RpgPlayerData next = data.withSeenSkillBooks(ids);
        if (next != data) {
            RpgAttachments.set(player, next);
        }
    }

    public static LearnResult learn(ServerPlayer player, String id) {
        Optional<SkillDef> def = SkillRegistry.get(id);
        if (def.isEmpty()) {
            return LearnResult.UNKNOWN;
        }
        RpgPlayerData data = RpgAttachments.get(player);
        if (data.learnedSkills().contains(id)) {
            return LearnResult.ALREADY;
        }
        if (!SkillRules.canLearn(def.get(), data)) {
            return LearnResult.NOT_ALLOWED;
        }
        RpgPlayerData next = data.withLearned(id);
        if (def.get().kind() == SkillDef.Kind.ACTIVE) {
            int free = next.slots().indexOf(Optional.<String>empty());
            if (free >= 0) {
                next = next.withSlot(free, Optional.of(id));
            }
        }
        RpgAttachments.set(player, next);
        if (def.get().kind() == SkillDef.Kind.PASSIVE) {
            SkillEffects.refreshPassives(player);
        }
        if (player.connection != null) {
            // chat, not actionbar: the level-up actionbar in the same tick would overwrite it
            player.sendSystemMessage(Component.translatable("goddessrpg.skill.learned", skillName(id)));
        }
        return LearnResult.OK;
    }

    /**
     * Network entry for the tome's slot picker. The client is not trusted: the skill must exist, belong to the player's
     * class/subtype, be learned and be active, and the index must be 0..3. Shows a short message when refused.
     */
    public static boolean onSlotSetRequest(ServerPlayer player, SkillPayloads.SlotSetPayload p) {
        boolean ok = p.skillId().map(id -> SkillRegistry.get(id)
                        .filter(def -> SkillRules.canLearn(def, RpgAttachments.get(player))).isPresent())
                .orElse(true)
                && setSlot(player, p.slot(), p.skillId());
        if (!ok && player.connection != null) {
            player.displayClientMessage(Component.translatable("goddessrpg.tome.slot_refused"), true);
        }
        return ok;
    }

    public static boolean setSlot(ServerPlayer player, int index, Optional<String> id) {
        try {
            RpgAttachments.set(player, RpgAttachments.get(player).withSlot(index, id));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Learns every proficiency-unlock skill the player already meets and has not learned. Call after anything that
     * can change levels or class (inquiry result, login, level-up, level command).
     */
    public static void grantMetProficiencySkills(ServerPlayer player) {
        RpgPlayerData data = RpgAttachments.get(player);
        if (!data.inquiryDone()) {
            return;
        }
        for (SkillDef s : SkillRegistry.all().values()) {
            if (s.unlock() instanceof SkillDef.Unlock.Proficiency p && data.levelOf(p.field()) >= p.level()
                    && !data.learnedSkills().contains(s.id())) {
                learn(player, s.id());
            }
        }
    }

    /**
     * Casts the skill in slot {@code slot} (0..3). The slot content is never trusted: it may hold a stale id (data
     * changed), a skill of another class, or one the player never learned. Checks run in the order of the results below;
     * nothing is spent or started unless the result is OK.
     */
    public static CastResult cast(ServerPlayer player, int slot) {
        RpgPlayerData data = RpgAttachments.get(player);
        if (slot < 0 || slot >= RpgPlayerData.SLOT_COUNT || data.slots().get(slot).isEmpty()) {
            return CastResult.EMPTY_SLOT;
        }
        String id = data.slots().get(slot).get();
        Optional<SkillDef> found = SkillRegistry.get(id);
        if (found.isEmpty()) {
            return CastResult.NOT_LEARNED;
        }
        SkillDef def = found.get();
        if (!SkillRules.canLearn(def, data)) {
            return CastResult.WRONG_CLASS;
        }
        if (!data.learnedSkills().contains(id) || def.kind() != SkillDef.Kind.ACTIVE) {
            return CastResult.NOT_LEARNED;
        }
        if (!SkillCooldowns.isReady(player, id)) {
            return CastResult.COOLDOWN;
        }
        if (!player.isAlive() || player.isSpectator() || SkillAnimations.busy(player)) {
            return CastResult.BUSY;
        }
        if (!SkillAnimations.hasStamina(player, def.staminaCost())) {
            return CastResult.NO_STAMINA;
        }
        if (def.manaCost() > 0 && ManaAttachment.get(player) < def.manaCost()) {
            return CastResult.NO_MANA;
        }
        if (def.manaCost() > 0 && !ManaAttachment.tryConsume(player, def.manaCost())) {
            return CastResult.NO_MANA;
        }
        SkillAnimations.consumeStamina(player, def.staminaCost());
        SkillCooldowns.start(player, id, def.cooldownSec());
        SkillEffects.apply(player, def);
        return CastResult.OK;
    }

    /** Network entry: casts and shows a short actionbar message on rejection. */
    public static void onCastRequest(ServerPlayer player, int slot) {
        CastResult r = cast(player, slot);
        if (r != CastResult.OK && player.connection != null) {
            player.displayClientMessage(Component.translatable("goddessrpg.cast." + r.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
    }

    public static Component skillName(String id) {
        return Component.translatable("goddessrpg.skill." + id);
    }
}
