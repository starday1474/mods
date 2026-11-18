package dev.goddessrpg.inquiry;

import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.inquiry.InquiryPayloads.QuestionView;
import dev.goddessrpg.inquiry.InquiryPayloads.QuestionsPayload;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.InquiryData.Question;
import dev.goddessrpg.rules.InquiryData.Subtype;
import dev.goddessrpg.rules.InquiryScorer;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.StarResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the goddess inquiry. In-progress answers live only in memory (never saved), so a player who
 * leaves mid-inquiry starts over from stage 1 on the next login. All input from the client is re-validated.
 */
public final class InquiryService {
    private InquiryService() {}

    private static final class Progress {
        int stage = 1;
        final List<Integer> picks1 = new ArrayList<>();
        final List<Integer> picks2 = new ArrayList<>();
        RpgClass rpgClass;
        Subtype subtype;
    }

    private static final Map<UUID, Progress> PROGRESS = new ConcurrentHashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                onLogin(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                resend(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                resend(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && isInInquiry(sp)) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> PROGRESS.remove(e.getEntity().getUUID()));
    }

    /** The stage (1..3) the player is currently answering, or 0 if there is no inquiry in progress. */
    public static int currentStage(ServerPlayer player) {
        Progress p = PROGRESS.get(player.getUUID());
        return p == null ? 0 : p.stage;
    }

    public static OptionalInt currentStage(UUID id) {
        Progress p = PROGRESS.get(id);
        return p == null ? OptionalInt.empty() : OptionalInt.of(p.stage);
    }

    /** Players are invulnerable while an inquiry is in progress. */
    public static boolean isInInquiry(ServerPlayer player) {
        return PROGRESS.containsKey(player.getUUID());
    }

    /** Re-opens the current stage after the screen was lost (death, dimension change). */
    public static void resend(ServerPlayer player) {
        Progress p = PROGRESS.get(player.getUUID());
        if (p != null) {
            sendStage(player, p);
        }
    }

    public static void onLogin(ServerPlayer player) {
        if (RpgAttachments.get(player).inquiryDone()) {
            PROGRESS.remove(player.getUUID());
            return;
        }
        start(player);
    }

    private static void start(ServerPlayer player) {
        Progress p = new Progress();
        PROGRESS.put(player.getUUID(), p);
        advance(player, p);
    }

    /** Resets the player (knowledge kept) and opens the inquiry again. */
    public static void reroll(ServerPlayer player) {
        RpgAttachments.set(player, RpgAttachments.get(player).resetKeepKnowledge());
        dev.goddessrpg.skill.SkillCooldowns.clear(player);
        dev.goddessrpg.skill.SkillEffects.refreshPassives(player);
        start(player);
    }

    static void onAnswerPacket(ServerPlayer player, int stage, List<Integer> picks) {
        if (!submit(player, stage, picks)) {
            Progress p = PROGRESS.get(player.getUUID());
            if (p != null) {
                sendStage(player, p); // let a confused or forging client see the real current question
            }
        }
    }

    public static boolean submit(ServerPlayer player, int stage, List<Integer> picks) {
        if (RpgAttachments.get(player).inquiryDone()) {
            return false;
        }
        Progress p = PROGRESS.get(player.getUUID());
        if (p == null || p.stage != stage || picks == null) {
            return false;
        }
        try {
            apply(player, p, picks);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    private static List<Question> questionsFor(Progress p) {
        return switch (p.stage) {
            case 1 -> RpgDataLoader.stage1();
            case 2 -> RpgDataLoader.stage2(p.rpgClass);
            default -> RpgDataLoader.stage3(p.rpgClass);
        };
    }

    /** Scores one stage; throws IllegalArgumentException (state untouched) on invalid picks. */
    private static void apply(ServerPlayer player, Progress p, List<Integer> picks) {
        List<Question> questions = questionsFor(p);
        switch (p.stage) {
            case 1 -> {
                RpgClass c = InquiryScorer.pickClass(questions, picks, RpgDataLoader.subtypes());
                p.rpgClass = c;
                p.picks1.clear();
                p.picks1.addAll(picks);
                p.stage = 2;
                advance(player, p);
            }
            case 2 -> {
                Subtype s = InquiryScorer.pickSubtype(questions, picks, subtypesOf(p.rpgClass));
                p.subtype = s;
                p.picks2.clear();
                p.picks2.addAll(picks);
                p.stage = 3;
                advance(player, p);
            }
            default -> {
                StarResult stars = InquiryScorer.stars(questions, picks, p.rpgClass);
                List<Integer> all = new ArrayList<>(p.picks1);
                all.addAll(p.picks2);
                all.addAll(picks);
                RpgPlayerData data = RpgAttachments.get(player)
                        .withResult(p.rpgClass, p.subtype.id(), stars, all, RpgDataLoader.tuning());
                PROGRESS.remove(player.getUUID());
                RpgAttachments.set(player, data);
                dev.goddessrpg.skill.SkillService.grantMetProficiencySkills(player);
                giveTome(player);
                send(player, new QuestionsPayload(0, List.of()));
                tell(player, Component.translatable("goddessrpg.inquiry.outro"));
            }
        }
    }

    private static List<Subtype> subtypesOf(RpgClass c) {
        return RpgDataLoader.subtypes().stream().filter(s -> s.rpgClass() == c).toList();
    }

    /** Sends the current stage; a stage with no questions at all is skipped (empty picks). */
    private static void advance(ServerPlayer player, Progress p) {
        if (questionsFor(p).isEmpty()) {
            apply(player, p, List.of());
        } else {
            sendStage(player, p);
        }
    }

    private static void sendStage(ServerPlayer player, Progress p) {
        List<QuestionView> views = new ArrayList<>();
        for (Question q : questionsFor(p)) {
            views.add(new QuestionView(q.textKey(), q.answers().stream().map(a -> a.textKey()).toList()));
        }
        send(player, new QuestionsPayload(p.stage, views));
    }

    private static void giveTome(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.is(RpgItems.GODDESS_TOME.get())) {
                return;
            }
        }
        ItemStack tome = new ItemStack(RpgItems.GODDESS_TOME.get());
        if (!player.getInventory().add(tome)) {
            player.drop(tome, false);
        }
    }

    private static void send(ServerPlayer player, QuestionsPayload payload) {
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
