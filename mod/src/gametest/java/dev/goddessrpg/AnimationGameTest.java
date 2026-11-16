package dev.goddessrpg;

import dev.goddessrpg.compat.epicfight.RpgAnimations;
import dev.goddessrpg.compat.epicfight.RpgWeaponTypes;
import dev.goddessrpg.item.RpgItems;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponTypeReloadListener;

@GameTestHolder(GoddessRpg.MODID)
public class AnimationGameTest {
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void balancedAnimationsRegistered(GameTestHelper h) {
        List<AnimationAccessor<?>> ours = List.of(RpgAnimations.BALANCED_AUTO1, RpgAnimations.BALANCED_AUTO2,
                RpgAnimations.BALANCED_AUTO3);
        for (int i = 0; i < ours.size(); i++) {
            AnimationAccessor<?> acc = ours.get(i);
            String key = "goddessrpg:biped/combat/balanced_auto" + (i + 1);
            if (acc == null || acc.get() == null) {
                h.fail("accessor not built: " + key);
                return;
            }
            AnimationAccessor<StaticAnimation> found = AnimationManager.byKey(key);
            if (found == null || found.get() == null || found.id() != acc.id()) {
                h.fail("AnimationManager has no " + key + " (got " + found + ")");
                return;
            }
            if (!acc.registryName().toString().equals(key)) {
                h.fail("registry name " + acc.registryName() + " != " + key);
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void trainingSwordUsesOurType(GameTestHelper h) {
        if (WeaponTypeReloadListener.get(RpgWeaponTypes.BALANCED_SWORD) == null) {
            h.fail("weapon type " + RpgWeaponTypes.BALANCED_SWORD + " not registered");
            return;
        }
        CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(new ItemStack(RpgItems.TRAINING_SWORD.get()));
        if (!(cap instanceof WeaponCapability weapon)) {
            h.fail("training_sword has no weapon capability: " + cap);
            return;
        }
        if (!weapon.hasMatchingTag(RpgWeaponTypes.BALANCED_SWORD)) {
            h.fail("training_sword capability is not " + RpgWeaponTypes.BALANCED_SWORD + ": tags=" + weapon.getTags());
            return;
        }
        if (weapon.getWeaponCategory() != CapabilityItem.WeaponCategories.SWORD) {
            h.fail("category " + weapon.getWeaponCategory());
            return;
        }
        List<AnimationAccessor<? extends AttackAnimation>> combo =
                weapon.getMovesetForStyle(CapabilityItem.Styles.ONE_HAND).getComboAttackAnimations();
        List<AnimationAccessor<?>> want = List.of(RpgAnimations.BALANCED_AUTO1, RpgAnimations.BALANCED_AUTO2,
                RpgAnimations.BALANCED_AUTO3, Animations.SWORD_DASH, Animations.SWORD_AIR_SLASH);
        if (combo.size() != want.size()) {
            h.fail("combo " + combo);
            return;
        }
        for (int i = 0; i < want.size(); i++) {
            if (combo.get(i).id() != want.get(i).id()) {
                h.fail("combo[" + i + "] = " + combo.get(i).registryName() + ", want " + want.get(i).registryName());
                return;
            }
        }
        h.succeed();
    }
}
