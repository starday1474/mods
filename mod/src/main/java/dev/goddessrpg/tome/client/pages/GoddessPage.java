package dev.goddessrpg.tome.client.pages;

import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.tome.client.ClientTomeData;
import dev.goddessrpg.tome.client.TomePage;
import dev.goddessrpg.tome.client.TomePaging;
import dev.goddessrpg.tome.client.TomeScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Page 4: the goddess's evaluation of the player's subtype on the left; the answers the player chose in the inquiry
 * on the right, scrolled with small arrows when they do not fit (the mouse wheel turns pages).
 */
public class GoddessPage implements TomePage {
    private static final int LIST_TOP = 30;
    private static final int STEP = 3;
    private int scroll;

    @Override
    public void reset() {
        scroll = 0;
    }

    private List<FormattedCharSequence> answerLines(TomeScreen s, RpgPlayerData d) {
        List<FormattedCharSequence> out = new ArrayList<>();
        RpgClass c = d.rpgClass().orElse(null);
        if (c == null) {
            return out;
        }
        for (String key : ClientTomeData.ref().chosenAnswerKeys(c, d.inquiryAnswers())) {
            if (!out.isEmpty()) {
                out.add(FormattedCharSequence.EMPTY);
            }
            out.addAll(s.wrap(Component.translatable("goddessrpg.tome.goddess.answer", Component.translatable(key)),
                    TomeScreen.SIDE_W));
        }
        return out;
    }

    private int visible(TomeScreen s) {
        return (TomeScreen.TEXT_BOTTOM - 10 - LIST_TOP) / (s.font().lineHeight + 1); // keep the last row for the arrow
    }

    @Override
    public void render(TomeScreen s, GuiGraphics g, int l, int t, int mx, int my) {
        RpgPlayerData d = RpgAttachments.get(Minecraft.getInstance().player);
        int lx = l + TomeScreen.LEFT_X;
        int rx = l + TomeScreen.RIGHT_X;
        int w = TomeScreen.SIDE_W;
        int lh = s.font().lineHeight + 1;
        s.heading(g, Component.translatable("goddessrpg.tome.page.goddess"), lx, t + TomeScreen.TEXT_TOP);
        if (!d.inquiryDone() || d.rpgClass().isEmpty()) {
            s.paragraph(g, Component.translatable("goddessrpg.info.no_inquiry"), lx, t + 32, w, TomeScreen.INK);
            return;
        }
        String sub = d.subtype().orElse("balanced");
        int y = t + 30;
        y = s.paragraph(g, Component.translatable("goddessrpg.tome.class_line",
                Component.translatable("goddessrpg.class." + d.rpgClass().get().id()),
                Component.translatable("goddessrpg.subtype." + sub)), lx, y, w, TomeScreen.INK_HEAD);
        y += 6;
        s.paragraph(g, Component.translatable("goddessrpg.tome.goddess.eval." + sub), lx, y, w, TomeScreen.INK);

        s.heading(g, Component.translatable("goddessrpg.tome.goddess.answers"), rx, t + TomeScreen.TEXT_TOP);
        List<FormattedCharSequence> lines = answerLines(s, d);
        int vis = visible(s);
        scroll = TomePaging.clampScroll(scroll, lines.size(), vis);
        int ry = t + LIST_TOP;
        for (int i = scroll; i < lines.size() && i < scroll + vis; i++) {
            g.drawString(s.font(), lines.get(i), rx, ry, TomeScreen.INK, false);
            ry += lh;
        }
        if (scroll > 0) {
            s.text(g, Component.literal("▲"), upX(l), upY(t), TomeScreen.INK_HEAD);
        }
        if (scroll + vis < lines.size()) {
            s.text(g, Component.literal("▼"), upX(l), downY(t), TomeScreen.INK_HEAD);
        }
    }

    private static int upX(int l) {
        return l + TomeScreen.RIGHT_X + TomeScreen.SIDE_W - 6;
    }

    private static int upY(int t) {
        return t + TomeScreen.TEXT_TOP;
    }

    private static int downY(int t) {
        return t + TomeScreen.TEXT_BOTTOM - 8;
    }

    @Override
    public boolean mouseClicked(TomeScreen s, int l, int t, double mx, double my, int button) {
        if (TomeScreen.inside(mx, my, upX(l) - 2, upY(t) - 2, 10, 12)) {
            scroll = Math.max(0, scroll - STEP);
            return true;
        }
        if (TomeScreen.inside(mx, my, upX(l) - 2, downY(t) - 2, 10, 12)) {
            scroll += STEP; // clamped on the next render
            return true;
        }
        return false;
    }
}
