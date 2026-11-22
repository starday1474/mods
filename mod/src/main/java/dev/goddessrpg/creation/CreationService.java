package dev.goddessrpg.creation;

import com.mojang.logging.LogUtils;
import dev.goddessrpg.creation.CreationPayloads.OpenCreationPayload;
import dev.goddessrpg.creation.CreationPayloads.SubtypeCard;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.item.ReawakeningCrystalItem;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.mana.ManaAttachment;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.AllocationRules;
import dev.goddessrpg.rules.CreationChoice;
import dev.goddessrpg.rules.InquiryData.Subtype;
import dev.goddessrpg.skill.SkillCooldowns;
import dev.goddessrpg.skill.SkillEffects;
import dev.goddessrpg.skill.SkillService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Server side of character creation (weapon card + star allocation), which replaced the goddess inquiry. Whether a
 * player is "in creation" lives only in memory and nothing half-made is ever saved, so a player who leaves before
 * confirming gets the screen again on the next login. Everything the client sends is re-validated.
 */
public final class CreationService {
    private CreationService() {}

    public static final String ERROR_DONE = "goddessrpg.create.error.done";
    public static final String ERROR_SUBTYPE = "goddessrpg.create.error.subtype";
    public static final String ERROR_NOT_OPEN = "goddessrpg.create.error.not_open";

    private static final Logger LOGGER = LogUtils.getLogger();
    /** A refused confirm is answered (chat line + screen refresh) at most once per this many ticks per player. */
    public static final int REJECT_COOLDOWN_TICKS = 10;

