package dev.goddessrpg.tome.client.pages;

import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.tome.client.TomePage;
import dev.goddessrpg.tome.client.TomeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Page 4: the player's class and subtype. A temporary page while the goddess inquiry is replaced by character
 * creation; the "my choice" page (weapon, subtype, star allocation) replaces it.
 */
public class GoddessPage implements TomePage {
    @Override
    public void render(TomeScreen s, GuiGraphics g, int l, int t, int mx, int my) {
        RpgPlayerData d = RpgAttachments.get(Minecraft.getInstance().player);
        int lx = l + TomeScreen.LEFT_X;
        int w = TomeScreen.SIDE_W;
        s.heading(g, Component.translatable("goddessrpg.tome.page.goddess"), lx, t + TomeScreen.TEXT_TOP);
        if (!d.inquiryDone() || d.rpgClass().isEmpty()) {
            s.paragraph(g, Component.translatable("goddessrpg.info.no_inquiry"), lx, t + 32, w, TomeScreen.INK);
            return;
        }
        String sub = d.subtype().orElse("balanced");
        s.paragraph(g, Component.translatable("goddessrpg.tome.class_line",
                Component.translatable("goddessrpg.class." + d.rpgClass().get().id()),
                Component.translatable("goddessrpg.subtype." + sub)), lx, t + 30, w, TomeScreen.INK_HEAD);
    }
}
