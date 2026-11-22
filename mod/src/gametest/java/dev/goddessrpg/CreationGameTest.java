package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.creation.CreationPayloads.OpenCreationPayload;
import dev.goddessrpg.creation.CreationPayloads.SubmitCreationPayload;
import dev.goddessrpg.creation.CreationPayloads.SubtypeCard;
import dev.goddessrpg.creation.CreationService;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.CreationChoice;
import dev.goddessrpg.rules.CreationChoice.Allocation;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.LevelProgress;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.skill.SkillCooldowns;
import dev.goddessrpg.skill.SkillService;
import dev.goddessrpg.skill.SkillService.CastResult;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

@GameTestHolder(GoddessRpg.MODID)
public class CreationGameTest {
    private static final CreationChoice BALANCED_4233 = new CreationChoice("balanced", new Allocation(4, 2, 3, 3));

    /** Never placed in the PlayerList (see PlayerDataGameTest); network sends are skipped for null connections. */
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "creation-fake"), ClientInformation.createDefault());
        ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, p, 0, "spawnInvulnerableTime");
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.damagesource.CombatTracker.class,
                p.getCombatTracker(), true, "inCombat");
        return p;
    }

    private static void expect(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static <T> T roundTrip(GameTestHelper h, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        codec.encode(buf, value);
        return codec.decode(buf);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void creationGivesBalancedAndTome(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        CreationService.onLogin(p);
        expect(h, CreationService.isInCreation(p), "creation not opened on login");
        expect(h, CreationService.submit(p, BALANCED_4233), "valid creation rejected");
        RpgPlayerData d = RpgAttachments.get(p);
        expect(h, d.inquiryDone() && d.rpgClass().orElse(null) == RpgClass.VANGUARD
                && d.subtype().equals(Optional.of("balanced")), "bad result: " + d);
        expect(h, d.creation().equals(Optional.of(BALANCED_4233)), "choice not recorded: " + d.creation());
        int expected = RpgDataLoader.tuning().talentStartLevel().get(4);
        expect(h, d.levelOf(Field.SWORD) == expected, "sword start level " + d.levelOf(Field.SWORD) + " != " + expected);
        expect(h, d.talentOf(Field.SWORD) == 4 && d.aptitudeOf(Field.SWORD) == 2
                && d.talentOf(Field.DEFENSE) == 3 && d.aptitudeOf(Field.DEFENSE) == 3, "stars " + d);
        expect(h, count(p, RpgItems.GODDESS_TOME.get()) == 1, "expected 1 tome, got " + count(p, RpgItems.GODDESS_TOME.get()));
        expect(h, !CreationService.isInCreation(p), "still in creation after submit");
        // the open payload: hidden subtypes left out, sorted by order, availability carried
        OpenCreationPayload open = CreationService.openPayload();
        List<String> ids = open.cards().stream().map(SubtypeCard::subtype).toList();
        expect(h, !ids.contains("spellbow") && ids.size() == 8 && ids.get(0).equals("balanced"), "cards " + ids);
        expect(h, open.cards().stream().filter(SubtypeCard::available).map(SubtypeCard::subtype).toList()
                .equals(List.of("balanced")), "availability " + open.cards());
        expect(h, open.tuning().equals(RpgDataLoader.allocationTuning()), "tuning " + open.tuning());
        expect(h, roundTrip(h, OpenCreationPayload.STREAM_CODEC, open).equals(open), "open payload round trip");
        SubmitCreationPayload sub = new SubmitCreationPayload(BALANCED_4233);
        expect(h, roundTrip(h, SubmitCreationPayload.STREAM_CODEC, sub).equals(sub), "submit payload round trip");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void forgedCreationRejected(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        CreationService.onLogin(p);
        expect(h, !CreationService.submit(p, new CreationChoice("balanced", new Allocation(4, 3, 3, 3))), "sum 13 accepted");
        expect(h, !CreationService.submit(p, new CreationChoice("balanced", new Allocation(6, 1, 4, 1))), "star 6 accepted");
        expect(h, !CreationService.submit(p, new CreationChoice("balanced", new Allocation(0, 4, 4, 4))), "star 0 accepted");
        expect(h, !CreationService.submit(p, new CreationChoice("tank", new Allocation(3, 3, 3, 3))), "unavailable accepted");
        expect(h, !CreationService.submit(p, new CreationChoice("spellbow", new Allocation(3, 3, 3, 3))), "hidden accepted");
        expect(h, !CreationService.submit(p, new CreationChoice("nope", new Allocation(3, 3, 3, 3))), "unknown accepted");
        expect(h, RpgAttachments.get(p).equals(RpgPlayerData.EMPTY), "data changed by forged input");
        expect(h, CreationService.isInCreation(p), "forged input closed creation");
        expect(h, CreationService.submit(p, BALANCED_4233), "valid creation rejected");
        RpgPlayerData done = RpgAttachments.get(p);
        expect(h, !CreationService.submit(p, new CreationChoice("balanced", new Allocation(5, 1, 5, 1))), "resubmit accepted");
        expect(h, RpgAttachments.get(p).equals(done), "result changed after completion");
        // a player who never opened creation cannot submit either
        ServerPlayer q = fakePlayer(h);
        expect(h, !CreationService.submit(q, BALANCED_4233), "submit without open creation accepted");
        expect(h, RpgAttachments.get(q).equals(RpgPlayerData.EMPTY), "data changed without session");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void creationReopensAfterRelog(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        CreationService.onLogin(p);
        CreationService.onLogout(p);
        expect(h, !CreationService.isInCreation(p), "session survived logout");
        CreationService.onLogin(p);
        expect(h, CreationService.isInCreation(p), "creation not reopened on relog");
        expect(h, RpgAttachments.get(p).equals(RpgPlayerData.EMPTY), "partial result saved");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void creationInvulnerableAndResent(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        CreationService.onLogin(p);
        var ev = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(
                p, new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().generic(), 5f));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ev);
        expect(h, ev.isCanceled(), "damage not cancelled during creation");

        int sent = CreationService.openCount(p);
        ServerPlayer fresh = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                p.getGameProfile(), ClientInformation.createDefault());
        fresh.restoreFrom(p, false);
        CreationService.onRespawn(fresh);
        expect(h, CreationService.openCount(fresh) == sent + 1, "not resent after respawn");
        CreationService.onDimensionChange(fresh);
        expect(h, CreationService.openCount(fresh) == sent + 2, "not resent after dimension change");

        ServerPlayer q = fakePlayer(h);
        CreationService.onLogin(q);
        CreationService.submit(q, BALANCED_4233);
        int qSent = CreationService.openCount(q);
        CreationService.onRespawn(q);
        CreationService.onDimensionChange(q);
        expect(h, CreationService.openCount(q) == qSent, "screen resent after creation finished");
        var ev2 = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(
                q, new net.neoforged.neoforge.common.damagesource.DamageContainer(q.damageSources().generic(), 5f));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ev2);
        expect(h, !ev2.isCanceled(), "damage cancelled without creation");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void legacySaveStillPlayable(GameTestHelper h) {
        String json = "{\"inquiry_done\":true,\"rpg_class\":\"vanguard\",\"subtype\":\"balanced\","
                + "\"talent\":{\"sword\":3,\"defense\":3},\"aptitude\":{\"sword\":3,\"defense\":3},"
                + "\"level\":{\"sword\":4,\"defense\":4},\"inquiry_answers\":[0,1,2],"
                + "\"learned_skills\":[\"test_cast_buff\"],\"slots\":[\"test_cast_buff\",\"\",\"\",\"\"]}";
        RpgPlayerData legacy = RpgPlayerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString(json)).getOrThrow();
        expect(h, legacy.creation().isEmpty() && legacy.inquiryAnswers().equals(List.of(0, 1, 2)), "legacy parse " + legacy);
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, legacy);
        CreationService.onLogin(p);
        expect(h, !CreationService.isInCreation(p), "creation opened for a legacy character");
        expect(h, CreationService.openCount(p) == 0, "open payload sent to a legacy character");
        ServerPlayerPatch pp = EpicFightCapabilities.getServerPlayerPatch(p);
        expect(h, pp != null, "no Epic Fight patch");
        pp.setStamina(pp.getMaxStamina());
        CastResult r = SkillService.cast(p, 0);
        expect(h, r == CastResult.OK, "legacy cast " + r);
        // the codec still round-trips the old answers next to the (absent) creation
        RpgPlayerData back = RpgPlayerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                RpgPlayerData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                        RpgAttachments.get(p)).getOrThrow()).getOrThrow();
        expect(h, back.equals(RpgAttachments.get(p)), "codec round trip");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void reawakeningResetsAndConsumes(GameTestHelper h) {
        // not created yet: refused, nothing consumed
        ServerPlayer n = fakePlayer(h);
        CreationService.onLogin(n);
        n.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.REAWAKENING_CRYSTAL.get(), 2));
        RpgItems.REAWAKENING_CRYSTAL.get().use(h.getLevel(), n, InteractionHand.MAIN_HAND);
        RpgItems.REAWAKENING_CRYSTAL.get().use(h.getLevel(), n, InteractionHand.MAIN_HAND);
        expect(h, n.getMainHandItem().getCount() == 2, "crystal consumed by an uncreated player");

        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, RpgPlayerData.EMPTY.withTome("t", 0.1, 0.5));
        CreationService.onLogin(p);
        expect(h, CreationService.submit(p, BALANCED_4233), "setup: creation rejected");
        RpgPlayerData d = RpgAttachments.get(p).withXp(Field.SWORD, new LevelProgress(20, 0.0))
                .withLearned("test_cast_buff").withLearned("test_cast_strike").withLearned("test_vanguard_book")
                .withSlot(0, Optional.of("test_cast_buff"));
        RpgAttachments.set(p, d);
        SkillCooldowns.start(p, "test_cast_buff", 30);
        expect(h, !SkillCooldowns.all(p).isEmpty(), "setup: no cooldown");
        expect(h, RpgAttachments.get(p).learnedSkills().size() >= 3, "setup: skills " + RpgAttachments.get(p).learnedSkills());

        p.getInventory().selected = 8; // keep the tome (given into the first free slot) out of the hand
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.REAWAKENING_CRYSTAL.get(), 2));
        RpgItems.REAWAKENING_CRYSTAL.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        expect(h, p.getMainHandItem().getCount() == 2, "first use consumed");
        expect(h, RpgAttachments.get(p).equals(d), "first use already reset");
        expect(h, !CreationService.isInCreation(p), "first use opened creation");
        RpgItems.REAWAKENING_CRYSTAL.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        expect(h, p.getMainHandItem().getCount() == 1, "second use did not consume exactly one: " + p.getMainHandItem().getCount());

        RpgPlayerData r = RpgAttachments.get(p);
        expect(h, !r.inquiryDone() && r.rpgClass().isEmpty() && r.creation().isEmpty(), "class kept " + r);
        expect(h, r.levelOf(Field.SWORD) == 0 && r.level().isEmpty(), "proficiency kept " + r.level());
        expect(h, r.learnedSkills().isEmpty(), "skills kept " + r.learnedSkills());
        expect(h, r.slots().stream().allMatch(Optional::isEmpty), "slots kept " + r.slots());
        expect(h, SkillCooldowns.all(p).isEmpty(), "cooldowns kept " + SkillCooldowns.all(p));
        expect(h, r.knowledge() == 0.1 && r.tomesRead().contains("t"), "knowledge lost " + r);
        expect(h, CreationService.isInCreation(p), "creation not reopened");

        // a new creation applies the new start levels
        expect(h, CreationService.submit(p, new CreationChoice("balanced", new Allocation(2, 4, 5, 1))), "recreate rejected");
        RpgPlayerData again = RpgAttachments.get(p);
        var start = RpgDataLoader.tuning().talentStartLevel();
        expect(h, again.levelOf(Field.SWORD) == start.get(2) && again.levelOf(Field.DEFENSE) == start.get(5),
                "new start levels " + again.level());
        expect(h, count(p, RpgItems.GODDESS_TOME.get()) == 1, "tome duplicated");

        // the crafting recipe is loaded and makes one crystal
        var recipe = h.getLevel().getRecipeManager().byKey(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "reawakening_crystal"));
        expect(h, recipe.isPresent(), "crystal recipe not loaded");
        ItemStack out = recipe.get().value().getResultItem(h.getLevel().registryAccess());
        expect(h, out.is(RpgItems.REAWAKENING_CRYSTAL.get()) && out.getCount() == 1, "recipe result " + out);
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void rerollNeedsPermission(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        CreationService.onLogin(p);
        CreationService.submit(p, BALANCED_4233);
        RpgPlayerData before = RpgAttachments.get(p);
        expect(h, !p.hasPermissions(2) && !p.isCreative(), "fake player unexpectedly privileged");
        RpgItems.REROLL.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        expect(h, RpgAttachments.get(p).equals(before), "unprivileged reroll changed data");
        expect(h, !CreationService.isInCreation(p), "unprivileged reroll opened creation");
        h.succeed();
    }
}
