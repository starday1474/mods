package dev.goddessrpg.mana;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.ManaRules;
import dev.goddessrpg.skill.SkillPayloads;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Mana pool. Not serialized and not copied on death: it is refilled to the max on login and respawn.
 * The max comes from {@link ManaRules#max} (0 before the inquiry, so a classless player holds no mana).
 */
public final class ManaAttachment {
    private ManaAttachment() {}

    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, GoddessRpg.MODID + ".mana");

    public static final Supplier<AttachmentType<Double>> MANA =
            REGISTER.register("mana", () -> AttachmentType.builder(() -> 0.0).build());

    /** Server-only bookkeeping for sync throttling. */
    private static final Supplier<AttachmentType<SyncState>> SYNC_STATE =
            REGISTER.register("mana_sync", () -> AttachmentType.builder(() -> new SyncState()).build());

    private static final double SYNC_DELTA = 0.5;
    private static final int SYNC_INTERVAL_TICKS = 20;

    private static final class SyncState {
        double lastSent = Double.NaN;
        double lastMax = Double.NaN;
        int ticksSince;
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                tick(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                refill(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                refill(sp);
            }
        });
    }

    public static double get(Player player) {
        return player.getData(MANA);
    }

    public static double max(Player player) {
        return ManaRules.max(RpgDataLoader.manaTuning(), RpgAttachments.get(player));
    }

    /** Spends mana if there is enough; otherwise returns false and changes nothing. */
    public static boolean tryConsume(ServerPlayer player, double amount) {
        double cur = get(player);
        if (cur < amount) {
            return false;
        }
        player.setData(MANA, cur - amount);
        sync(player);
        return true;
    }

    /** Sets mana to the current max and syncs. */
    public static void refill(ServerPlayer player) {
        player.setData(MANA, max(player));
        sync(player);
    }

    /** Per-tick regen; syncs when the value moved by 0.5+ or a second passed with a change. */
    public static void tick(ServerPlayer player) {
        RpgPlayerData data = RpgAttachments.get(player);
        ManaRules.ManaTuning t = RpgDataLoader.manaTuning();
        double max = ManaRules.max(t, data);
        double regen = data.rpgClass().map(c -> t.regenPerSec().getOrDefault(c, 0.0)).orElse(0.0) / 20.0;
        double cur = get(player);
        double next = Math.min(max, cur + regen);
        if (next != cur) {
            player.setData(MANA, next);
        }
        SyncState s = player.getData(SYNC_STATE);
        s.ticksSince++;
        boolean changed = next != s.lastSent || max != s.lastMax;
        if (changed && (Math.abs(next - s.lastSent) >= SYNC_DELTA || max != s.lastMax || Double.isNaN(s.lastSent)
                || s.ticksSince >= SYNC_INTERVAL_TICKS)) {
            sync(player);
        }
    }

    private static void sync(ServerPlayer player) {
        SyncState s = player.getData(SYNC_STATE);
        double mana = get(player);
        double max = max(player);
        s.lastSent = mana;
        s.lastMax = max;
        s.ticksSince = 0;
        if (player.connection == null) {
            return; // never placed (tests); login sync covers it
        }
        PacketDistributor.sendToPlayer(player, new SkillPayloads.ManaSync(mana, max));
    }
}
