package dev.goddessrpg.skill.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.inquiry.client.InquiryClient;
import dev.goddessrpg.skill.SkillPayloads;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only key bindings: skill slots 1-4 on Z X C V, the goddess tome on J (category key.categories.goddessrpg).
 * Independent of Epic Fight's battle/vanilla mode (none of these overlap EF's defaults). Ignored while a screen is open.
 */
public final class SkillKeys {
    private SkillKeys() {}

    public static final String CATEGORY = "key.categories." + GoddessRpg.MODID;

    public static final List<KeyMapping> SLOTS = List.of(
            key("slot1", GLFW.GLFW_KEY_Z), key("slot2", GLFW.GLFW_KEY_X),
            key("slot3", GLFW.GLFW_KEY_C), key("slot4", GLFW.GLFW_KEY_V));
    public static final KeyMapping TOME = key("tome", GLFW.GLFW_KEY_J);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key." + GoddessRpg.MODID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    @EventBusSubscriber(modid = GoddessRpg.MODID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent e) {
            SLOTS.forEach(e::register);
            e.register(TOME);
        }
    }

    @EventBusSubscriber(modid = GoddessRpg.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            boolean usable = mc.player != null && mc.screen == null && mc.getConnection() != null;
            for (int i = 0; i < SLOTS.size(); i++) {
                while (SLOTS.get(i).consumeClick()) {
                    if (usable) {
                        PacketDistributor.sendToServer(new SkillPayloads.CastRequestPayload(i));
                    }
                }
            }
            while (TOME.consumeClick()) {
                if (usable) {
                    InquiryClient.openStats(); // Task 7 replaces this with the goddess tome screen
                }
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientSkillState.reset();
        }
    }
}
