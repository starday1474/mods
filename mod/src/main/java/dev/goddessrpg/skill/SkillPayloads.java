package dev.goddessrpg.skill;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.client.ClientSync;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payloads for the skill system: mana sync, cast requests and cooldown sync. */
public final class SkillPayloads {
    private SkillPayloads() {}

    public record ManaSync(double mana, double max) implements CustomPacketPayload {
        public static final Type<ManaSync> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "mana_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ManaSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, ManaSync::mana, ByteBufCodecs.DOUBLE, ManaSync::max, ManaSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the player pressed the key of slot {@code slot} (0..3). Everything is re-checked on the server. */
    public record CastRequestPayload(int slot) implements CustomPacketPayload {
        public static final Type<CastRequestPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "cast_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CastRequestPayload> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(CastRequestPayload::new, CastRequestPayload::slot);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> owner: skill id -> server game time when it is ready again. */
    public record CooldownSyncPayload(Map<String, Long> readyAtTick) implements CustomPacketPayload {
        public static final Type<CooldownSyncPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "cooldown_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> STREAM_CODEC =
                ByteBufCodecs.<RegistryFriendlyByteBuf, String, Long, Map<String, Long>>map(HashMap::new,
                                ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG)
                        .map(CooldownSyncPayload::new, p -> new HashMap<>(p.readyAtTick()));

        public CooldownSyncPayload {
            readyAtTick = Map.copyOf(readyAtTick);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(SkillPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ManaSync.TYPE, ManaSync.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientSync.applyMana(p.mana(), p.max())));
        registrar.playToClient(CooldownSyncPayload.TYPE, CooldownSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.goddessrpg.skill.client.ClientSkillState.applyCooldowns(p.readyAtTick())));
        registrar.playToServer(CastRequestPayload.TYPE, CastRequestPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        SkillService.onCastRequest(sp, p.slot());
                    }
                }));
    }
}
