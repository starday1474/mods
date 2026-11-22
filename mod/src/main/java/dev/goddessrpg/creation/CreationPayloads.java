package dev.goddessrpg.creation;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.creation.client.CreationClient;
import dev.goddessrpg.rules.AllocationRules.AllocationTuning;
import dev.goddessrpg.rules.CreationChoice;
import dev.goddessrpg.rules.RpgClass;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CreationPayloads {
    private CreationPayloads() {}

    private static final int MAX_CARDS = 32;

    private static final StreamCodec<ByteBuf, RpgClass> CLASS_CODEC =
            ByteBufCodecs.idMapper(i -> RpgClass.values()[i], RpgClass::ordinal);

    /** One weapon card as the client shows it. {@code available == false} is shown as "coming soon". */
    public record SubtypeCard(String subtype, RpgClass rpgClass, String weapon, String icon, boolean available) {
        public static final StreamCodec<ByteBuf, SubtypeCard> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SubtypeCard::subtype,
                CLASS_CODEC, SubtypeCard::rpgClass,
                ByteBufCodecs.STRING_UTF8, SubtypeCard::weapon,
                ByteBufCodecs.STRING_UTF8, SubtypeCard::icon,
                ByteBufCodecs.BOOL, SubtypeCard::available,
                SubtypeCard::new);
    }

    /**
     * Server to client: open (or replace) the character creation screen. Hidden subtypes are left out and the cards
     * come in display order. An empty card list closes the screen (creation finished).
     */
    public record OpenCreationPayload(List<SubtypeCard> cards, AllocationTuning tuning) implements CustomPacketPayload {
        public static final Type<OpenCreationPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "creation_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreationPayload> STREAM_CODEC = StreamCodec.composite(
                SubtypeCard.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CARDS)), OpenCreationPayload::cards,
                AllocationTuning.STREAM_CODEC, OpenCreationPayload::tuning,
                OpenCreationPayload::new);

        public OpenCreationPayload {
            cards = List.copyOf(cards);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the confirmed pick. The server re-validates everything. */
    public record SubmitCreationPayload(CreationChoice choice) implements CustomPacketPayload {
        public static final Type<SubmitCreationPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "creation_submit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SubmitCreationPayload> STREAM_CODEC =
                StreamCodec.composite(CreationChoice.STREAM_CODEC, SubmitCreationPayload::choice, SubmitCreationPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CreationPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(OpenCreationPayload.TYPE, OpenCreationPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> CreationClient.open(payload)));
        registrar.playToServer(SubmitCreationPayload.TYPE, SubmitCreationPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        CreationService.onSubmitPacket(sp, payload.choice());
                    }
                }));
    }
}