    /** Players with the creation screen open. */
    private static final Set<UUID> OPEN = ConcurrentHashMap.newKeySet();
    /** Open payload send attempts per player, kept across sessions so tests can see resends. */
    private static final Map<UUID, Integer> SENT = new ConcurrentHashMap<>();
    /** Server tick of the last answered refusal per player. */
    private static final Map<UUID, Integer> LAST_REJECT = new ConcurrentHashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                onLogin(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                onRespawn(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                onDimensionChange(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && isInCreation(sp)) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                onLogout(sp);
            }
        });
    }

    /** Players are invulnerable while the creation screen is open. */
    public static boolean isInCreation(ServerPlayer player) {
        return OPEN.contains(player.getUUID());
    }

    /** Number of open payloads sent to this player so far (exposed for tests). */
    public static int openCount(ServerPlayer player) {
        return SENT.getOrDefault(player.getUUID(), 0);
    }

    /** Opens creation for a player who has no character yet (a 0.2.x inquiry result counts as one). */
    public static void onLogin(ServerPlayer player) {
        if (RpgAttachments.get(player).inquiryDone()) {
            OPEN.remove(player.getUUID());
            return;
        }
        open(player);
    }

    public static void onLogout(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        SENT.remove(player.getUUID());
        LAST_REJECT.remove(player.getUUID());
        ReawakeningCrystalItem.forget(player.getUUID());
    }

    /** The client rebuilt its screen state (death screen); show creation again if it is still open. */
    public static void onRespawn(ServerPlayer player) {
        resend(player);
    }

    public static void onDimensionChange(ServerPlayer player) {
        resend(player);
    }

    private static void resend(ServerPlayer player) {
        resend(player, "");
    }

    private static void resend(ServerPlayer player, String error) {
        if (isInCreation(player)) {
            OpenCreationPayload payload = openPayload(error);
            if (payload.closes()) {
                LOGGER.error("No character creation cards loaded; not resending the creation screen to {}",
                        player.getGameProfile().getName());
                return;
            }
            send(player, payload);
        }
    }

    /**
     * Opens creation again. With {@code resetProgress} the character is wiped first (proficiency, learned skills,
     * slots, cooldowns; tomes and knowledge are kept): the admin item, {@code /goddessrpg reset} and the reawakening
     * crystal all do this. Without it, creation opens only for a player who has no character.
     */
    public static void reopen(ServerPlayer player, boolean resetProgress) {
        if (resetProgress) {
            RpgAttachments.set(player, RpgAttachments.get(player).resetKeepKnowledge());
            SkillCooldowns.clear(player);
            SkillEffects.refreshPassives(player);
            ManaAttachment.refill(player);
        } else if (RpgAttachments.get(player).inquiryDone()) {
            return;
        }
        open(player);
    }

    /**
     * Marks the player as in creation (invulnerable) and sends the screen. With no cards loaded (broken data pack)
     * nothing is marked or sent, since an empty card list would mean "close" and leave the player invulnerable.
     */
    private static void open(ServerPlayer player) {
        OpenCreationPayload payload = openPayload();
        if (payload.closes()) {
            LOGGER.error("No character creation cards loaded (check data/goddessrpg/subtypes); "
                    + "not opening creation for {}", player.getGameProfile().getName());
            return;
        }
        OPEN.add(player.getUUID());
        send(player, payload);
    }

    public static OpenCreationPayload openPayload() {
        return openPayload("");
    }

    /**
     * The cards, tuning and growth tables the screen is opened with (hidden subtypes left out, in display order), with
     * the translation key of a refusal to show, or "".
     */
    public static OpenCreationPayload openPayload(String error) {
        List<SubtypeCard> cards = RpgDataLoader.subtypes().stream()
                .filter(s -> !s.hidden())
                .sorted(Comparator.comparingInt(Subtype::order).thenComparing(Subtype::id))
                .map(s -> new SubtypeCard(s.id(), s.rpgClass(), s.weapon(), s.icon(), s.available()))
                .toList();
        return new OpenCreationPayload(cards, RpgDataLoader.allocationTuning(),
                RpgDataLoader.tuning().talentStartLevel(), RpgDataLoader.tuning().aptitudeMultiplier(), error);
    }

    /** Why a pick would be refused (a translation key), or empty when it would be accepted. Changes nothing. */
    public static Optional<String> check(ServerPlayer player, CreationChoice choice) {
        if (RpgAttachments.get(player).inquiryDone()) {
            return Optional.of(ERROR_DONE);
        }
        if (!isInCreation(player)) {
            return Optional.of(ERROR_NOT_OPEN);
        }
        if (choice == null || choice.allocation() == null) {
            return Optional.of(AllocationRules.ERROR_SUM);
        }
        Optional<Subtype> sub = RpgDataLoader.subtype(choice.subtype());
        if (sub.isEmpty() || sub.get().hidden() || !sub.get().available()) {
            return Optional.of(ERROR_SUBTYPE);
        }
        return AllocationRules.validate(RpgDataLoader.allocationTuning(), choice.allocation());
    }

    /** Confirms a pick; returns false (nothing changed) when {@link #check} refuses it. */
    public static boolean submit(ServerPlayer player, CreationChoice choice) {
        if (check(player, choice).isPresent()) {
            return false;
        }
        Subtype sub = RpgDataLoader.subtype(choice.subtype()).orElseThrow();
        RpgPlayerData data = RpgAttachments.get(player)
                .withResult(sub.rpgClass(), sub.id(), AllocationRules.toStars(sub.rpgClass(), choice.allocation()),
                        List.of(), RpgDataLoader.tuning())
                .withCreation(choice);
        OPEN.remove(player.getUUID());
        RpgAttachments.set(player, data);
        giveTome(player);
        SkillService.grantMetProficiencySkills(player);
        SkillEffects.refreshPassives(player);
        ManaAttachment.refill(player);
        send(player, OpenCreationPayload.close(RpgDataLoader.allocationTuning()));
        tell(player, Component.translatable("goddessrpg.create.done"));
        return true;
    }

    /**
     * A confirm from the client. A refusal is answered with a chat line and the screen again (carrying the reason), or
     * a close when the player is not in creation (stale screen), but at most once per {@link #REJECT_COOLDOWN_TICKS}
     * per player so a spamming client cannot flood chat and the network; refusals within that window are dropped.
     */
    public static void onSubmitPacket(ServerPlayer player, CreationChoice choice) {
        Optional<String> error = check(player, choice);
        if (error.isEmpty()) {
            submit(player, choice);
            return;
        }
        int now = player.server.getTickCount();
        Integer last = LAST_REJECT.get(player.getUUID());
        if (last != null && now - last < REJECT_COOLDOWN_TICKS && now >= last) {
            return;
        }
        LAST_REJECT.put(player.getUUID(), now);
        tell(player, Component.translatable(error.get()));
        if (isInCreation(player)) {
            resend(player, error.get()); // let a confused or forging client see the real state again
        } else {
            send(player, OpenCreationPayload.close(RpgDataLoader.allocationTuning()));
        }
    }

    private static void giveTome(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.is(RpgItems.GODDESS_TOME.get())) {
                return;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(RpgItems.GODDESS_TOME.get())) {
                return;
            }
        }
        ItemStack tome = new ItemStack(RpgItems.GODDESS_TOME.get());
        if (!player.getInventory().add(tome)) {
            player.drop(tome, false);
        }
    }

    private static void send(ServerPlayer player, OpenCreationPayload payload) {
        SENT.merge(player.getUUID(), 1, Integer::sum);
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void tell(ServerPlayer player, Component msg) {
        if (player.connection != null) {
            player.sendSystemMessage(msg);
        }
    }
}
