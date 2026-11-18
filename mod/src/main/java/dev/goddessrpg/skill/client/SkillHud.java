package dev.goddessrpg.skill.client;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.client.ClientSync;
import dev.goddessrpg.mana.ManaAttachment;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Client-only HUD: four skill slots right of the hotbar (key letter, placeholder icon = first letter of the skill name,
 * cooldown overlay) and the mana bar above them. Positions come from {@link HudLayout}.
 */
@EventBusSubscriber(modid = GoddessRpg.MODID, value = Dist.CLIENT)
public final class SkillHud {
    private SkillHud() {}

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "skill_hud"),
                SkillHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }
        RpgPlayerData data = RpgAttachments.get(mc.player);
        if (!data.inquiryDone()) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        Font font = mc.font;
        List<HudLayout.Rect> slots = HudLayout.slots(w, h);
        for (int i = 0; i < slots.size(); i++) {
            HudLayout.Rect r = slots.get(i);
            g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), 0x90000000);
            g.renderOutline(r.x(), r.y(), r.w(), r.h(), 0xFF8A7A50);
            Optional<String> id = data.slots().get(i);
            if (id.isPresent()) {
                float frac = ClientSkillState.remainingFraction(id.get());
                if (frac <= 0) {
                    String name = I18n.get("goddessrpg.skill." + id.get());
                    String icon = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1));
                    g.drawCenteredString(font, icon, r.x() + r.w() / 2, r.y() + (r.h() - 8) / 2, 0xFFFFFFFF);
                } else {
                    int top = r.y() + r.h() - Math.round(r.h() * frac);
                    g.fill(r.x(), top, r.x() + r.w(), r.y() + r.h(), 0xA0101010);
                    long secs = (ClientSkillState.remainingTicks(id.get()) + 19) / 20;
                    g.drawCenteredString(font, Long.toString(secs), r.x() + r.w() / 2, r.y() + (r.h() - 8) / 2, 0xFFFF6060);
                }
            }
            String key = SkillKeys.SLOTS.get(i).getTranslatedKeyMessage().getString();
            if (!key.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(r.x() + 1, r.y() + 1, 0);
                g.pose().scale(0.5F, 0.5F, 1F);
                g.drawString(font, key.substring(0, key.offsetByCodePoints(0, 1)), 0, 0, 0xFFE0D090);
                g.pose().popPose();
            }
        }
        double max = ClientSync.manaMax();
        if (max > 0) {
            HudLayout.Rect bar = HudLayout.manaBar(w, h);
            double mana = mc.player.getData(ManaAttachment.MANA);
            int filled = (int) Math.round(bar.w() * Math.max(0, Math.min(1, mana / max)));
            g.fill(bar.x(), bar.y(), bar.x() + bar.w(), bar.y() + bar.h(), 0xA0000000);
            g.fill(bar.x(), bar.y(), bar.x() + filled, bar.y() + bar.h(), 0xFF3A7BFF);
        }
    }
}
