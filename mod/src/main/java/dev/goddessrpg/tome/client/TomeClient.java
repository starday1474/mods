package dev.goddessrpg.tome.client;

import net.minecraft.client.Minecraft;

/** Client-only entry point; referenced lazily from the tome item and the J key. */
public final class TomeClient {
    private TomeClient() {}

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new TomeScreen());
        }
    }
}
