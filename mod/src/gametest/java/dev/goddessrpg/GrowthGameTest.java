package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.growth.GrowthEvents;
import dev.goddessrpg.item.AncientTomeItem;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.StarResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GoddessRpg.MODID)
public class GrowthGameTest {
    /** Never placed in the PlayerList (see PlayerDataGameTest): no connection, so no sends. */
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-fake-player"), ClientInformation.createDefault());
    }

    private static RpgPlayerData vanguard(int swordAptitude) {
        StarResult stars = new StarResult(
                Map.of(Field.SWORD, 3, Field.DEFENSE, 3),
                Map.of(Field.SWORD, swordAptitude, Field.DEFENSE, 3));
        return RpgPlayerData.EMPTY.withResult(RpgClass.VANGUARD, "balanced", stars, List.of(), GrowthTuning.DEFAULT);
    }

    private static void check(GameTestHelper h, double expected, double actual, String what) {
        if (Math.abs(expected - actual) > 1e-6) {
            h.fail(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void swordHit(GameTestHelper h, int aptitude, double expected) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, vanguard(aptitude));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        LivingEntity zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        zombie.hurt(h.getLevel().damageSources().playerAttack(p), 5.0F);
        check(h, expected, RpgAttachments.get(p).xpOf(Field.SWORD), "sword xp");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void hitGivesSwordXp(GameTestHelper h) {
        swordHit(h, 3, 3.0);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aptitudeScalesXp(GameTestHelper h) {
        swordHit(h, 5, 4.5);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void offClassFieldSlow(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, vanguard(5));
        GrowthEvents.grant(p, Field.ARCHERY, 10);
        check(h, 3.0, RpgAttachments.get(p).xpOf(Field.ARCHERY), "archery xp");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void armorStandGivesNothing(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, vanguard(3));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        ArmorStand stand = h.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 2, 1));
        stand.hurt(h.getLevel().damageSources().playerAttack(p), 5.0F);
        check(h, 0.0, RpgAttachments.get(p).xpOf(Field.SWORD), "sword xp from armor stand");
        h.succeed();
    }

    private static ItemStack tome(String id) {
        ItemStack s = new ItemStack(RpgItems.ANCIENT_TOME.get());
        s.set(RpgItems.TOME_ID.get(), id);
        return s;
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void tomeReadOnce(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, vanguard(3));
        p.setItemInHand(InteractionHand.MAIN_HAND, tome("first_steps"));
        p.setItemInHand(InteractionHand.OFF_HAND, tome("first_steps"));
        AncientTomeItem.read(p, InteractionHand.MAIN_HAND);
        check(h, 0.05, RpgAttachments.get(p).knowledge(), "knowledge after first read");
        if (!p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            h.fail("first tome should be consumed");
        }
        AncientTomeItem.read(p, InteractionHand.OFF_HAND);
        check(h, 0.05, RpgAttachments.get(p).knowledge(), "knowledge after second read");
        if (p.getItemInHand(InteractionHand.OFF_HAND).getCount() != 1) {
            h.fail("second tome must not be consumed");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void unknownTomeNotConsumed(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, vanguard(3));
        p.setItemInHand(InteractionHand.MAIN_HAND, tome("no_such_tome"));
        AncientTomeItem.read(p, InteractionHand.MAIN_HAND);
        if (p.getItemInHand(InteractionHand.MAIN_HAND).getCount() != 1 || RpgAttachments.get(p).knowledge() != 0.0) {
            h.fail("unknown tome must do nothing");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void knowledgeCapped(GameTestHelper h) {
        RpgPlayerData d = vanguard(3);
        for (int i = 0; i < 11; i++) {
            d = d.withTome("test_tome_" + i, 0.05, 0.5);
        }
        check(h, 0.5, d.knowledge(), "capped knowledge");
        h.succeed();
    }
}
