package dev.goddessrpg.item;

import dev.goddessrpg.GoddessRpg;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RpgItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GoddessRpg.MODID);

    public static final DeferredItem<GoddessTomeItem> GODDESS_TOME =
            ITEMS.register("goddess_tome", () -> new GoddessTomeItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RerollItem> REROLL =
            ITEMS.register("goddess_reroll", () -> new RerollItem(new Item.Properties().stacksTo(1)));

    private RpgItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
                e.accept(REROLL.get());
            }
        });
    }
}
