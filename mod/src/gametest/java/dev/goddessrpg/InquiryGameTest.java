package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.inquiry.InquiryService;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.RpgClass;
import java.util.List;
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
public class InquiryGameTest {
    /** Never placed in the PlayerList (see PlayerDataGameTest); network sends are skipped for null connections. */
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "inquiry-fake"), ClientInformation.createDefault());
    }

    private static int tomes(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(RpgItems.GODDESS_TOME.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fullInquiryGivesBalancedAndTome(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        InquiryService.onLogin(p);
        if (!InquiryService.submit(p, 1, List.of(0, 0, 0))) {
            h.fail("stage 1 rejected");
        }
        if (!InquiryService.submit(p, 2, List.of(0, 0, 0))) {
            h.fail("stage 2 rejected");
        }
        if (RpgAttachments.get(p).inquiryDone()) {
            h.fail("done before stage 3");
        }
        if (!InquiryService.submit(p, 3, List.of(0, 0, 0, 0))) {
            h.fail("stage 3 rejected");
        }
        RpgPlayerData d = RpgAttachments.get(p);
        if (!d.inquiryDone() || d.rpgClass().orElse(null) != RpgClass.VANGUARD || !d.subtype().equals(java.util.Optional.of("balanced"))) {
            h.fail("bad result: " + d);
        }
        if (tomes(p) != 1) {
            h.fail("expected 1 tome, got " + tomes(p));
        }
        int expected = RpgDataLoader.tuning().talentStartLevel().get(d.talentOf(Field.SWORD));
        if (d.levelOf(Field.SWORD) != expected) {
            h.fail("sword start level " + d.levelOf(Field.SWORD) + " != " + expected);
        }
        if (d.inquiryAnswers().size() != 10) {
            h.fail("answers not stored: " + d.inquiryAnswers());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void forgedAnswersIgnored(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        InquiryService.onLogin(p);
        if (InquiryService.submit(p, 2, List.of(0, 0, 0))) {
            h.fail("stage 2 before stage 1 accepted");
        }
        if (InquiryService.submit(p, 1, List.of(99, 0, 0))) {
            h.fail("pick 99 accepted");
        }
        if (InquiryService.submit(p, 1, List.of(0, 0))) {
            h.fail("short pick list accepted");
        }
        if (InquiryService.submit(p, 1, List.of(-1, 0, 0))) {
            h.fail("negative pick accepted");
        }
        if (!RpgAttachments.get(p).equals(RpgPlayerData.EMPTY)) {
            h.fail("data changed by forged input");
        }
        InquiryService.submit(p, 1, List.of(0, 0, 0));
        InquiryService.submit(p, 2, List.of(0, 0, 0));
        InquiryService.submit(p, 3, List.of(0, 0, 0, 0));
        RpgPlayerData done = RpgAttachments.get(p);
        if (!done.inquiryDone()) {
            h.fail("inquiry did not finish");
        }
        if (InquiryService.submit(p, 1, List.of(1, 1, 1)) || InquiryService.submit(p, 3, List.of(1, 1, 1, 1))) {
            h.fail("submit after completion accepted");
        }
        if (!RpgAttachments.get(p).equals(done)) {
            h.fail("result changed after completion");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void inquiryReopensAfterRelog(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        InquiryService.onLogin(p);
        InquiryService.submit(p, 1, List.of(0, 0, 0));
        if (InquiryService.currentStage(p) != 2) {
            h.fail("expected stage 2, got " + InquiryService.currentStage(p));
        }
        InquiryService.onLogin(p);
        if (InquiryService.currentStage(p) != 1 || RpgAttachments.get(p).inquiryDone()) {
            h.fail("inquiry not restarted at stage 1");
        }
        if (InquiryService.submit(p, 2, List.of(0, 0, 0))) {
            h.fail("old progress survived relog");
        }
        if (!RpgAttachments.get(p).equals(RpgPlayerData.EMPTY)) {
            h.fail("partial result was saved");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void rerollKeepsKnowledge(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, RpgPlayerData.EMPTY.withTome("t", 0.1, 0.5));
        InquiryService.onLogin(p);
        InquiryService.submit(p, 1, List.of(0, 0, 0));
        InquiryService.submit(p, 2, List.of(0, 0, 0));
        InquiryService.submit(p, 3, List.of(0, 0, 0, 0));
        if (!RpgAttachments.get(p).inquiryDone()) {
            h.fail("setup: inquiry not done");
        }
        InquiryService.reroll(p);
        RpgPlayerData d = RpgAttachments.get(p);
        if (d.inquiryDone() || d.rpgClass().isPresent() || d.knowledge() != 0.1) {
            h.fail("reroll wrong: " + d);
        }
        if (InquiryService.currentStage(p) != 1) {
            h.fail("inquiry not reopened");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void rerollNeedsPermission(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        InquiryService.onLogin(p);
        InquiryService.submit(p, 1, List.of(0, 0, 0));
        InquiryService.submit(p, 2, List.of(0, 0, 0));
        InquiryService.submit(p, 3, List.of(0, 0, 0, 0));
        RpgPlayerData before = RpgAttachments.get(p);
        if (p.hasPermissions(2) || p.isCreative()) {
            h.fail("fake player unexpectedly privileged");
        }
        RpgItems.REROLL.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        if (!RpgAttachments.get(p).equals(before)) {
            h.fail("unprivileged reroll changed data");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void inquiryResentAfterRespawn(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        InquiryService.onLogin(p);
        if (!InquiryService.submit(p, 1, List.of(0, 0, 0))) {
            h.fail("stage 1 rejected");
        }
        ServerPlayer fresh = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                p.getGameProfile(), ClientInformation.createDefault());
        fresh.restoreFrom(p, false);
        InquiryService.resend(fresh);
        if (!InquiryService.currentStage(fresh.getUUID()).equals(java.util.OptionalInt.of(2))) {
            h.fail("stage changed: " + InquiryService.currentStage(fresh.getUUID()));
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void inquiryPlayerInvulnerable(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        InquiryService.onLogin(p);
        var ev = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(
                p, new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().generic(), 5f));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ev);
        if (!ev.isCanceled()) {
            h.fail("damage not cancelled during inquiry");
        }
        ServerPlayer q = fakePlayer(h);
        RpgAttachments.set(q, RpgAttachments.get(q).withResult(RpgClass.VANGUARD, "balanced",
                new dev.goddessrpg.rules.StarResult(
                java.util.Map.of(Field.SWORD, 1), java.util.Map.of(Field.SWORD, 1)), List.of(), RpgDataLoader.tuning()));
        var ev2 = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(
                q, new net.neoforged.neoforge.common.damagesource.DamageContainer(q.damageSources().generic(), 5f));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ev2);
        if (ev2.isCanceled()) {
            h.fail("damage cancelled without inquiry");
        }
        h.succeed();
    }
}
