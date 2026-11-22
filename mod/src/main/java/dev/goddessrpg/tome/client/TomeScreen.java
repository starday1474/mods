package dev.goddessrpg.tome.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.skill.client.SkillKeys;
import dev.goddessrpg.tome.client.pages.ChoicePage;
import dev.goddessrpg.tome.client.pages.KnowledgePage;
import dev.goddessrpg.tome.client.pages.MindMapPage;
import dev.goddessrpg.tome.client.pages.StatsPage;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The adventurer's tome: an open book of four spreads (stats, skill mind map, knowledge, my choice). Pages turn with
 * the arrow buttons, the left/right keys and the mouse wheel; the last page viewed is remembered while the game runs.
 */
public class TomeScreen extends Screen {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "textures/gui/tome.png");
    public static final int TEX_W = 256;
    public static final int TEX_H = 192;
    /** Writable area of each side, relative to the texture's top-left corner. */
    public static final int LEFT_X = 19;
    public static final int RIGHT_X = 137;
    public static final int SIDE_W = 100;
    public static final int TEXT_TOP = 16;
    public static final int TEXT_BOTTOM = 156;

    public static final int INK = 0xFF3B2A1A;
    public static final int INK_HEAD = 0xFF7A4A10;
    public static final int INK_DIM = 0xFF8A7A60;
    public static final int INK_GOOD = 0xFF2E6A8A;

    private static int lastPage = 0;

    private final List<TomePage> pages = List.of(new StatsPage(), new MindMapPage(), new KnowledgePage(), new ChoicePage());
    private int page = TomePaging.clamp(lastPage);
    private PageButton back;
    private PageButton forward;

    public TomeScreen() {
        super(Component.translatable("goddessrpg.stats.title"));
    }

    public int left() {
        return (width - TEX_W) / 2;
    }

    public int top() {
        return Math.max(0, (height - TEX_H) / 2);
    }

    public Font font() {
        return font;
    }

    @Override
    protected void init() {
        int l = left();
        int t = top();
        back = addRenderableWidget(new PageButton(l + 20, t + 159, false, b -> turn(TomePaging.prev(page)), true));
        forward = addRenderableWidget(new PageButton(l + TEX_W - 20 - 23, t + 159, true, b -> turn(TomePaging.next(page)), true));
        updateButtons();
    }

    private void turn(int to) {
        if (to != page) {
            pages.get(page).reset();
            page = to;
            lastPage = to;
            updateButtons();
        }
    }

    private void updateButtons() {
        if (back != null) {
            back.visible = page > 0;
            forward.visible = page < TomePaging.PAGE_COUNT - 1;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        g.blit(TEXTURE, left(), top(), 0F, 0F, TEX_W, TEX_H, TEX_W, TEX_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int l = left();
        int t = top();
        Component num = Component.translatable("goddessrpg.tome.page_number", page + 1, TomePaging.PAGE_COUNT);
        g.drawString(font, num, l + RIGHT_X + (SIDE_W - font.width(num)) / 2, t + 162, INK_DIM, false);
        if (minecraft != null && minecraft.player != null) {
            pages.get(page).render(this, g, l, t, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pages.get(page).mouseClicked(this, left(), top(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        turn(TomePaging.scroll(page, scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_LEFT) {
            turn(TomePaging.prev(page));
            return true;
        }
        if (keyCode == InputConstants.KEY_RIGHT) {
            turn(TomePaging.next(page));
            return true;
        }
        if (SkillKeys.TOME.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- drawing helpers shared by the pages ----

    public void text(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font, c, x, y, color, false);
    }

    /** Heading centred on a side ({@code sideX} = screen x of the side's left edge). */
    public void heading(GuiGraphics g, Component c, int sideX, int y) {
        g.drawString(font, c, sideX + (SIDE_W - font.width(c)) / 2, y, INK_HEAD, false);
    }

    public List<FormattedCharSequence> wrap(FormattedText c, int width) {
        return font.split(c, width);
    }

    /** Draws wrapped text and returns the y below it. */
    public int paragraph(GuiGraphics g, FormattedText c, int x, int y, int width, int color) {
        for (FormattedCharSequence line : wrap(c, width)) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
