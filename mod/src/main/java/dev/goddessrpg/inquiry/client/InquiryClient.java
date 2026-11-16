package dev.goddessrpg.inquiry.client;

import dev.goddessrpg.inquiry.InquiryPayloads.QuestionsPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points; referenced lazily from payload handlers and items. */
public final class InquiryClient {
    private InquiryClient() {}

    public static void open(QuestionsPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.questions().isEmpty()) {
            if (mc.screen instanceof InquiryScreen) {
                mc.setScreen(null);
            }
            return;
        }
        mc.setScreen(new InquiryScreen(payload.stage(), payload.questions()));
    }

    public static void openStats() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new StatsScreen());
        }
    }
}
