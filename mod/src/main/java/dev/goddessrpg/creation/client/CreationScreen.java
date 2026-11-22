package dev.goddessrpg.creation.client;

import dev.goddessrpg.creation.CreationPayloads.OpenCreationPayload;
import dev.goddessrpg.creation.CreationPayloads.SubmitCreationPayload;
import dev.goddessrpg.creation.CreationPayloads.SubtypeCard;
import dev.goddessrpg.creation.client.CreationLayout.Rect;
import dev.goddessrpg.rules.CreationChoice;
import dev.goddessrpg.rules.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Character creation, in two steps. ESC does not close it; only the server does (an empty card list).
 * <ol>
 *   <li>Weapon cards: icon, weapon name, subtype name and two description lines each. "Coming soon" cards are dimmed
 *       and cannot be picked; picking an available card moves on.</li>
 *   <li>Star allocation over the class's two fields (talent = start level, aptitude = growth speed): five stars and
 *       -/+ per slot, the stars left, and a numeric preview of what each slot's stars give. "Back" returns to the
 *       cards; "Confirm" is enabled only when every star is spent and sends {@link SubmitCreationPayload}.</li>
 * </ol>
 * A refused confirm comes back as a fresh open payload carrying the reason, which is shown while the pick is kept.
 */
public class CreationScreen extends Screen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int GOLD = 0xFFE0C060;
    private static final int DIM = 0xFFA0A0A0;
    private static final int GOOD = 0xFF70E070;
    private static final int ERROR = 0xFFFF6A6A;
    private static final int STAR_ON = 0xFFFFC832;
    private static final int STAR_OFF = 0xFF4A4A4A;
    private static final int PANEL = 0xD0181410;
    private static final int BORDER = 0xFF6A5A40;
    private static final int BORDER_HOVER = 0xFFFFD060;
    private static final int SHADE = 0xA0000000;
    /** Confirm stays disabled this long while waiting for the server's answer. */
    private static final int WAIT_TICKS = 40;

    private OpenCreationPayload payload;
    private SubtypeCard selected;
    private AllocationDraft draft;
    /** Subtype the draft was made for, so going back and picking the same card keeps the stars. */
    private String lastSubtype;
    private String error;
    private int waitTicks;

    private final List<Button> minus = new ArrayList<>();
    private final List<Button> plus = new ArrayList<>();
    private Button confirm;

    public CreationScreen(OpenCreationPayload payload) {
        super(Component.translatable("goddessrpg.create.title"));
        this.payload = payload;
        this.error = payload.error();
    }

    /** A new open payload while the screen is up (refused confirm, respawn): keep the pick if it is still valid. */
    public void update(OpenCreationPayload next) {
        this.payload = next;
        this.error = next.error();
        this.waitTicks = 0;
        if (draft != null && !draft.tuning().equals(next.tuning())) {
            draft = new AllocationDraft(next.tuning());
        }
        if (selected != null) {
            String id = selected.subtype();
            Optional<SubtypeCard> same = next.cards().stream()
                    .filter(c -> c.subtype().equals(id) && c.available()).findFirst();
            selected = same.orElse(null);
        }
        rebuildWidgets();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean allocating() {
        return selected != null && draft != null;
    }

    @Override
    protected void init() {
        minus.clear();
        plus.clear();
        confirm = null;
        if (!allocating()) {
            return;
        }
        List<Rect> slots = CreationLayout.allocation(width, height);
        for (int i = 0; i < AllocationDraft.SLOTS; i++) {
            int slot = i;
            minus.add(addRenderableWidget(button(Component.literal("-"), CreationLayout.minusButton(slots.get(i)),
                    b -> { draft.dec(slot); refresh(); })));
            plus.add(addRenderableWidget(button(Component.literal("+"), CreationLayout.plusButton(slots.get(i)),
                    b -> { draft.inc(slot); refresh(); })));
        }
        addRenderableWidget(button(Component.translatable("goddessrpg.create.back"),
                CreationLayout.backButton(width, height), b -> {
                    selected = null;
                    error = "";
                    rebuildWidgets();
                }));
        confirm = addRenderableWidget(button(Component.translatable("goddessrpg.create.confirm"),
                CreationLayout.confirmButton(width, height), b -> submit()));
        refresh();
    }

    private static Button button(Component label, Rect r, Button.OnPress onPress) {
        return Button.builder(label, onPress).bounds(r.x(), r.y(), r.w(), r.h()).build();
    }

    private void refresh() {
        if (!allocating()) {
            return;
        }
        for (int i = 0; i < AllocationDraft.SLOTS; i++) {
            minus.get(i).active = draft.canDec(i);
            plus.get(i).active = draft.canInc(i);
        }
        if (confirm != null) {
            confirm.active = draft.complete() && waitTicks == 0;
        }
    }

    private void submit() {
        if (!allocating() || !draft.complete() || waitTicks > 0) {
            return;
        }
        PacketDistributor.sendToServer(new SubmitCreationPayload(new CreationChoice(selected.subtype(), draft.toAllocation())));
        error = "";
        waitTicks = WAIT_TICKS;
        refresh();
    }

    @Override
    public void tick() {
        if (waitTicks > 0) {
            waitTicks--;
            if (waitTicks == 0) {
                refresh();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!allocating() && button == 0) {
            int i = CreationLayout.hit(CreationLayout.cards(width, height, payload.cards().size()), mx, my);
            if (i >= 0 && payload.cards().get(i).available()) {
                SubtypeCard card = payload.cards().get(i);
                if (draft == null || !card.subtype().equals(lastSubtype)) {
                    draft = new AllocationDraft(payload.tuning());
                }
                selected = card;
                lastSubtype = card.subtype();
                error = "";
                if (minecraft != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ---- drawing ----

    private static Component weaponName(SubtypeCard c) {
        return Component.translatable("goddessrpg.create.weapon." + c.subtype());
    }

    private static Component subtypeName(SubtypeCard c) {
        return Component.translatable("goddessrpg.subtype." + c.subtype());
    }

    private static Component fieldName(Field f) {
        return Component.translatable("goddessrpg.field." + f.name().toLowerCase(Locale.ROOT));
    }

    private static ItemStack icon(SubtypeCard c) {
        ResourceLocation id = ResourceLocation.tryParse(c.icon());
        return id == null ? ItemStack.EMPTY
                : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderBackground(g, mx, my, partialTick);
        if (allocating()) {
            for (Rect r : CreationLayout.allocation(width, height)) {
                panel(g, r, BORDER);
            }
        }
    }

    private static void panel(GuiGraphics g, Rect r, int border) {
        g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), PANEL);
        g.renderOutline(r.x(), r.y(), r.w(), r.h(), border);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        g.drawCenteredString(font, title, width / 2, 6, TEXT);
        if (allocating()) {
            renderAllocation(g);
        } else {
            renderCards(g, mx, my);
        }
    }

    private void renderCards(GuiGraphics g, int mx, int my) {
        Component sub = error.isEmpty() ? Component.translatable("goddessrpg.create.pick_weapon")
                : Component.translatable(error);
        g.drawCenteredString(font, sub, width / 2, 17, error.isEmpty() ? DIM : ERROR);
        List<SubtypeCard> cards = payload.cards();
        List<Rect> rects = CreationLayout.cards(width, height, cards.size());
        int hovered = CreationLayout.hit(rects, mx, my);
        for (int i = 0; i < cards.size(); i++) {
            renderCard(g, cards.get(i), rects.get(i), i == hovered);
        }
        if (hovered >= 0) {
            SubtypeCard c = cards.get(hovered);
            List<Component> tip = new ArrayList<>();
            tip.add(weaponName(c).copy().withColor(GOLD));
            tip.add(Component.translatable("goddessrpg.tome.class_line",
                    Component.translatable("goddessrpg.class." + c.rpgClass().id()), subtypeName(c)));
            tip.add(Component.translatable("goddessrpg.create.desc." + c.subtype() + ".1"));
            tip.add(Component.translatable("goddessrpg.create.desc." + c.subtype() + ".2"));
            if (!c.available()) {
                tip.add(Component.translatable("goddessrpg.create.coming_soon").withColor(DIM));
            }
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private void renderCard(GuiGraphics g, SubtypeCard c, Rect r, boolean hovered) {
        panel(g, r, hovered && c.available() ? BORDER_HOVER : BORDER);
        int x = r.x();
        int y = r.y();
        g.renderItem(icon(c), x + 4, y + 4);
        fitText(g, weaponName(c), x + 24, y + 4, r.w() - 28, TEXT);
        fitText(g, subtypeName(c), x + 24, y + 14, r.w() - 28, GOLD);
        int bottom = y + r.h() - (c.available() ? 4 : 14);
        Component desc = Component.translatable("goddessrpg.create.desc." + c.subtype() + ".1")
                .append(" ").append(Component.translatable("goddessrpg.create.desc." + c.subtype() + ".2"));
        paragraphFit(g, desc, x + 4, y + 26, r.w() - 8, bottom, DIM);
        if (!c.available()) {
            g.fill(x + 1, y + 1, x + r.w() - 1, y + r.h() - 1, SHADE);
            Component soon = Component.translatable("goddessrpg.create.coming_soon");
            g.drawString(font, soon, x + (r.w() - font.width(soon)) / 2, y + r.h() - 12, TEXT, false);
        }
    }

    /** One line, shrunk (down to 60%) when wider than {@code maxW}. */
    private void fitText(GuiGraphics g, Component c, int x, int y, int maxW, int color) {
        int w = font.width(c);
        if (w <= maxW || maxW <= 0) {
            g.drawString(font, c, x, y, color, false);
            return;
        }
        float s = Math.max(0.6F, (float) maxW / w);
        g.pose().pushPose();
        g.pose().translate(x, y + (font.lineHeight * (1 - s)) / 2, 0);
        g.pose().scale(s, s, 1);
        g.enableScissor(x, y - 1, x + maxW, y + font.lineHeight + 1);
        g.drawString(font, c, 0, 0, color, false);
        g.disableScissor();
        g.pose().popPose();
    }

    /** Wrapped text that must end above {@code bottom}: drawn at 75% when it does not fit, and clipped after that. */
    private void paragraphFit(GuiGraphics g, Component c, int x, int y, int w, int bottom, int color) {
        float s = 1.0F;
        List<FormattedCharSequence> lines = font.split(c, w);
        if (lines.size() * (font.lineHeight + 1) > bottom - y) {
            s = 0.75F;
            lines = font.split(c, (int) (w / s));
        }
        g.enableScissor(x, y, x + w, bottom);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1);
        int ly = 0;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, 0, ly, color, false);
            ly += font.lineHeight + 1;
        }
        g.pose().popPose();
        g.disableScissor();
    }

    private void renderAllocation(GuiGraphics g) {
        g.drawCenteredString(font, Component.translatable("goddessrpg.tome.class_line", weaponName(selected),
                subtypeName(selected)), width / 2, 18, GOLD);
        int left = draft.remaining();
        g.drawCenteredString(font, Component.translatable("goddessrpg.create.remaining", left, draft.tuning().budget()),
                width / 2, 30, left == 0 ? GOOD : TEXT);
        List<Rect> slots = CreationLayout.allocation(width, height);
        Field[] fields = {selected.rpgClass().first(), selected.rpgClass().second()};
        for (int i = 0; i < AllocationDraft.SLOTS; i++) {
            Rect r = slots.get(i);
            boolean talent = AllocationDraft.isTalent(i);
            String kind = talent ? "talent" : "aptitude";
            fitText(g, Component.translatable("goddessrpg.create.slot." + kind, fieldName(fields[i / 2])),
                    r.x() + 4, CreationLayout.labelY(r), r.w() - 8, TEXT);
            fitText(g, Component.translatable("goddessrpg.create.hint." + kind), r.x() + 4, CreationLayout.hintY(r),
                    r.w() - 8, DIM);
            int n = draft.stars(i);
            for (int k = 0; k < draft.max() && k < 5; k++) {
                g.drawString(font, "★", CreationLayout.starX(r, k), CreationLayout.starY(r), k < n ? STAR_ON : STAR_OFF, false);
            }
            Component preview = talent
                    ? Component.translatable("goddessrpg.create.preview.talent",
                            AllocationDraft.startLevel(payload.talentStartLevel(), n))
                    : Component.translatable("goddessrpg.create.preview.aptitude",
                            AllocationDraft.formatMultiplier(AllocationDraft.multiplier(payload.aptitudeMultiplier(), n)));
            fitText(g, preview, r.x() + 4, CreationLayout.previewY(r), r.w() - 8, GOLD);
        }
        if (!error.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(error), width / 2, CreationLayout.errorY(width, height), ERROR);
        } else if (waitTicks > 0) {
            g.drawCenteredString(font, Component.translatable("goddessrpg.create.waiting"), width / 2,
                    CreationLayout.errorY(width, height), DIM);
        }
    }
}
