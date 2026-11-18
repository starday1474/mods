package dev.goddessrpg.skill;

import com.mojang.serialization.Codec;
import dev.goddessrpg.GoddessRpg;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Skill cooldowns: skill id -> the server game time (overworld {@code getGameTime()}, shared by all dimensions and saved
 * with the world) at which the skill is ready again. A separate attachment from RpgPlayerData; serialized and copied on
 * death, so neither dying nor relogging resets a cooldown.
 */
public final class SkillCooldowns {
    private SkillCooldowns() {}

    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, GoddessRpg.MODID);

    public static final Supplier<AttachmentType<Map<String, Long>>> READY_AT = REGISTER.register("skill_cooldowns",
            () -> AttachmentType.<Map<String, Long>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(Map::copyOf, Map::copyOf))
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> syncIfServer(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> syncIfServer(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> syncIfServer(e.getEntity()));
    }

    private static void syncIfServer(net.minecraft.world.entity.player.Player p) {
        if (p instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    public static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static Map<String, Long> all(ServerPlayer player) {
        return player.getData(READY_AT);
    }

    public static boolean isReady(ServerPlayer player, String id) {
        return all(player).getOrDefault(id, Long.MIN_VALUE) <= now(player);
    }

    /** Starts the cooldown; expired entries are dropped so the map does not grow forever. */
    public static void start(ServerPlayer player, String id, double cooldownSec) {
        long now = now(player);
        Map<String, Long> next = new HashMap<>();
        all(player).forEach((k, v) -> {
            if (v > now) {
                next.put(k, v);
            }
        });
        if (cooldownSec > 0) {
            next.put(id, now + Math.round(cooldownSec * 20.0));
        }
        player.setData(READY_AT, Map.copyOf(next));
        sync(player);
    }

    public static void clear(ServerPlayer player) {
        player.setData(READY_AT, Map.of());
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        if (player.connection == null) {
            return; // never placed (tests)
        }
        PacketDistributor.sendToPlayer(player, new SkillPayloads.CooldownSyncPayload(all(player)));
    }
}
