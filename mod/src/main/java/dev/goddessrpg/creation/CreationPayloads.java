package dev.goddessrpg.creation;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.creation.client.CreationClient;
import dev.goddessrpg.rules.AllocationRules.AllocationTuning;
import dev.goddessrpg.rules.CreationChoice;
import dev.goddessrpg.rules.RpgClass;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final StreamCodec<ByteBuf, Map<Integer, Integer>> START_LEVEL_CODEC =
            ByteBufCodecs.<ByteBuf, Integer, Integer, Map<Integer, Integer>>map(HashMap::new,
                    ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_INT, 16);
    private static final StreamCodec<ByteBuf, Map<Integer, Double>> MULTIPLIER_CODEC =
            ByteBufCodecs.<ByteBuf, Integer, Double, Map<Integer, Double>>map(HashMap::new,
                    ByteBufCodecs.VAR_INT, ByteBufCodecs.DOUBLE, 16);

    /**
     * Server to client: open (or refresh) the character creation screen. Hidden subtypes are left out and the cards
     * come in display order. An empty card list closes the screen (creation finished).
     *
     * <p>{@code talentStartLevel} and {@code aptitudeMultiplier} are the growth tables (star count to start level /
     * proficiency XP multiplier) so the allocation step can preview them; they travel here rather than in
     * {@code TomeRef} because the screen can open on login before the tome reference data arrives, and only this
     * screen needs them. {@code error} is the translation key of the reason the last confirm was refused (empty
     * when there is none); the screen keeps the player's pick and shows it.
     */
    public record OpenCreationPayload(List<SubtypeCard> cards, AllocationTuning tuning,
            Map<Integer, Integer> talentStartLevel, Map<Integer, Double> aptitudeMultiplier, String error)
            implements CustomPacketPayload {
        public static final Type<OpenCreationPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "creation_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreationPayload> STREAM_CODEC = StreamCodec.composite(
                SubtypeCard.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CARDS)), OpenCreationPayload::cards,
                AllocationTuning.STREAM_CODEC, OpenCreationPayload::tuning,
                START_LEVEL_CODEC, OpenCreationPayload::talentStartLevel,
                MULTIPLIER_CODEC, OpenCreationPayload::aptitudeMultiplier,
                ByteBufCodecs.STRING_UTF8, OpenCreationPayload::error,
                OpenCreationPayload::new);

        public OpenCreationPayload {
            cards = List.copyOf(cards);
            talentStartLevel = Map.copyOf(talentStartLevel);
            aptitudeMultiplier = Map.copyOf(aptitudeMultiplier);
            error = error == null ? "" : error;
        }

        /** The payload that closes the screen. */
        public static OpenCreationPayload close(AllocationTuning tuning) {
            return new OpenCreationPayload(List.of(), tuning, Map.of(), Map.of(), "");
        }

        public boolean closes() {
            return cards.isEmpty();
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
