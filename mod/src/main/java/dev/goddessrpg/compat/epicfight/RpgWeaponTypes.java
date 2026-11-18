package dev.goddessrpg.compat.epicfight;

import com.mojang.logging.LogUtils;
import dev.goddessrpg.GoddessRpg;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.ex_cap.data.Moveset;
import yesman.epicfight.api.ex_cap.managers.ExCapManager;
import yesman.epicfight.gameasset.ColliderPreset;
import yesman.epicfight.registry.deferred.MovesetRegister;
import yesman.epicfight.registry.deferred.holders.DeferredMoveset;
import yesman.epicfight.registry.entries.EpicFightMovesets;
import yesman.epicfight.registry.entries.EpicFightProviderConditionals;
import yesman.epicfight.registry.entries.EpicFightSounds;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets;

/**
 * Epic Fight weapon types ({@code "type"} in {@code data/<ns>/capabilities/weapons/<item>.json}), one per moveset file
 * read by {@link MovesetLoader} ({@code data/goddessrpg/movesets/<name>.json}).
 *
 * <p>Base {@code epicfight:sword} (the only base so far) is a copy of EF's {@code epicfight:sword} preset
 * ({@code EpicFightItemCapabilityPresets.SWORD}: category SWORD, blade hit sound, SWORD collider, one-hand moveset +
 * dual-wield moveset with the DUAL_SWORDS / DEFAULT_1H_WIELD_STYLE conditionals) whose one-hand moveset
 * {@code goddessrpg:<name>_1h} has EF's {@code sword_1h} as parent (guard, mount attack, innate skill) and replaces the
 * combo with the file's combo + dash + air attack. {@code goddessrpg:balanced_sword} comes from {@code balanced.json}
 * (Epic Fight Extra sword_auto1..3, sword_dash, sword_airslash).
 *
 * <p>Epic Fight 21.17.3.1 keeps combos in movesets (registry {@code EpicFightRegistries.MOVESETS}, filled with
 * {@link MovesetRegister}); the moveset supplier runs at registry time, after every mod's {@code AnimationRegistryEvent},
 * so animation ids are resolved there. The weapon type itself is added through the EF event hook
 * {@code EpicFightEventHooks.Registry.WEAPON_CAPABILITY_PRESET} ({@code WeaponCapabilityPresetRegistryEvent.getTypeEntry()}),
 * posted by {@code WeaponTypeReloadListener.registerDefaultWeaponTypes()} at common setup and on every reload.
 */
public final class RpgWeaponTypes {
    private RpgWeaponTypes() {}

    public static final ResourceLocation BALANCED_SWORD = ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "balanced_sword");
    private static final ResourceLocation EF_SWORD_TAG = ResourceLocation.fromNamespaceAndPath("epicfight", "sword");
    private static final Logger LOG = LogUtils.getLogger();

    public static final MovesetRegister MOVESETS = MovesetRegister.create(GoddessRpg.MODID);

    /** Weapon type -> its one-hand moveset, one per moveset file (read at class load). */
    private static final Map<ResourceLocation, DeferredMoveset> ONE_HAND = new LinkedHashMap<>();

    static {
        for (MovesetLoader.Config c : MovesetLoader.all()) {
            if (!EF_SWORD_TAG.equals(c.base())) {
                LOG.error("Moveset {}: unsupported base {} (only {}); weapon type {} not registered", c.name(), c.base(),
                        EF_SWORD_TAG, c.weaponType());
                continue;
            }
            if (ONE_HAND.containsKey(c.weaponType())) {
                LOG.error("Moveset {}: weapon type {} already defined by another moveset file", c.name(), c.weaponType());
                continue;
            }
            ONE_HAND.put(c.weaponType(), MOVESETS.registerMoveset(c.name() + "_1h", () -> Moveset.builder()
                    .parent(EpicFightMovesets.SWORD_1H)
                    .replaceComboAttacks(comboArray(MovesetLoader.resolve(c)))));
        }
        if (!ONE_HAND.containsKey(BALANCED_SWORD)) {
            LOG.error("No moveset file for {}; it uses Epic Fight's sword moveset", BALANCED_SWORD);
            ONE_HAND.put(BALANCED_SWORD, EpicFightMovesets.SWORD_1H);
        }
    }

    @SuppressWarnings("unchecked")
    private static AnimationAccessor<? extends AttackAnimation>[] comboArray(List<AnimationAccessor<? extends AttackAnimation>> list) {
        return list.toArray(new AnimationAccessor[0]);
    }

    public static void register(IEventBus modBus) {
        MOVESETS.register(modBus);
        EpicFightEventHooks.Registry.WEAPON_CAPABILITY_PRESET.registerEvent(event -> {
            // registerPreset() looks up the type's ex_cap data list, which only exists for registered acceptors
            // (EF does this itself for presets in its own registry); putIfAbsent, survives reloads.
            for (ResourceLocation type : ONE_HAND.keySet()) {
                ExCapManager.addAcceptor(type);
                event.getTypeEntry().put(type, item -> WeaponCapabilityPresets.registerPreset(sword(type), item));
            }
        });
    }

    /** {@code goddessrpg:balanced_sword}'s preset. */
    public static WeaponCapability.Builder balancedSword() {
        return sword(BALANCED_SWORD);
    }

    /** Same as EF's sword preset except the one-hand moveset; tagged with the type id so it can be recognised. */
    private static WeaponCapability.Builder sword(ResourceLocation type) {
        WeaponCapability.Builder b = WeaponCapability.builder().identifier(type);
        b.category(CapabilityItem.WeaponCategories.SWORD);
        b.hitSound(EpicFightSounds.BLADE_HIT);
        b.collider(ColliderPreset.SWORD);
        return b.setTierValues(0, 0, 0, 0)
                .addMoveset(CapabilityItem.Styles.ONE_HAND, ONE_HAND.get(type))
                .addMoveset(CapabilityItem.Styles.TWO_HAND, EpicFightMovesets.SWORD_DUAL)
                .addConditionals(EpicFightProviderConditionals.DUAL_SWORDS, EpicFightProviderConditionals.DEFAULT_1H_WIELD_STYLE)
                .addTag(EF_SWORD_TAG)
                .addTag(type);
    }
}
