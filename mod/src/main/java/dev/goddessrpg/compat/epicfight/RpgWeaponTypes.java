package dev.goddessrpg.compat.epicfight;

import dev.goddessrpg.GoddessRpg;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.ex_cap.data.Moveset;
import yesman.epicfight.api.ex_cap.managers.ExCapManager;
import yesman.epicfight.gameasset.Animations;
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
 * Epic Fight weapon types ({@code "type"} in {@code data/<ns>/capabilities/weapons/<item>.json}).
 *
 * <p>{@code goddessrpg:balanced_sword} (balanced vanguard, sword + shield) is a copy of EF's {@code epicfight:sword}
 * preset ({@code EpicFightItemCapabilityPresets.SWORD}: category SWORD, blade hit sound, SWORD collider, one-hand
 * moveset + dual-wield moveset with the DUAL_SWORDS / DEFAULT_1H_WIELD_STYLE conditionals) whose one-hand moveset
 * replaces the auto-attack combo with BALANCED_AUTO1..3 and keeps EF's sword dash and air slash (and, through the
 * {@code sword_1h} parent, its guard, mount attack and innate skill).
 *
 * <p>Epic Fight 21.17.3.1 keeps combos in movesets (registry {@code EpicFightRegistries.MOVESETS}, filled with
 * {@link MovesetRegister}); the weapon type itself is added through the EF event hook
 * {@code EpicFightEventHooks.Registry.WEAPON_CAPABILITY_PRESET} ({@code WeaponCapabilityPresetRegistryEvent.getTypeEntry()}),
 * posted by {@code WeaponTypeReloadListener.registerDefaultWeaponTypes()} at common setup and on every reload.
 */
public final class RpgWeaponTypes {
    private RpgWeaponTypes() {}

    public static final ResourceLocation BALANCED_SWORD = ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "balanced_sword");
    private static final ResourceLocation EF_SWORD_TAG = ResourceLocation.fromNamespaceAndPath("epicfight", "sword");

    public static final MovesetRegister MOVESETS = MovesetRegister.create(GoddessRpg.MODID);

    /** EF's one-hand sword moveset with our combo; the supplier runs at registry time, after the animations are built. */
    public static final DeferredMoveset BALANCED_1H = MOVESETS.registerMoveset("balanced_1h", () -> Moveset.builder()
            .parent(EpicFightMovesets.SWORD_1H)
            .replaceComboAttacks(RpgAnimations.BALANCED_AUTO1, RpgAnimations.BALANCED_AUTO2, RpgAnimations.BALANCED_AUTO3,
                    Animations.SWORD_DASH, Animations.SWORD_AIR_SLASH));

    public static void register(IEventBus modBus) {
        MOVESETS.register(modBus);
        EpicFightEventHooks.Registry.WEAPON_CAPABILITY_PRESET.registerEvent(event -> {
            // registerPreset() looks up the type's ex_cap data list, which only exists for registered acceptors
            // (EF does this itself for presets in its own registry); putIfAbsent, survives reloads.
            ExCapManager.addAcceptor(BALANCED_SWORD);
            event.getTypeEntry().put(BALANCED_SWORD, item -> WeaponCapabilityPresets.registerPreset(balancedSword(), item));
        });
    }

    /** Same as EF's sword preset except the one-hand moveset; tagged with our type id so it can be recognised. */
    public static WeaponCapability.Builder balancedSword() {
        WeaponCapability.Builder b = WeaponCapability.builder().identifier(BALANCED_SWORD);
        b.category(CapabilityItem.WeaponCategories.SWORD);
        b.hitSound(EpicFightSounds.BLADE_HIT);
        b.collider(ColliderPreset.SWORD);
        return b.setTierValues(0, 0, 0, 0)
                .addMoveset(CapabilityItem.Styles.ONE_HAND, BALANCED_1H)
                .addMoveset(CapabilityItem.Styles.TWO_HAND, EpicFightMovesets.SWORD_DUAL)
                .addConditionals(EpicFightProviderConditionals.DUAL_SWORDS, EpicFightProviderConditionals.DEFAULT_1H_WIELD_STYLE)
                .addTag(EF_SWORD_TAG)
                .addTag(BALANCED_SWORD);
    }
}
