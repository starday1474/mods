package dev.goddessrpg.client;

import dev.goddessrpg.GoddessRpg;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = GoddessRpg.MODID, value = Dist.CLIENT)
public final class ClientManaEvents {
    private ClientManaEvents() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientSync.resetMana();
    }
}
