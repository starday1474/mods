package dev.goddessrpg.inquiry.client;

import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.RpgClass;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Temporary stats view opened by the goddess's tome; replaced by the book UI in a later plan. */
public class StatsScreen extends Screen {
    public StatsScreen() {
        super(Component.translatable("goddessrpg.stats.title"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private static String stars(int n) {
        return "★".repeat(Math.max(0, n)) + "☆".repeat(Math.max(0, 5 - n));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int y = 14;
        g.drawCenteredString(font, getTitle(), cx, y, 0xFFD27A);
        y += font.lineHeight + 8;
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        RpgPlayerData d = RpgAttachments.get(minecraft.player);
        if (!d.inquiryDone() || d.rpgClass().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("goddessrpg.info.no_inquiry"), cx, y, 0xFFFFFF);
            return;
        }
        RpgClass c = d.rpgClass().get();
        Component cls = Component.translatable("goddessrpg.class." + c.id());
        Component sub = Component.translatable("goddessrpg.subtype." + d.subtype().orElse("balanced"));
        g.drawCenteredString(font, Component.translatable("goddessrpg.info.class", cls, sub), cx, y, 0xFFFFFF);
        y += font.lineHeight + 8;
        for (Field f : new Field[] {c.first(), c.second()}) {
            Component name = Component.translatable("goddessrpg.field." + f.name().toLowerCase(Locale.ROOT));
            g.drawCenteredString(font, Component.translatable("goddessrpg.stats.field", name, d.levelOf(f),
                    RpgDataLoader.tuning().maxLevel(), stars(d.talentOf(f)), stars(d.aptitudeOf(f))), cx, y, 0xFFFFFF);
            y += font.lineHeight + 4;
        }
        y += 4;
        for (Field f : Field.values()) {
            if (!c.owns(f)) {
                Component name = Component.translatable("goddessrpg.field." + f.name().toLowerCase(Locale.ROOT));
                g.drawCenteredString(font, Component.translatable("goddessrpg.stats.off_field", name, d.levelOf(f)),
                        cx, y, 0xB0B0B0);
                y += font.lineHeight + 2;
            }
        }
        y += 6;
        g.drawCenteredString(font, Component.translatable("goddessrpg.stats.knowledge",
                String.format(Locale.ROOT, "%.0f", d.knowledge() * 100), d.tomesRead().size()), cx, y, 0xA0E0FF);
    }
}
