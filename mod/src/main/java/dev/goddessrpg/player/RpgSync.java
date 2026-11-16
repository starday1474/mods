package dev.goddessrpg.player;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.client.ClientSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RpgSync {
    private RpgSync() {}

    public record SyncPayload(RpgPlayerData data) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC =
                RpgPlayerData.STREAM_CODEC.map(SyncPayload::new, SyncPayload::data);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RpgSync::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> sendIfServer(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> sendIfServer(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> sendIfServer(e.getEntity()));
    }

    private static void sendIfServer(net.minecraft.world.entity.player.Player p) {
        if (p instanceof ServerPlayer sp) {
            send(sp);
        }
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientSync.apply(payload.data())));
    }

    public static void send(ServerPlayer player) {
        if (player.connection == null) {
            return; // not placed in the world yet (or a test fake); login sync will cover it
        }
        PacketDistributor.sendToPlayer(player, new SyncPayload(RpgAttachments.get(player)));
    }
}
