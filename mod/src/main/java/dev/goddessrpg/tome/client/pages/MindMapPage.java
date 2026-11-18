package dev.goddessrpg.tome.client.pages;

import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.skill.SkillPayloads.SlotSetPayload;
import dev.goddessrpg.tome.client.ClientTomeData;
import dev.goddessrpg.tome.client.MindMapLayout;
import dev.goddessrpg.tome.client.MindMapLayout.Kind;
import dev.goddessrpg.tome.client.MindMapLayout.Node;
import dev.goddessrpg.tome.client.MindMapLayout.State;
import dev.goddessrpg.tome.client.TomePage;
import dev.goddessrpg.tome.client.TomeScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Page 2: the skill mind map across both sides (class centre, two fields, skills; learned bright, not yet learned
 * dim, undiscovered "???"), hover hints, a slot picker (1-4) for learned actives, and the four current slots below.
 */
public class MindMapPage implements TomePage {
    /** Map area and slot row, relative to the book texture. */
    static final int MAP_X = 20;
    static final int MAP_Y = 26;
    static final int MAP_W = 216;
    static final int MAP_H = 112;
    static final int SLOT_Y = 141;
    static final int SLOT_W = 48;
    static final int SLOT_H = 16;
    static final int[] SLOT_X = {20, 76, 132, 188};
    private static final int PICK = 14;
    private static final int PICK_GAP = 3;
    private static final int POPUP_W = 4 * PICK + 3 * PICK_GAP + 8;
    private static final int POPUP_H = PICK + 18;

    private String popupSkill;
    private int popupX;
    private int popupY;

    @Override
    public void reset() {
        popupSkill = null;
    }

    private static List<Node> nodes(RpgPlayerData d) {
        return MindMapLayout.layout(ClientTomeData.skills(), d, MAP_W, MAP_H);
    }

    private static Component skillName(String id) {
        return Component.translatable("goddessrpg.skill." + id);
    }

    private static String firstChar(String s) {
        return s.isEmpty() ? "?" : s.substring(0, s.offsetByCodePoints(0, 1));
    }

