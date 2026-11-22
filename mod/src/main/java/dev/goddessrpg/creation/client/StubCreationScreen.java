package dev.goddessrpg.creation.client;

import dev.goddessrpg.creation.CreationPayloads.OpenCreationPayload;
import dev.goddessrpg.creation.CreationPayloads.SubmitCreationPayload;
import dev.goddessrpg.creation.CreationPayloads.SubtypeCard;
import dev.goddessrpg.rules.AllocationRules.AllocationTuning;
import dev.goddessrpg.rules.CreationChoice;
import dev.goddessrpg.rules.CreationChoice.Allocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Temporary stand-in for the character creation screen: one button per card, and picking an available card confirms
 * it with the budget spread as evenly as the tuning allows. The real two-step screen replaces it. ESC does not close it.
 */
public class StubCreationScreen extends Screen {
    private static final int BUTTON_W = 200;
    private static final int BUTTON_H = 20;
    private final OpenCreationPayload payload;

    public StubCreationScreen(OpenCreationPayload payload) {
        super(Component.translatable("goddessrpg.create.title"));
        this.payload = payload;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The budget split over four slots, each kept within [min, max]. */
    static Allocation even(AllocationTuning t) {
        int[] v = new int[4];
        int left = t.budget();
        for (int i = 0; i < 4; i++) {
            int share = Math.max(t.min(), Math.min(t.max(), left / (4 - i)));
            v[i] = share;
            left -= share;
        }
        return new Allocation(v[0], v[1], v[2], v[3]);
    }

    @Override
    protected void init() {
        int n = payload.cards().size();
        int gap = 2;
        int top = Math.max(30, (height - n * (BUTTON_H + gap)) / 2);
        int x = (width - BUTTON_W) / 2;
        for (int i = 0; i < n; i++) {
            SubtypeCard card = payload.cards().get(i);
            Component label = Component.translatable("goddessrpg.subtype." + card.subtype());
            if (!card.available()) {
                label = Component.translatable("goddessrpg.create.card_unavailable", label);
            }
            Button b = Button.builder(label, btn -> PacketDistributor.sendToServer(new SubmitCreationPayload(
                            new CreationChoice(card.subtype(), even(payload.tuning())))))
                    .bounds(x, top + i * (BUTTON_H + gap), BUTTON_W, BUTTON_H).build();
            b.active = card.available();
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }
}
