package dev.goddessrpg.tome.client;

import net.minecraft.client.gui.GuiGraphics;

/** One spread (two sides) of the adventurer's tome. Coordinates passed in are screen coordinates. */
public interface TomePage {
    /** {@code left}/{@code top}: the book texture's top-left corner on screen. */
    void render(TomeScreen screen, GuiGraphics g, int left, int top, int mouseX, int mouseY);

    /** Called when the page is shown or hidden (close popups, reset scroll). */
    default void reset() {}

    default boolean mouseClicked(TomeScreen screen, int left, int top, double mouseX, double mouseY, int button) {
        return false;
    }
}
