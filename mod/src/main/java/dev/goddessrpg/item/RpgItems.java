package dev.goddessrpg.item;

import dev.goddessrpg.GoddessRpg;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import java.util.function.Supplier;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RpgItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GoddessRpg.MODID);

    public static final DeferredItem<GoddessTomeItem> GODDESS_TOME =
            ITEMS.register("goddess_tome", () -> new GoddessTomeItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RerollItem> REROLL =
            ITEMS.register("goddess_reroll", () -> new RerollItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ReawakeningCrystalItem> REAWAKENING_CRYSTAL = ITEMS.register("reawakening_crystal",
            () -> new ReawakeningCrystalItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, GoddessRpg.MODID);
    public static final Supplier<DataComponentType<String>> TOME_ID = COMPONENTS.registerComponentType("tome_id",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final Supplier<DataComponentType<String>> SKILL_ID = COMPONENTS.registerComponentType("skill_id",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DeferredItem<SkillBookItem> SKILL_BOOK =
            ITEMS.register("skill_book", () -> new SkillBookItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<AncientTomeItem> ANCIENT_TOME =
            ITEMS.register("ancient_tome", () -> new AncientTomeItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<TrainingSwordItem> TRAINING_SWORD =
            ITEMS.register("training_sword", () -> new TrainingSwordItem(new Item.Properties()));

    private RpgItems() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.COMBAT) {
                e.accept(TRAINING_SWORD.get());
            }
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(REAWAKENING_CRYSTAL.get());
            }
            if (e.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
                e.accept(REROLL.get());
                for (var def : dev.goddessrpg.skill.SkillRegistry.all().values()) {
                    if (def.unlock() instanceof dev.goddessrpg.rules.SkillDef.Unlock.Book) {
                        ItemStack book = new ItemStack(SKILL_BOOK.get());
                        book.set(SKILL_ID.get(), def.id());
                        e.accept(book);
                    }
                }
                for (String id : new String[] {"first_steps", "old_war_record"}) {
                    ItemStack tome = new ItemStack(ANCIENT_TOME.get());
                    tome.set(TOME_ID.get(), id);
                    e.accept(tome);
                }
            }
        });
    }
}
