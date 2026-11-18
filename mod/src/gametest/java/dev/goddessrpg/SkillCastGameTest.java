package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.inquiry.InquiryService;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.StarResult;
import dev.goddessrpg.skill.SkillCooldowns;
import dev.goddessrpg.skill.SkillEffects;
import dev.goddessrpg.skill.SkillService;
import dev.goddessrpg.skill.SkillService.CastResult;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

@GameTestHolder(GoddessRpg.MODID)
public class SkillCastGameTest {
    /**
     * Never placed (see PlayerDataGameTest). Spawn protection is cleared so hurt() reaches the damage events, and the
     * combat tracker is marked in combat: entering combat sends a packet, which a connectionless player cannot.
     */
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-cast-player"), ClientInformation.createDefault());
        ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, p, 0, "spawnInvulnerableTime");
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.damagesource.CombatTracker.class,
                p.getCombatTracker(), true, "inCombat");
        return p;
    }

    private static RpgPlayerData vanguard() {
        return RpgPlayerData.EMPTY.withResult(RpgClass.VANGUARD, "balanced", new StarResult(Map.of(), Map.of()),
                List.of(), GrowthTuning.DEFAULT);
    }

    /** Raw state: learned set and slot contents are written as-is (no withSlot validation), like stale or forged data. */
    private static RpgPlayerData with(RpgPlayerData d, Set<String> learned, String... slots) {
        List<Optional<String>> s = Arrays.stream(slots).map(x -> x == null ? Optional.<String>empty() : Optional.of(x)).toList();
        return new RpgPlayerData(d.inquiryDone(), d.rpgClass(), d.subtype(), d.talent(), d.aptitude(), d.level(), d.xp(),
                d.tomesRead(), d.knowledge(), d.inquiryAnswers(), learned, s);
    }

    private static ServerPlayerPatch patch(GameTestHelper h, ServerPlayer p) {
        ServerPlayerPatch pp = EpicFightCapabilities.getServerPlayerPatch(p);
        if (pp == null) {
            h.fail("no Epic Fight patch");
        }
        return pp;
    }

    private static ServerPlayer caster(GameTestHelper h, String skill) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, with(vanguard(), Set.of(skill), skill, null, null, null));
        ServerPlayerPatch pp = patch(h, p);
        pp.setStamina(pp.getMaxStamina());
        return p;
    }

    /** Full health, no hurt cooldown, then {@code amount} generic damage; returns the health lost. */
    private static float hit(ServerPlayer p, net.minecraft.world.damagesource.DamageSource src, float amount) {
        p.setHealth(p.getMaxHealth());
        p.invulnerableTime = 0;
        p.hurt(src, amount);
        return p.getMaxHealth() - p.getHealth();
    }

    /**
     * A hit caused by {@code attacker}. Typed magic (no knockback): knockback sends a hurt-direction packet, which a
     * connectionless player cannot.
     */
    private static net.minecraft.world.damagesource.DamageSource attack(GameTestHelper h, net.minecraft.world.entity.Entity attacker) {
        return new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MAGIC), attacker);
    }

    private static void expect(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void castRejectsUnlearnedOrForeign(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, with(vanguard(), Set.of(), "test_dealer_book", "test_cast_buff", "no_such_skill", null));
        ServerPlayerPatch pp = patch(h, p);
        pp.setStamina(pp.getMaxStamina());
        float stamina = pp.getStamina();
        expect(h, SkillService.cast(p, 0) == CastResult.WRONG_CLASS, "foreign: " + SkillService.cast(p, 0));
        expect(h, SkillService.cast(p, 1) == CastResult.NOT_LEARNED, "unlearned: " + SkillService.cast(p, 1));
        expect(h, SkillService.cast(p, 2) == CastResult.NOT_LEARNED, "unknown: " + SkillService.cast(p, 2));
        expect(h, SkillService.cast(p, 3) == CastResult.EMPTY_SLOT, "empty: " + SkillService.cast(p, 3));
        expect(h, SkillService.cast(p, 7) == CastResult.EMPTY_SLOT, "bad index");
        // the foreign skill learned by forged data is still the wrong class
        RpgAttachments.set(p, with(vanguard(), Set.of("test_dealer_book"), "test_dealer_book", null, null, null));
        expect(h, SkillService.cast(p, 0) == CastResult.WRONG_CLASS, "forged learned foreign");
        expect(h, pp.getStamina() == stamina, "stamina spent");
        expect(h, SkillCooldowns.all(p).isEmpty(), "cooldown set: " + SkillCooldowns.all(p));
        expect(h, p.getAttributeValue(SkillEffects.DAMAGE_TAKEN) == 1.0, "buff applied");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void castRejectedOnCooldownOrStamina(GameTestHelper h) {
        ServerPlayer p = caster(h, "test_cast_buff");
        ServerPlayerPatch pp = patch(h, p);
        float before = pp.getStamina();
        expect(h, before >= 2, "max stamina " + before);
        expect(h, SkillService.cast(p, 0) == CastResult.OK, "first cast");
        expect(h, pp.getStamina() < before, "stamina not spent");
        expect(h, SkillService.cast(p, 0) == CastResult.COOLDOWN, "second cast not on cooldown");
        long ready = SkillCooldowns.all(p).getOrDefault("test_cast_buff", 0L);
        expect(h, ready == h.getLevel().getServer().overworld().getGameTime() + 100, "readyAt " + ready);

        // cooldown survives death
        ServerPlayer fresh = fakePlayer(h);
        fresh.restoreFrom(p, false);
        patch(h, fresh).setStamina(patch(h, fresh).getMaxStamina());
        expect(h, SkillService.cast(fresh, 0) == CastResult.COOLDOWN, "cooldown lost on death");

        ServerPlayer q = caster(h, "test_cast_buff");
        patch(h, q).setStamina(0);
        expect(h, SkillService.cast(q, 0) == CastResult.NO_STAMINA, "no stamina: " + SkillService.cast(q, 0));
        expect(h, SkillCooldowns.all(q).isEmpty(), "rejected cast started cooldown");

        ServerPlayer m = caster(h, "test_cast_mana");
        expect(h, SkillService.cast(m, 0) == CastResult.NO_MANA, "no mana");

        // reroll clears cooldowns
        InquiryService.reroll(p);
        expect(h, SkillCooldowns.all(p).isEmpty(), "reroll kept cooldowns");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    @PrefixGameTestTemplate(false)
    public static void buffAppliesAndExpires(GameTestHelper h) {
        ServerPlayer p = caster(h, "test_cast_buff");
        double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        expect(h, SkillService.cast(p, 0) == CastResult.OK, "cast");
        float taken = hit(p, h.getLevel().damageSources().generic(), 10);
        expect(h, Math.abs(taken - 7) < 0.01, "buffed damage " + taken);
        expect(h, p.getAttributeValue(Attributes.MOVEMENT_SPEED) > speed, "speed not raised");
        h.runAfterDelay(81, () -> {
            float later = hit(p, h.getLevel().damageSources().generic(), 10);
            expect(h, Math.abs(later - 10) < 0.01, "damage after expiry " + later);
            expect(h, Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1e-9, "speed not restored");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    @PrefixGameTestTemplate(false)
    public static void counterWindowNegatesHit(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, 1, 2, 1);
        ServerPlayer p = caster(h, "test_cast_counter");
        expect(h, SkillService.cast(p, 0) == CastResult.OK, "cast");
        float taken = hit(p, attack(h, z), 6);
        expect(h, taken == 0, "countered hit dealt " + taken);
        float second = hit(p, attack(h, z), 6);
        expect(h, second > 0, "window must be used up by the first hit");

        ServerPlayer idle = caster(h, "test_cast_counter");
        expect(h, SkillService.cast(idle, 0) == CastResult.OK, "cast 2");
        h.runAfterDelay(45, () -> {
            float late = hit(idle, attack(h, z), 6);
            expect(h, late > 0, "hit after the window was negated");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    @PrefixGameTestTemplate(false)
    public static void strikeMultiplierRemovedByTime(GameTestHelper h) {
        ServerPlayer p = caster(h, "test_cast_strike");
        double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        expect(h, SkillService.cast(p, 0) == CastResult.OK, "cast");
        expect(h, Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base * 2.0) < 1e-6,
                "strike mult " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
        h.runAfterDelay(80, () -> {
            expect(h, Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 1e-6, "strike mult not removed");
            h.succeed();
        });
    }

    private static void meleeOrThrow(Zombie z, net.minecraft.world.damagesource.DamageSource src) {
        z.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        z.invulnerableTime = 0;
        z.hurt(src, 1.0F);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void knockbackStunOnlyDirectHitsOncePerTarget(GameTestHelper h) {
        Zombie melee = h.spawn(EntityType.ZOMBIE, 1, 2, 1);
        Zombie shot = h.spawn(EntityType.ZOMBIE, 3, 2, 3);
        ServerPlayer p = caster(h, "test_cast_strike");
        p.setPos(melee.getX() + 1.5, melee.getY(), melee.getZ()); // moveTo would reset the (absent) connection
        expect(h, SkillService.cast(p, 0) == CastResult.OK, "cast");
        var slow = net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN;

        meleeOrThrow(melee, h.getLevel().damageSources().playerAttack(p));
        var eff = melee.getEffect(slow);
        expect(h, eff != null && eff.getAmplifier() == 3, "direct hit not stunned: " + eff);
        double kb = melee.getDeltaMovement().horizontalDistance();
        expect(h, kb > 0.7, "direct hit not knocked back hard: " + kb);

        var snowball = new net.minecraft.world.entity.projectile.Snowball(h.getLevel(), p);
        meleeOrThrow(shot, h.getLevel().damageSources().thrown(snowball, p));
        expect(h, shot.getEffect(slow) == null, "projectile applied the stun");

        melee.removeEffect(slow);
        meleeOrThrow(melee, h.getLevel().damageSources().playerAttack(p));
        expect(h, melee.getEffect(slow) == null, "second hit re-applied the stun");
        expect(h, melee.getDeltaMovement().horizontalDistance() < 0.7,
                "second hit re-applied knockback: " + melee.getDeltaMovement().horizontalDistance());
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void passiveAppliesOnlyWithCondition(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, vanguard());
        double bare = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double armor = p.getAttributeValue(Attributes.ARMOR);
        expect(h, SkillService.learn(p, "test_cast_passive") == SkillService.LearnResult.OK, "learn");
        expect(h, p.getAttributeValue(Attributes.ATTACK_DAMAGE) == bare, "applied without a sword");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        SkillEffects.refreshPassives(p);
        SkillEffects.refreshPassives(p); // must not stack
        double withSword = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        expect(h, Math.abs(withSword - bare * 1.2) < 1e-6, "sword attack " + withSword + " bare " + bare);
        expect(h, Math.abs(p.getAttributeValue(Attributes.ARMOR) - (armor + 2)) < 1e-6, "armor");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        SkillEffects.refreshPassives(p);
        expect(h, p.getAttributeValue(Attributes.ATTACK_DAMAGE) == bare, "not removed bare-handed");
        expect(h, p.getAttributeValue(Attributes.ARMOR) == armor, "armor not removed");
        // reroll forgets the passive
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        SkillEffects.refreshPassives(p);
        InquiryService.reroll(p);
        expect(h, p.getAttributeValue(Attributes.ATTACK_DAMAGE) == bare, "passive kept after reroll");
        h.succeed();
    }
}
