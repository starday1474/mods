package dev.goddessrpg.tome.client.pages;

import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.CreationChoice;
import dev.goddessrpg.rules.CreationChoice.Allocation;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.tome.client.TomePage;
import dev.goddessrpg.tome.client.TomeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Page 4, "my choice": what was picked at character creation (weapon, subtype, the four star slots). Characters made
 * before 0.3 (goddess inquiry) have no record, which the page says.
 */
public class ChoicePage implements TomePage {
    @Override
    public void render(TomeScreen s, GuiGraphics g, int l, int t, int mx, int my) {
        RpgPlayerData d = RpgAttachments.get(Minecraft.getInstance().player);
        int lx = l + TomeScreen.LEFT_X;
        int rx = l + TomeScreen.RIGHT_X;
        int w = TomeScreen.SIDE_W;
        int lh = s.font().lineHeight + 1;
        s.heading(g, Component.translatable("goddessrpg.tome.page.choice"), lx, t + TomeScreen.TEXT_TOP);
        if (!d.inquiryDone() || d.rpgClass().isEmpty()) {
            s.paragraph(g, Component.translatable("goddessrpg.info.no_inquiry"), lx, t + 32, w, TomeScreen.INK);
            return;
        }
        RpgClass c = d.rpgClass().get();
        if (d.creation().isEmpty()) {
            int y = s.paragraph(g, Component.translatable("goddessrpg.tome.class_line",
                    Component.translatable("goddessrpg.class." + c.id()),
                    Component.translatable("goddessrpg.subtype." + d.subtype().orElse("balanced"))),
                    lx, t + 30, w, TomeScreen.INK_HEAD);
            s.paragraph(g, Component.translatable("goddessrpg.tome.choice.none"), lx, y + 6, w, TomeScreen.INK_DIM);
            return;
        }
        CreationChoice choice = d.creation().get();
        int y = t + 30;
        s.text(g, Component.translatable("goddessrpg.tome.choice.weapon"), lx, y, TomeScreen.INK_DIM);
        y += lh;
        y = s.paragraph(g, Component.translatable("goddessrpg.create.weapon." + choice.subtype()), lx + 4, y, w - 4,
                TomeScreen.INK_HEAD);
        y += 4;
        s.text(g, Component.translatable("goddessrpg.tome.choice.subtype"), lx, y, TomeScreen.INK_DIM);
        y += lh;
        y = s.paragraph(g, Component.translatable("goddessrpg.tome.class_line",
                Component.translatable("goddessrpg.class." + c.id()),
                Component.translatable("goddessrpg.subtype." + choice.subtype())), lx + 4, y, w - 4, TomeScreen.INK);
        y += 4;
        s.paragraph(g, Component.translatable("goddessrpg.create.desc." + choice.subtype() + ".1"), lx, y, w,
                TomeScreen.INK_DIM);

        Allocation a = choice.allocation();
        int[][] cells = {{a.talentFirst(), a.aptitudeFirst()}, {a.talentSecond(), a.aptitudeSecond()}};
        int ry = t + 30;
        s.heading(g, Component.translatable("goddessrpg.tome.choice.stars"), rx, t + TomeScreen.TEXT_TOP);
        Field[] fields = {c.first(), c.second()};
        for (int f = 0; f < 2; f++) {
            s.text(g, StatsPage.fieldName(fields[f]), rx, ry, TomeScreen.INK);
            ry += lh;
            s.text(g, Component.translatable("goddessrpg.tome.stats.talent", StatsPage.stars(cells[f][0])), rx + 4, ry,
                    TomeScreen.INK_GOOD);
            ry += lh;
            s.text(g, Component.translatable("goddessrpg.tome.stats.aptitude", StatsPage.stars(cells[f][1])), rx + 4, ry,
                    TomeScreen.INK_GOOD);
            ry += lh + 6;
        }
        s.paragraph(g, Component.translatable("goddessrpg.tome.choice.permanent"), rx, ry, w, TomeScreen.INK_DIM);
    }
}