    @Override
    public void render(TomeScreen s, GuiGraphics g, int l, int t, int mx, int my) {
        RpgPlayerData d = RpgAttachments.get(Minecraft.getInstance().player);
        s.heading(g, Component.translatable("goddessrpg.tome.page.skills"), l + TomeScreen.LEFT_X, t + TomeScreen.TEXT_TOP);
        if (!d.inquiryDone() || d.rpgClass().isEmpty()) {
            s.paragraph(g, Component.translatable("goddessrpg.info.no_inquiry"), l + TomeScreen.LEFT_X, t + 32,
                    TomeScreen.SIDE_W, TomeScreen.INK);
            return;
        }
        int ox = l + MAP_X;
        int oy = t + MAP_Y;
        List<Node> nodes = nodes(d);
        Map<String, Node> byId = nodes.stream().collect(Collectors.toMap(Node::id, Function.identity(), (a, b) -> a));

        // links: class -> fields, field -> its skills
        Node center = nodes.get(0);
        for (Node n : nodes) {
            if (n.kind() == Kind.FIELD) {
                line(g, ox + center.x(), oy + center.y(), ox + n.x(), oy + n.y(), 0xFF9A7A50);
            } else if (n.kind() == Kind.ACTIVE || n.kind() == Kind.PASSIVE) {
                Optional<SkillDef> def = ClientTomeData.skill(n.id());
                Node parent = def.map(x -> byId.get(MindMapLayout.fieldId(x.field()))).orElse(center);
                line(g, ox + parent.x(), oy + parent.y(), ox + n.x(), oy + n.y(),
                        n.state() == State.LEARNED ? 0xFFB08A3A : 0xFFC8B898);
            }
        }
        for (Node n : nodes) {
            drawNode(s, g, d, ox + n.x(), oy + n.y(), n);
        }
        drawSlots(s, g, d, l, t);

        if (popupSkill != null) {
            drawPopup(s, g, mx, my);
            return;
        }
        Optional<Node> hover = MindMapLayout.hit(nodes, mx - ox, my - oy);
        if (hover.isPresent()) {
            g.renderComponentTooltip(s.font(), tooltip(hover.get(), d), mx, my);
            return;
        }
        for (int i = 0; i < SLOT_X.length; i++) {
            if (TomeScreen.inside(mx, my, l + SLOT_X[i], t + SLOT_Y, SLOT_W, SLOT_H)) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("goddessrpg.tome.slot_n", i + 1));
                d.slots().get(i).ifPresentOrElse(id -> {
                    tip.add(skillName(id).copy().withStyle(ChatFormatting.YELLOW));
                    tip.add(Component.translatable("goddessrpg.tome.slot_clear").withStyle(ChatFormatting.GRAY));
                }, () -> tip.add(Component.translatable("goddessrpg.skill.empty").withStyle(ChatFormatting.GRAY)));
                g.renderComponentTooltip(s.font(), tip, mx, my);
            }
        }
    }

    private void drawNode(TomeScreen s, GuiGraphics g, RpgPlayerData d, int cx, int cy, Node n) {
        int half = MindMapLayout.NODE_SIZE / 2;
        int x0 = cx - half;
        int y0 = cy - half;
        int size = MindMapLayout.NODE_SIZE;
        int fill;
        int border;
        int ink;
        String label;
        switch (n.kind()) {
            case CLASS -> {
                fill = 0xFFE3C15A;
                border = 0xFF6A4A10;
                ink = 0xFF3B2A1A;
                label = firstChar(I18n.get("goddessrpg.class." + n.id()));
            }
            case FIELD -> {
                fill = 0xFFD6B47A;
                border = 0xFF7A5A20;
                ink = 0xFF3B2A1A;
                label = firstChar(I18n.get("goddessrpg.field." + n.id()));
            }
            default -> {
                boolean passive = n.kind() == Kind.PASSIVE;
                switch (n.state()) {
                    case LEARNED -> {
                        fill = passive ? 0xFF4F8F5A : 0xFF3F6FB8;
                        ink = 0xFFFFFFFF;
                        label = firstChar(I18n.get("goddessrpg.skill." + n.id()));
                    }
                    case KNOWN_LOCKED -> {
                        fill = 0xFFCFC2A4;
                        ink = 0xFF8A7A60;
                        label = firstChar(I18n.get("goddessrpg.skill." + n.id()));
                    }
                    default -> {
                        fill = 0xFF5A4A3A;
                        ink = 0xFFD8C8A8;
                        label = "?";
                    }
                }
                border = passive ? 0xFF2F5A34 : 0xFF2A3A5A;
                if (n.state() != State.LEARNED) {
                    border = 0xFF8A7A60;
                }
            }
        }
        g.fill(x0, y0, x0 + size, y0 + size, fill);
        g.renderOutline(x0, y0, size, size, border);
        if (n.kind() == Kind.PASSIVE) {
            // passives: corner notches, so they read differently from actives without colour
            g.fill(x0 + 1, y0 + 1, x0 + 3, y0 + 3, border);
            g.fill(x0 + size - 3, y0 + size - 3, x0 + size - 1, y0 + size - 1, border);
        }
        g.drawString(s.font(), label, cx - s.font().width(label) / 2 + 1, cy - 4, ink, false);
        if (n.state() == State.LEARNED && (n.kind() == Kind.ACTIVE)) {
            int slot = d.slots().indexOf(Optional.of(n.id()));
            if (slot >= 0) {
                String num = Integer.toString(slot + 1);
                g.pose().pushPose();
                g.pose().translate(x0 + size - 4, y0 - 2, 0);
                g.pose().scale(0.5F, 0.5F, 1F);
                g.fill(-1, -1, 7, 9, 0xFFE3C15A);
                g.drawString(s.font(), num, 0, 0, 0xFF3B2A1A, false);
                g.pose().popPose();
            }
        }
    }

    private List<Component> tooltip(Node n, RpgPlayerData d) {
        List<Component> tip = new ArrayList<>();
        switch (n.kind()) {
            case CLASS -> {
                tip.add(Component.translatable("goddessrpg.class." + n.id()).withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("goddessrpg.subtype." + d.subtype().orElse("balanced")).withStyle(ChatFormatting.GRAY));
                return tip;
            }
            case FIELD -> {
                tip.add(Component.translatable("goddessrpg.field." + n.id()).withStyle(ChatFormatting.GOLD));
                d.rpgClass().ifPresent(c -> {
                    for (var f : new dev.goddessrpg.rules.Field[] {c.first(), c.second()}) {
                        if (MindMapLayout.fieldId(f).equals(n.id())) {
                            tip.add(Component.translatable("goddessrpg.tome.stats.level_short", d.levelOf(f))
                                    .withStyle(ChatFormatting.GRAY));
                        }
                    }
                });
                return tip;
            }
            default -> { }
        }
        Optional<SkillDef> def = ClientTomeData.skill(n.id());
        boolean hidden = n.state() == State.UNDISCOVERED;
        tip.add(hidden ? Component.translatable("goddessrpg.tome.unknown_skill").withStyle(ChatFormatting.DARK_GRAY)
                : skillName(n.id()).copy().withStyle(n.state() == State.LEARNED ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        if (!hidden) {
            tip.add(Component.translatable("goddessrpg.tome.kind." + (n.kind() == Kind.ACTIVE ? "active" : "passive"))
                    .append(" · ")
                    .append(Component.translatable("goddessrpg.tome.state." + n.state().name().toLowerCase(Locale.ROOT)))
                    .withStyle(ChatFormatting.DARK_GRAY));
            String descKey = "goddessrpg.skill." + n.id() + ".desc";
            if (I18n.exists(descKey)) {
                tip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
            }
            if (n.kind() == Kind.ACTIVE && def.isPresent()) {
                tip.add(costs(def.get()).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        if (n.state() != State.LEARNED) {
            def.map(MindMapPage::hint).ifPresent(h -> tip.add(h.withStyle(ChatFormatting.GOLD)));
        } else if (n.kind() == Kind.ACTIVE) {
            tip.add(Component.translatable("goddessrpg.tome.click_to_slot").withStyle(ChatFormatting.GREEN));
        }
        return tip;
    }

    private static MutableComponent costs(SkillDef s) {
        MutableComponent c = Component.translatable("goddessrpg.tome.cost.cooldown", fmt(s.cooldownSec()));
        if (s.staminaCost() > 0) {
            c.append(" · ").append(Component.translatable("goddessrpg.tome.cost.stamina", fmt(s.staminaCost())));
        }
        if (s.manaCost() > 0) {
            c.append(" · ").append(Component.translatable("goddessrpg.tome.cost.mana", fmt(s.manaCost())));
        }
        return c;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** How to get the skill: "<field> <level> 달성", "스킬북: 던전 상자", "보스 처치". */
    static MutableComponent hint(SkillDef s) {
        return switch (s.unlock()) {
            case SkillDef.Unlock.Proficiency p -> Component.translatable("goddessrpg.tome.hint.proficiency",
                    StatsPage.fieldName(p.field()), p.level());
            case SkillDef.Unlock.Book b -> Component.translatable("goddessrpg.tome.hint.book");
            case SkillDef.Unlock.Boss b -> Component.translatable("goddessrpg.tome.hint.boss");
        };
    }

    private void drawSlots(TomeScreen s, GuiGraphics g, RpgPlayerData d, int l, int t) {
        for (int i = 0; i < SLOT_X.length; i++) {
            int x = l + SLOT_X[i];
            int y = t + SLOT_Y;
            Optional<String> id = d.slots().get(i);
            g.fill(x, y, x + SLOT_W, y + SLOT_H, id.isPresent() ? 0x40305080 : 0x20000000);
            g.renderOutline(x, y, SLOT_W, SLOT_H, 0xFF8A6A30);
            g.drawString(s.font(), Integer.toString(i + 1), x + 3, y + 4, TomeScreen.INK_HEAD, false);
            String name = id.map(v -> I18n.get("goddessrpg.skill." + v)).orElse("-");
            name = s.font().plainSubstrByWidth(name, SLOT_W - 14);
            g.drawString(s.font(), name, x + 11, y + 4, id.isPresent() ? TomeScreen.INK : TomeScreen.INK_DIM, false);
        }
    }

    private void drawPopup(TomeScreen s, GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(popupX, popupY, popupX + POPUP_W, popupY + POPUP_H, 0xF0281C10);
        g.renderOutline(popupX, popupY, POPUP_W, POPUP_H, 0xFFE3C15A);
        Component title = Component.translatable("goddessrpg.tome.pick_slot");
        String tt = s.font().plainSubstrByWidth(title.getString(), POPUP_W - 6);
        g.drawString(s.font(), tt, popupX + 4, popupY + 3, 0xFFE3C15A, false);
        for (int i = 0; i < 4; i++) {
            int bx = pickX(i);
            int by = pickY();
            boolean hover = TomeScreen.inside(mx, my, bx, by, PICK, PICK);
            g.fill(bx, by, bx + PICK, by + PICK, hover ? 0xFF6A5A30 : 0xFF3A2E1E);
            g.renderOutline(bx, by, PICK, PICK, 0xFFB08A3A);
            String num = Integer.toString(i + 1);
            g.drawString(s.font(), num, bx + (PICK - s.font().width(num)) / 2 + 1, by + 3, 0xFFFFFFFF, false);
        }
        g.pose().popPose();
    }

    private int pickX(int i) {
        return popupX + 4 + i * (PICK + PICK_GAP);
    }

    private int pickY() {
        return popupY + 14;
    }

    @Override
    public boolean mouseClicked(TomeScreen s, int l, int t, double mx, double my, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (popupSkill != null) {
            for (int i = 0; i < 4; i++) {
                if (TomeScreen.inside(mx, my, pickX(i), pickY(), PICK, PICK)) {
                    PacketDistributor.sendToServer(new SlotSetPayload(i, Optional.of(popupSkill)));
                    break;
                }
            }
            popupSkill = null;
            return true;
        }
        RpgPlayerData d = RpgAttachments.get(mc.player);
        for (int i = 0; i < SLOT_X.length; i++) {
            if (button == 1 && TomeScreen.inside(mx, my, l + SLOT_X[i], t + SLOT_Y, SLOT_W, SLOT_H)
                    && d.slots().get(i).isPresent()) {
                PacketDistributor.sendToServer(new SlotSetPayload(i, Optional.empty()));
                return true;
            }
        }
        if (button != 0) {
            return false;
        }
        int ox = l + MAP_X;
        int oy = t + MAP_Y;
        Optional<Node> hit = MindMapLayout.hit(nodes(d), (int) mx - ox, (int) my - oy);
        if (hit.isPresent() && hit.get().kind() == Kind.ACTIVE && hit.get().state() == State.LEARNED) {
            Node n = hit.get();
            popupSkill = n.id();
            popupX = Math.max(2, Math.min(s.width - POPUP_W - 2, ox + n.x() + MindMapLayout.NODE_SIZE / 2 + 2));
            popupY = Math.max(2, Math.min(s.height - POPUP_H - 2, oy + n.y() - POPUP_H / 2));
            return true;
        }
        return false;
    }

    /** 1px line (Bresenham), drawn with fills. */
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        for (int guard = 0; guard < 4096; guard++) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
