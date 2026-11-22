package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.command.RpgCommand;
import dev.goddessrpg.growth.GrowthEvents;
import dev.goddessrpg.creation.CreationService;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.item.SkillBookItem;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.LevelProgress;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.StarResult;
import dev.goddessrpg.skill.SkillService;
import dev.goddessrpg.skill.SkillService.LearnResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GoddessRpg.MODID)
public class SkillLearnGameTest {
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-fake-player"), ClientInformation.createDefault());
    }

    private static RpgPlayerData player(RpgClass c, String subtype, int talent) {
        StarResult stars = new StarResult(
                Map.of(Field.SWORD, talent, Field.DEFENSE, talent, Field.ARCHERY, talent, Field.ELEMENTAL, talent),
                Map.of());
        return RpgPlayerData.EMPTY.withResult(c, subtype, stars, List.of(), GrowthTuning.DEFAULT);
    }

    private static ItemStack book(String id) {
        ItemStack s = new ItemStack(RpgItems.SKILL_BOOK.get());
        s.set(RpgItems.SKILL_ID.get(), id);
        return s;
    }

    private static void expect(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    /** Talent 1 starts every field at level 0, so nothing is granted at inquiry time. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void autoLearnOnProficiency(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.VANGUARD, "balanced", 1).withXp(Field.SWORD, new LevelProgress(9, 0)));
        expect(h, RpgAttachments.get(p).learnedSkills().isEmpty(), "nothing learned yet");
        GrowthEvents.grant(p, Field.SWORD, 100000);
        RpgPlayerData d = RpgAttachments.get(p);
        expect(h, d.levelOf(Field.SWORD) >= 10, "sword level " + d.levelOf(Field.SWORD));
        expect(h, d.learnedSkills().contains("test_vanguard_prof10"), "not learned: " + d.learnedSkills());
        // real skills (balanced_dash_slash, balanced_counter, ...) are auto-learned at the same time and compete for slots
        expect(h, d.slots().contains(Optional.of("test_vanguard_prof10")), "not slotted: " + d.slots());
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void startingLevelSkillsGranted(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        // talent 3 starts at level 4 (defense >= 3 for test_vanguard_start)
        RpgAttachments.set(p, player(RpgClass.VANGUARD, "balanced", 3));
        SkillService.grantMetProficiencySkills(p);
        RpgPlayerData d = RpgAttachments.get(p);
        expect(h, d.learnedSkills().contains("test_vanguard_start"), "starting skill not granted: " + d.learnedSkills());
        expect(h, !d.learnedSkills().contains("test_vanguard_prof10"), "prof10 granted too early");
        expect(h, !d.learnedSkills().contains("test_dealer_prof"), "foreign class skill granted");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void levelCommandTriggersAutoLearn(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.VANGUARD, "balanced", 1));
        RpgCommand.setLevel(p, Field.SWORD, 10);
        RpgPlayerData d = RpgAttachments.get(p);
        expect(h, d.levelOf(Field.SWORD) == 10 && d.xpOf(Field.SWORD) == 0.0, "level/xp: " + d);
        expect(h, d.learnedSkills().contains("test_vanguard_prof10"), "not learned: " + d.learnedSkills());
        RpgCommand.setLevel(p, Field.SWORD, 999);
        expect(h, RpgAttachments.get(p).levelOf(Field.SWORD) == GrowthTuning.DEFAULT.maxLevel(), "clamped");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void xpCommandTriggersAutoLearn(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.VANGUARD, "balanced", 1));
        expect(h, !RpgAttachments.get(p).learnedSkills().contains("test_vanguard_prof10"), "learned too early");
        RpgCommand.addXp(p, Field.SWORD, 1.0e9);
        RpgPlayerData d = RpgAttachments.get(p);
        expect(h, d.levelOf(Field.SWORD) >= 10, "level: " + d.levelOf(Field.SWORD));
        expect(h, d.learnedSkills().contains("test_vanguard_prof10"), "not learned: " + d.learnedSkills());
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void skillBookLearnsAndConsumes(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.VANGUARD, "balanced", 1));
        p.setItemInHand(InteractionHand.MAIN_HAND, book("test_vanguard_book"));
        p.setItemInHand(InteractionHand.OFF_HAND, book("test_dealer_book"));
        SkillBookItem.use(p, InteractionHand.MAIN_HAND);
        expect(h, RpgAttachments.get(p).learnedSkills().contains("test_vanguard_book"), "own-class book not learned");
        expect(h, p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "own-class book not consumed");
        SkillBookItem.use(p, InteractionHand.OFF_HAND);
        expect(h, !RpgAttachments.get(p).learnedSkills().contains("test_dealer_book"), "foreign book learned");
        expect(h, p.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1, "foreign book consumed");
        // branch skill: balanced cannot, tank can
        p.setItemInHand(InteractionHand.MAIN_HAND, book("test_tank_branch"));
        SkillBookItem.use(p, InteractionHand.MAIN_HAND);
        expect(h, !RpgAttachments.get(p).learnedSkills().contains("test_tank_branch")
                && p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "branch skill leaked to balanced");
        ServerPlayer t = fakePlayer(h);
        RpgAttachments.set(t, player(RpgClass.VANGUARD, "tank", 1));
        if (SkillService.learn(t, "test_tank_branch") != LearnResult.OK
                || SkillService.learn(t, "test_tank_branch") != LearnResult.ALREADY
                || SkillService.learn(t, "nope") != LearnResult.UNKNOWN
                || SkillService.learn(p, "test_dealer_book") != LearnResult.NOT_ALLOWED) {
            h.fail("learn results wrong");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void slotRules(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.VANGUARD, "balanced", 1));
        expect(h, !SkillService.setSlot(p, 0, Optional.of("test_vanguard_book")), "unlearned skill accepted");
        SkillService.learn(p, "test_vanguard_book"); // auto slot 0
        SkillService.learn(p, "test_vanguard_passive");
        expect(h, RpgAttachments.get(p).slots().get(0).equals(Optional.of("test_vanguard_book")), "auto slot");
        expect(h, RpgAttachments.get(p).slots().get(1).isEmpty(), "passive must not take a slot");
        expect(h, !SkillService.setSlot(p, 1, Optional.of("test_vanguard_passive")), "passive accepted");
        expect(h, !SkillService.setSlot(p, 4, Optional.of("test_vanguard_book")), "index 4 accepted");
        expect(h, SkillService.setSlot(p, 2, Optional.of("test_vanguard_book")), "slot 2 refused");
        List<Optional<String>> s = RpgAttachments.get(p).slots();
        expect(h, s.get(0).isEmpty() && s.get(2).equals(Optional.of("test_vanguard_book")), "not moved: " + s);
        expect(h, SkillService.setSlot(p, 2, Optional.empty()) && RpgAttachments.get(p).slots().get(2).isEmpty(), "clear");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void rerollClearsSkills(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.VANGUARD, "balanced", 1).withTome("a", 0.1, 0.5));
        SkillService.learn(p, "test_vanguard_book");
        CreationService.reopen(p, true);
        RpgPlayerData d = RpgAttachments.get(p);
        expect(h, d.learnedSkills().isEmpty(), "skills kept: " + d.learnedSkills());
        expect(h, d.slots().size() == 4 && d.slots().stream().allMatch(Optional::isEmpty), "slots kept: " + d.slots());
        expect(h, d.knowledge() == 0.1 && d.tomesRead().contains("a"), "knowledge lost");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void skillsSurviveDeath(GameTestHelper h) {
        ServerPlayer old = fakePlayer(h);
        RpgAttachments.set(old, player(RpgClass.VANGUARD, "balanced", 1));
        SkillService.learn(old, "test_vanguard_book");
        RpgPlayerData before = RpgAttachments.get(old);
        ServerPlayer fresh = fakePlayer(h);
        fresh.restoreFrom(old, false);
        RpgPlayerData got = RpgAttachments.get(fresh);
        expect(h, got.learnedSkills().equals(before.learnedSkills()) && got.slots().equals(before.slots())
                && !got.learnedSkills().isEmpty(), "skills lost: " + got);
        h.succeed();
    }
}
