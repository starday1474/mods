package dev.goddessrpg.client;

import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import net.minecraft.client.Minecraft;

/** Client-only; referenced lazily from the sync payload handler. */
public final class ClientSync {
    private ClientSync() {}

    public static void apply(RpgPlayerData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.setData(RpgAttachments.DATA, data);
        }
    }

    private static volatile double manaMax;

    public static void applyMana(double mana, double max) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.setData(dev.goddessrpg.mana.ManaAttachment.MANA, mana);
        }
        manaMax = max;
    }

    public static double manaMax() {
        return manaMax;
    }
}
