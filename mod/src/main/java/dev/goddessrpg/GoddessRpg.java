package dev.goddessrpg;

import dev.goddessrpg.command.RpgCommand;
import dev.goddessrpg.compat.epicfight.EfSkillPolicy;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.inquiry.InquiryPayloads;
import dev.goddessrpg.inquiry.InquiryService;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgSync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(GoddessRpg.MODID)
public class GoddessRpg {
    public static final String MODID = "goddessrpg";

    public GoddessRpg(IEventBus modBus) {
        RpgAttachments.REGISTER.register(modBus);
        RpgSync.register(modBus);
        InquiryPayloads.register(modBus);
        InquiryService.register();
        EfSkillPolicy.register();
        RpgItems.register(modBus);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new RpgDataLoader()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> RpgCommand.register(e.getDispatcher()));
    }
}
