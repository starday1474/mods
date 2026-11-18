package dev.goddessrpg.tome.client.pages;

import dev.goddessrpg.client.ClientSync;
import dev.goddessrpg.mana.ManaAttachment;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.tome.client.ClientTomeData;
import dev.goddessrpg.tome.client.TomePage;
import dev.goddessrpg.tome.client.TomeScreen;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Page 1: class and subtype, class fields (level, talent and aptitude stars), mana; other fields and knowledge. */
public class StatsPage implements TomePage {
    static String stars(int n) {
        return "★".repeat(Math.max(0, Math.min(5, n))) + "☆".repeat(Math.max(0, 5 - Math.max(0, n)));
    }

    static Component fieldName(Field f) {
        return Component.translatable("goddessrpg.field." + f.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void render(TomeScreen s, GuiGraphics g, int l, int t, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        RpgPlayerData d = RpgAttachments.get(mc.player);
        int lx = l + TomeScreen.LEFT_X;
        int rx = l + TomeScreen.RIGHT_X;
        int w = TomeScreen.SIDE_W;
        s.heading(g, Component.translatable("goddessrpg.tome.page.stats"), lx, t + TomeScreen.TEXT_TOP);
        if (!d.inquiryDone() || d.rpgClass().isEmpty()) {
            s.paragraph(g, Component.translatable("goddessrpg.info.no_inquiry"), lx, t + 32, w, TomeScreen.INK);
            return;
        }
        RpgClass c = d.rpgClass().get();
        int lh = s.font().lineHeight + 1;
        int y = t + 30;
        y = s.paragraph(g, Component.translatable("goddessrpg.tome.class_line",
                Component.translatable("goddessrpg.class." + c.id()),
                Component.translatable("goddessrpg.subtype." + d.subtype().orElse("balanced"))), lx, y, w, TomeScreen.INK_HEAD);
        y += 4;
        int max = ClientTomeData.ref().maxLevel();
        for (Field f : new Field[] {c.first(), c.second()}) {
            s.text(g, fieldName(f), lx, y, TomeScreen.INK);
            Component lv = Component.translatable("goddessrpg.tome.stats.level", d.levelOf(f), max);
            s.text(g, lv, lx + w - s.font().width(lv), y, TomeScreen.INK);
            y += lh;
            s.text(g, Component.translatable("goddessrpg.tome.stats.talent", stars(d.talentOf(f))), lx + 4, y, TomeScreen.INK_GOOD);
            y += lh;
            s.text(g, Component.translatable("goddessrpg.tome.stats.aptitude", stars(d.aptitudeOf(f))), lx + 4, y, TomeScreen.INK_GOOD);
            y += lh + 4;
        }
        double manaMax = ClientSync.manaMax();
        double mana = mc.player.getData(ManaAttachment.MANA);
        s.text(g, Component.translatable("goddessrpg.tome.stats.mana",
                String.format(Locale.ROOT, "%.0f", mana), String.format(Locale.ROOT, "%.0f", manaMax)), lx, y, TomeScreen.INK);
        y += lh;
        g.fill(lx, y, lx + w, y + 5, 0xFF6A5A48);
        int filled = manaMax <= 0 ? 0 : (int) Math.round((w - 2) * Math.max(0, Math.min(1, mana / manaMax)));
        g.fill(lx + 1, y + 1, lx + 1 + filled, y + 4, 0xFF3A7AD8);

        int ry = t + TomeScreen.TEXT_TOP;
        s.heading(g, Component.translatable("goddessrpg.tome.stats.other_fields"), rx, ry);
        ry += lh + 6;
        for (Field f : Field.values()) {
            if (!c.owns(f)) {
                s.text(g, fieldName(f), rx, ry, TomeScreen.INK_DIM);
                Component lv = Component.translatable("goddessrpg.tome.stats.level_short", d.levelOf(f));
                s.text(g, lv, rx + w - s.font().width(lv), ry, TomeScreen.INK_DIM);
                ry += lh;
            }
        }
        ry += 8;
        s.heading(g, Component.translatable("goddessrpg.tome.page.knowledge"), rx, ry);
        ry += lh + 4;
        s.paragraph(g, Component.translatable("goddessrpg.stats.knowledge",
                String.format(Locale.ROOT, "%.0f", d.knowledge() * 100), d.tomesRead().size()), rx, ry, w, TomeScreen.INK);
    }
}
