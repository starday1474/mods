package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.mana.ManaAttachment;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.ManaRules;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.StarResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GoddessRpg.MODID)
public class ManaGameTest {
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-fake-player"), ClientInformation.createDefault());
    }

    private static RpgPlayerData player(RpgClass c) {
        return RpgPlayerData.EMPTY.withResult(c, "balanced", new StarResult(Map.of(), Map.of()), List.of(),
                GrowthTuning.DEFAULT);
    }

    private static void expect(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void manaRegens(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.VANGUARD));
        p.setData(ManaAttachment.MANA, 0.0);
        for (int i = 0; i < 40; i++) {
            ManaAttachment.tick(p);
        }
        expect(h, Math.abs(ManaAttachment.get(p) - 1.0) < 1e-6, "mana " + ManaAttachment.get(p));
        ServerPlayer m = fakePlayer(h);
        RpgAttachments.set(m, player(RpgClass.REAR_DEALER));
        m.setData(ManaAttachment.MANA, 0.0);
        for (int i = 0; i < 40; i++) {
            ManaAttachment.tick(m);
        }
        expect(h, Math.abs(ManaAttachment.get(m) - 4.0) < 1e-6, "dealer mana " + ManaAttachment.get(m));
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void manaCappedAtMax(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.VANGUARD));
        double max = ManaAttachment.max(p);
        expect(h, max >= 30.0, "max " + max);
        p.setData(ManaAttachment.MANA, max - 0.1);
        for (int i = 0; i < 100; i++) {
            ManaAttachment.tick(p);
        }
        expect(h, ManaAttachment.get(p) == max, "mana " + ManaAttachment.get(p));
        ServerPlayer none = fakePlayer(h);
        ManaAttachment.tick(none);
        expect(h, ManaAttachment.get(none) == 0.0, "classless mana " + ManaAttachment.get(none));
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void tryConsume(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.REAR_DEALER));
        p.setData(ManaAttachment.MANA, 10.0);
        expect(h, !ManaAttachment.tryConsume(p, 10.5), "overdraw accepted");
        expect(h, ManaAttachment.get(p) == 10.0, "mana changed on failure: " + ManaAttachment.get(p));
        expect(h, ManaAttachment.tryConsume(p, 4.0) && ManaAttachment.get(p) == 6.0, "consume: " + ManaAttachment.get(p));
        expect(h, !ManaAttachment.tryConsume(p, -5.0) && !ManaAttachment.tryConsume(p, Double.NaN)
                && ManaAttachment.get(p) == 6.0, "negative/NaN amount accepted: " + ManaAttachment.get(p));
        expect(h, ManaAttachment.tryConsume(p, 0.0), "zero cost refused");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void manaRefillsOnRespawn(GameTestHelper h) {
        ServerPlayer old = fakePlayer(h);
        RpgAttachments.set(old, player(RpgClass.REAR_SUPPORTER));
        old.setData(ManaAttachment.MANA, 3.0);
        ServerPlayer fresh = fakePlayer(h);
        fresh.restoreFrom(old, false);
        expect(h, ManaAttachment.get(fresh) == 0.0, "mana must not be copied on death: " + ManaAttachment.get(fresh));
        ManaAttachment.refill(fresh);
        expect(h, ManaAttachment.get(fresh) == ManaAttachment.max(old) && ManaAttachment.max(old) >= 100.0, "not refilled: " + ManaAttachment.get(fresh));
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void manaJsonEqualsDefault(GameTestHelper h) {
        expect(h, RpgDataLoader.manaTuning().equals(ManaRules.ManaTuning.DEFAULT),
                "mana.json differs from DEFAULT: " + RpgDataLoader.manaTuning());
        h.succeed();
    }

    /** A full pool never triggers a throttled sync; a dimension change must still resend it. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dimensionChangeResyncs(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, player(RpgClass.REAR_DEALER));
        ManaAttachment.refill(p);
        double before = ManaAttachment.get(p);
        for (int i = 0; i < 60; i++) {
            ManaAttachment.tick(p);
        }
        int count = ManaAttachment.syncCount(p);
        for (int i = 0; i < 60; i++) {
            ManaAttachment.tick(p);
        }
        expect(h, ManaAttachment.syncCount(p) == count, "full pool kept syncing");
        ManaAttachment.resync(p);
        expect(h, ManaAttachment.syncCount(p) == count + 1, "resync did not send");
        expect(h, ManaAttachment.get(p) == before, "resync changed mana");
        h.succeed();
    }
}
