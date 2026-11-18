package dev.goddessrpg.tome.client.pages;

import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.tome.TomeRef;
import dev.goddessrpg.tome.client.ClientTomeData;
import dev.goddessrpg.tome.client.TomePage;
import dev.goddessrpg.tome.client.TomeScreen;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Page 3: total knowledge bonus (and its cap) on the left, the ancient tomes read with each one's bonus on the right. */
public class KnowledgePage implements TomePage {
    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f", v * 100);
    }

    @Override
    public void render(TomeScreen s, GuiGraphics g, int l, int t, int mx, int my) {
        RpgPlayerData d = RpgAttachments.get(Minecraft.getInstance().player);
        TomeRef ref = ClientTomeData.ref();
        int lx = l + TomeScreen.LEFT_X;
        int rx = l + TomeScreen.RIGHT_X;
        int w = TomeScreen.SIDE_W;
        int lh = s.font().lineHeight + 1;
        s.heading(g, Component.translatable("goddessrpg.tome.page.knowledge"), lx, t + TomeScreen.TEXT_TOP);
        int y = t + 30;
        y = s.paragraph(g, Component.translatable("goddessrpg.tome.knowledge.total", pct(d.knowledge())), lx, y, w, TomeScreen.INK);
        y = s.paragraph(g, Component.translatable("goddessrpg.tome.knowledge.cap", pct(ref.knowledgeCap())), lx, y, w, TomeScreen.INK_DIM);
        y += 6;
        y = s.paragraph(g, Component.translatable("goddessrpg.tome.knowledge.read_count", d.tomesRead().size()), lx, y, w, TomeScreen.INK);
        y += 6;
        s.paragraph(g, Component.translatable("goddessrpg.tome.knowledge.desc"), lx, y, w, TomeScreen.INK_DIM);

        int ry = t + TomeScreen.TEXT_TOP;
        s.heading(g, Component.translatable("goddessrpg.tome.knowledge.list"), rx, ry);
        ry += lh + 6;
        List<String> ids = d.tomesRead().stream().sorted().toList();
        if (ids.isEmpty()) {
            s.paragraph(g, Component.translatable("goddessrpg.tome.knowledge.none"), rx, ry, w, TomeScreen.INK_DIM);
            return;
        }
        for (String id : ids) {
            if (ry + lh > t + TomeScreen.TEXT_BOTTOM) {
                s.text(g, Component.literal("…"), rx, ry - 2, TomeScreen.INK_DIM);
                break;
            }
            Double k = ref.tomeKnowledge().get(id);
            Component gain = Component.literal(k == null ? "" : "+" + pct(k) + "%");
            int gw = s.font().width(gain);
            String title = s.font().plainSubstrByWidth(Component.translatable("goddessrpg.tome." + id).getString(), w - gw - 4);
            s.text(g, Component.literal(title), rx, ry, TomeScreen.INK);
            s.text(g, gain, rx + w - gw, ry, TomeScreen.INK_GOOD);
            ry += lh;
        }
    }
}
