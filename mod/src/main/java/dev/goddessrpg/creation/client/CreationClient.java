package dev.goddessrpg.creation.client;

import dev.goddessrpg.creation.CreationPayloads.OpenCreationPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry point; referenced lazily from the payload handler. */
public final class CreationClient {
    private CreationClient() {}

    public static void open(OpenCreationPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.cards().isEmpty()) {
            if (mc.screen instanceof StubCreationScreen) {
                mc.setScreen(null);
            }
            return;
        }
        mc.setScreen(new StubCreationScreen(payload));
    }
}
