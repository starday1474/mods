package dev.goddessrpg.creation.client;

import dev.goddessrpg.creation.CreationPayloads.OpenCreationPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry point; referenced lazily from the payload handler lambda. */
public final class CreationClient {
    private CreationClient() {}

    public static void open(OpenCreationPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.closes()) {
            if (mc.screen instanceof CreationScreen) {
                mc.setScreen(null);
            }
            return;
        }
        if (mc.screen instanceof CreationScreen screen) {
            screen.update(payload); // keeps the pick, shows the refusal
            return;
        }
        mc.setScreen(new CreationScreen(payload));
    }
}
