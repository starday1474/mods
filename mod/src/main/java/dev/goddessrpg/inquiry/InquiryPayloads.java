package dev.goddessrpg.inquiry;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.inquiry.client.InquiryClient;
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

public final class InquiryPayloads {
    private InquiryPayloads() {}

    private static final int MAX_QUESTIONS = 16;

    /** One question as the client shows it: translation keys only. */
    public record QuestionView(String textKey, List<String> answerKeys) {
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestionView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, QuestionView::textKey,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), QuestionView::answerKeys,
                QuestionView::new);
    }

    /** Server to client: open (or replace) the inquiry screen for a stage. An empty list closes it. */
    public record QuestionsPayload(int stage, List<QuestionView> questions) implements CustomPacketPayload {
        public static final Type<QuestionsPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "inquiry_questions"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestionsPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, QuestionsPayload::stage,
                QuestionView.CODEC.apply(ByteBufCodecs.list(MAX_QUESTIONS)), QuestionsPayload::questions,
                QuestionsPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the picks for one whole stage. The server re-validates everything. */
    public record AnswerPayload(int stage, List<Integer> picks) implements CustomPacketPayload {
        public static final Type<AnswerPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "inquiry_answer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AnswerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AnswerPayload::stage,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_QUESTIONS)), AnswerPayload::picks,
                AnswerPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(InquiryPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(QuestionsPayload.TYPE, QuestionsPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> InquiryClient.open(payload)));
        registrar.playToServer(AnswerPayload.TYPE, AnswerPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                InquiryService.onAnswerPacket(sp, payload.stage(), payload.picks());
            }
        }));
    }
}
