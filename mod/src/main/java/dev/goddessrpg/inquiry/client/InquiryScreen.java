package dev.goddessrpg.inquiry.client;

import dev.goddessrpg.inquiry.InquiryPayloads.AnswerPayload;
import dev.goddessrpg.inquiry.InquiryPayloads.QuestionView;
import dev.goddessrpg.inquiry.client.InquiryLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The goddess asks, the player answers. One stage per screen; the picks of a whole stage are sent at once.
 * ESC does not close it: the inquiry must be finished.
 */
public class InquiryScreen extends Screen {
    private final int stage;
    private final List<QuestionView> questions;
    private final List<Integer> picks = new ArrayList<>();
    /** -1 shows the goddess's greeting (stage 1 only) before the first question. */
    private int index;

    public InquiryScreen(int stage, List<QuestionView> questions) {
        super(Component.translatable("goddessrpg.goddess.name"));
        this.stage = stage;
        this.questions = List.copyOf(questions);
        this.index = stage == 1 ? -1 : 0;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component speech() {
        if (index < 0) {
            return Component.translatable("goddessrpg.inquiry.greeting");
        }
        return Component.translatable(questions.get(index).textKey());
    }

    private List<Component> answerLabels() {
        if (index < 0) {
            return List.of(Component.translatable("goddessrpg.inquiry.continue"));
        }
        return questions.get(index).answerKeys().stream().<Component>map(Component::translatable).toList();
    }

    @Override
    protected void init() {
        clearWidgets();
        List<Component> labels = answerLabels();
        List<Rect> rects = InquiryLayout.layout(width, height, labels.size());
        for (int i = 0; i < labels.size(); i++) {
            Rect r = rects.get(i);
            int choice = i;
            addRenderableWidget(new AnswerButton(r, labels.get(i), b -> choose(choice)));
        }
    }

    private void choose(int choice) {
        if (index < 0) {
            index = 0;
            rebuildWidgets();
            return;
        }
        picks.add(choice);
        if (index + 1 < questions.size()) {
            index++;
            rebuildWidgets();
        } else {
            PacketDistributor.sendToServer(new AnswerPayload(stage, List.copyOf(picks)));
            // Stay open; the server replaces this screen with the next stage or closes it.
            for (var w : children()) {
                if (w instanceof Button b) {
                    b.active = false;
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int textWidth = Math.min(InquiryLayout.layout(width, height, 1).get(0).w(), width - 20);
        int x = (width - textWidth) / 2;
        int y = 12;
        g.drawString(font, getTitle(), x, y, 0xFFD27A, true);
        y += font.lineHeight + 6;
        int maxY = InquiryLayout.buttonsTop(height) - font.lineHeight;
        for (FormattedCharSequence line : font.split(speech(), textWidth)) {
            if (y > maxY) {
                break;
            }
            g.drawString(font, line, x, y, 0xFFFFFF, true);
            y += font.lineHeight + 2;
        }
    }

    /** A button whose label wraps over several lines instead of being cut off. */
    private static final class AnswerButton extends Button {
        AnswerButton(Rect r, Component label, OnPress onPress) {
            super(r.x(), r.y(), r.w(), r.h(), label, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blitSprite(SPRITES.get(this.active, this.isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight());
            var font = net.minecraft.client.Minecraft.getInstance().font;
            List<FormattedCharSequence> lines = font.split(getMessage(), getWidth() - 8);
            int maxLines = Math.max(1, (getHeight() - 4) / font.lineHeight);
            int shown = Math.min(lines.size(), maxLines);
            int y = getY() + (getHeight() - shown * font.lineHeight) / 2;
            int color = this.active ? 0xFFFFFF : 0xA0A0A0;
            for (int i = 0; i < shown; i++) {
                g.drawCenteredString(font, lines.get(i), getX() + getWidth() / 2, y + i * font.lineHeight, color);
            }
        }
    }
}
