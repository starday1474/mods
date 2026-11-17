package dev.goddessrpg.compat.epicfight;

import dev.goddessrpg.GoddessRpg;
import java.util.Set;
import net.neoforged.bus.api.IEventBus;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.AnimationManager.AnimationBuilder;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackAnimationProperty;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.ComboAttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;

/**
 * Our own Epic Fight animations. The keyframe sources live in {@code mod/anim/*.json} and are built by
 * {@code tools/anim/build_anims.py} into {@code assets/goddessrpg/animmodels/animations/biped/combat/}.
 * The phase times (antic, contact, recovery) below must match each source's {@code "phase"}
 * (tools/anim/tests/test_build_anims.py checks this).
 *
 * <p>Epic Fight 21.17.3.1 API: {@code AnimationManager.AnimationRegistryEvent} (mod bus, posted during mod
 * construction) {@code newBuilder(namespace, Consumer<AnimationBuilder>)}, {@code AnimationBuilder.nextAccessor},
 * {@code ComboAttackAnimation(convertTime, antic, contact, recovery, collider, joint, accessor, armature)} as in
 * EF's own SWORD_AUTO1..3 (null collider = the weapon's collider, BASIS_ATTACK_SPEED 1.6).
 */
public final class RpgAnimations {
    private RpgAnimations() {}

    public static AnimationAccessor<ComboAttackAnimation> BALANCED_AUTO1;
    public static AnimationAccessor<ComboAttackAnimation> BALANCED_AUTO2;
    public static AnimationAccessor<ComboAttackAnimation> BALANCED_AUTO3;

    public static void register(IEventBus modBus) {
        modBus.addListener((AnimationManager.AnimationRegistryEvent e) -> e.newBuilder(GoddessRpg.MODID, RpgAnimations::build));
    }

    public static void build(AnimationBuilder builder) {
        BALANCED_AUTO1 = builder.nextAccessor("biped/combat/balanced_auto1",
                acc -> combo(new ComboAttackAnimation(0.1F, 0.15F, 0.3F, 0.45F, null, toolR(), acc, Armatures.BIPED)));
        BALANCED_AUTO2 = builder.nextAccessor("biped/combat/balanced_auto2",
                acc -> combo(new ComboAttackAnimation(0.1F, 0.15F, 0.3F, 0.4F, null, toolR(), acc, Armatures.BIPED)));
        BALANCED_AUTO3 = builder.nextAccessor("biped/combat/balanced_auto3",
                acc -> combo(new ComboAttackAnimation(0.1F, 0.2F, 0.35F, 0.5F, null, toolR(), acc, Armatures.BIPED)));
        registerIndexed(builder);
    }

    /** Hand-registered above; their timings are checked against mod/anim by a Python test. */
    private static final Set<String> HAND_REGISTERED = Set.of("balanced_auto1", "balanced_auto2", "balanced_auto3");

    /**
     * Every other entry of {@link AnimIndex} (anim_index.json): attack -> ComboAttackAnimation(convertTime 0.1, antic, contact,
     * recovery, null collider = weapon's, toolR); loop -> StaticAnimation(convertTime 0.1, repeat=true);
     * action and cast -> ActionAnimation(convertTime 0.1; non-repeating, Root motion applies, fixed playback speed, no
     * weapon collider so it never deals melee damage; a cast's phase stays in the index only as event timing).
     */
    private static void registerIndexed(AnimationBuilder builder) {
        for (AnimIndex.Entry e : AnimIndex.entries()) {
            if (HAND_REGISTERED.contains(e.name())) {
                continue;
            }
            switch (e.kind()) {
                case ATTACK -> builder.<ComboAttackAnimation>nextAccessor(e.path(), acc -> combo(new ComboAttackAnimation(
                        0.1F, e.phase().antic(), e.phase().contact(), e.phase().recovery(), null, toolR(), acc, Armatures.BIPED)));
                case LOOP -> builder.<StaticAnimation>nextAccessor(e.path(), acc -> new StaticAnimation(0.1F, true, acc, Armatures.BIPED));
                case CAST, ACTION -> builder.<ActionAnimation>nextAccessor(e.path(), acc -> new ActionAnimation(0.1F, acc, Armatures.BIPED));
            }
        }
    }

    private static Joint toolR() {
        return ((HumanoidArmature) Armatures.BIPED.get()).toolR;
    }

    private static ComboAttackAnimation combo(ComboAttackAnimation anim) {
        anim.addProperty(AttackAnimationProperty.BASIS_ATTACK_SPEED, 1.6F);
        return anim;
    }
}
