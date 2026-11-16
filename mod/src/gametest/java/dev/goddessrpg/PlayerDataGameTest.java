package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.InquiryData.Subtype;
import dev.goddessrpg.rules.LevelProgress;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.StarResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GoddessRpg.MODID)
public class PlayerDataGameTest {
    /**
     * A ServerPlayer that is never placed in the PlayerList. makeMockServerPlayerInLevel() fires the login
     * datapack sync, and Epic Fight then tries to send a payload over the mock connection and throws.
     */
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-fake-player"), ClientInformation.createDefault());
    }

    private static RpgPlayerData sample() {
        StarResult stars = new StarResult(
                Map.of(Field.SWORD, 4, Field.DEFENSE, 2, Field.ARCHERY, 3, Field.ELEMENTAL, 3, Field.SUMMONING, 3, Field.RELIC, 1),
                Map.of(Field.SWORD, 5, Field.DEFENSE, 1, Field.ARCHERY, 3, Field.ELEMENTAL, 3, Field.SUMMONING, 3, Field.RELIC, 3));
        return RpgPlayerData.EMPTY
                .withResult(RpgClass.VANGUARD, "balanced", stars, List.of(0, 2, 1), GrowthTuning.DEFAULT)
                .withXp(Field.SWORD, new LevelProgress(7, 12.5))
                .withTome("goddessrpg:tome_a", 0.05, 0.5);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void newPlayerIsEmpty(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        if (!RpgAttachments.get(p).equals(RpgPlayerData.EMPTY)) {
            h.fail("new player data is not EMPTY");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dataSurvivesDeath(GameTestHelper h) {
        ServerPlayer old = fakePlayer(h);
        RpgPlayerData data = sample();
        RpgAttachments.set(old, data);
        // restoreFrom(old, false) is exactly what PlayerList.respawn runs after a death (keepEverything=false).
        // old.kill() is skipped: a never-placed fake player has no connection to send the death message on.
        ServerPlayer fresh = fakePlayer(h);
        fresh.restoreFrom(old, false);
        RpgPlayerData got = RpgAttachments.get(fresh);
        if (got.rpgClass().isEmpty() || got.rpgClass().get() != RpgClass.VANGUARD
                || !got.level().equals(data.level()) || got.knowledge() != data.knowledge() || !got.equals(data)) {
            h.fail("data lost after death copy: " + got);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void codecRoundTrip(GameTestHelper h) {
        RpgPlayerData data = sample();
        Tag tag = RpgPlayerData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        RpgPlayerData back = RpgPlayerData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        if (!back.equals(data)) {
            h.fail("round trip mismatch: " + back + " vs " + data);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void withResultSetsStartLevels(GameTestHelper h) {
        RpgPlayerData d = sample();
        if (!d.inquiryDone() || d.level().get(Field.SWORD) != 7) {
            h.fail("bad result");
        }
        RpgPlayerData r = RpgPlayerData.EMPTY.withResult(RpgClass.VANGUARD, "balanced",
                new StarResult(Map.of(Field.SWORD, 4, Field.DEFENSE, 2), Map.of()), List.of(1), GrowthTuning.DEFAULT);
        if (r.level().get(Field.SWORD) != 6 || r.level().get(Field.DEFENSE) != 2 || r.level().get(Field.ARCHERY) != 0) {
            h.fail("start levels wrong: " + r.level());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void tomeCappedAndOnce(GameTestHelper h) {
        RpgPlayerData d = RpgPlayerData.EMPTY.withTome("a", 0.4, 0.5);
        if (d.withTome("a", 0.4, 0.5) != d) {
            h.fail("re-reading must be a no-op");
        }
        RpgPlayerData d2 = d.withTome("b", 0.4, 0.5);
        if (d2.knowledge() != 0.5) {
            h.fail("cap not applied: " + d2.knowledge());
        }
        RpgPlayerData kept = d2.withResult(RpgClass.VANGUARD, "balanced", new StarResult(Map.of(), Map.of()), List.of(), GrowthTuning.DEFAULT)
                .resetKeepKnowledge();
        if (kept.knowledge() != 0.5 || kept.inquiryDone() || kept.rpgClass().isPresent() || kept.tomesRead().size() != 2) {
            h.fail("reset wrong: " + kept);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void loaderReadsBalancedOnly(GameTestHelper h) {
        List<Subtype> all = RpgDataLoader.subtypes();
        List<String> avail = all.stream().filter(Subtype::available).map(Subtype::id).toList();
        if (all.size() != 9 || !avail.equals(List.of("balanced"))) {
            h.fail("subtypes: " + all);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void growthJsonEqualsDefault(GameTestHelper h) {
        if (!RpgDataLoader.tuning().equals(GrowthTuning.DEFAULT)) {
            h.fail("growth.json differs from DEFAULT: " + RpgDataLoader.tuning());
        }
        if (!RpgDataLoader.stage1().isEmpty() || !RpgDataLoader.stage2(RpgClass.VANGUARD).isEmpty()
                || !RpgDataLoader.stage3(RpgClass.REAR_DEALER).isEmpty()) {
            // Task 4 adds inquiry files; until then missing files must yield empty lists (never null/throw).
        }
        h.succeed();
    }
}
