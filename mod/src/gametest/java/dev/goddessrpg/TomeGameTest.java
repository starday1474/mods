package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.item.RpgItems;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.rules.StarResult;
import dev.goddessrpg.skill.SkillPayloads.SkillDefsPayload;
import dev.goddessrpg.skill.SkillPayloads.SlotSetPayload;
import dev.goddessrpg.skill.SkillPayloads.TomeRefPayload;
import dev.goddessrpg.skill.SkillRegistry;
import dev.goddessrpg.skill.SkillService;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GoddessRpg.MODID)
public class TomeGameTest {
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-fake-player"), ClientInformation.createDefault());
    }

    private static RpgPlayerData vanguard() {
        StarResult stars = new StarResult(Map.of(Field.SWORD, 1, Field.DEFENSE, 1), Map.of());
        return RpgPlayerData.EMPTY.withResult(RpgClass.VANGUARD, "balanced", stars, List.of(), GrowthTuning.DEFAULT);
    }

    private static void expect(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static <T> T roundTrip(GameTestHelper h, net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        codec.encode(buf, value);
        T back = codec.decode(buf);
        if (buf.readableBytes() != 0) {
            h.fail("trailing bytes: " + buf.readableBytes());
        }
        return back;
    }

    /** A forged SlotSetPayload is validated exactly like the server-side setSlot. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void slotSetPayloadValidated(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, vanguard());
        expect(h, !SkillService.onSlotSetRequest(p, new SlotSetPayload(0, Optional.of("test_vanguard_book"))), "unlearned accepted");
        expect(h, !SkillService.onSlotSetRequest(p, new SlotSetPayload(0, Optional.of("no_such_skill"))), "unknown accepted");
        SkillService.learn(p, "test_vanguard_passive");
        expect(h, !SkillService.onSlotSetRequest(p, new SlotSetPayload(1, Optional.of("test_vanguard_passive"))), "passive accepted");
        SkillService.learn(p, "test_vanguard_book"); // auto -> slot 0
        expect(h, !SkillService.onSlotSetRequest(p, new SlotSetPayload(-1, Optional.of("test_vanguard_book"))), "slot -1 accepted");
        expect(h, !SkillService.onSlotSetRequest(p, new SlotSetPayload(4, Optional.of("test_vanguard_book"))), "slot 4 accepted");
        expect(h, SkillService.onSlotSetRequest(p, new SlotSetPayload(3, Optional.of("test_vanguard_book"))), "valid move refused");
        List<Optional<String>> s = RpgAttachments.get(p).slots();
        expect(h, s.get(0).isEmpty() && s.get(3).equals(Optional.of("test_vanguard_book")), "slots " + s);
        expect(h, SkillService.onSlotSetRequest(p, new SlotSetPayload(3, Optional.empty())), "clear refused");
        expect(h, RpgAttachments.get(p).slots().get(3).isEmpty(), "not cleared");
        // a learned skill that no longer fits the player's class (data edited) is refused too
        RpgAttachments.set(p, RpgAttachments.get(p).withLearned("test_dealer_book"));
        expect(h, !SkillService.onSlotSetRequest(p, new SlotSetPayload(0, Optional.of("test_dealer_book"))), "foreign accepted");
        // the payload codec carries both forms
        SlotSetPayload a = new SlotSetPayload(2, Optional.of("balanced_counter"));
        SlotSetPayload b = new SlotSetPayload(1, Optional.empty());
        expect(h, roundTrip(h, SlotSetPayload.STREAM_CODEC, a).equals(a) && roundTrip(h, SlotSetPayload.STREAM_CODEC, b).equals(b),
                "slot payload round trip");
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void skillDefsPayloadMatchesRegistry(GameTestHelper h) {
        SkillDefsPayload sent = SkillDefsPayload.fromRegistry();
        SkillDefsPayload got = roundTrip(h, SkillDefsPayload.STREAM_CODEC, sent);
        Map<String, SkillDef> byId = new HashMap<>();
        got.skills().forEach(s -> byId.put(s.id(), s));
        expect(h, !SkillRegistry.all().isEmpty(), "registry empty");
        expect(h, byId.size() == got.skills().size(), "duplicate ids");
        expect(h, byId.equals(SkillRegistry.all()), "payload " + byId.keySet() + " != registry " + SkillRegistry.all().keySet());
        expect(h, byId.get("balanced_counter").equals(SkillRegistry.get("balanced_counter").orElseThrow()), "counter differs");

        TomeRefPayload ref = TomeRefPayload.fromServer();
        TomeRefPayload refBack = roundTrip(h, TomeRefPayload.STREAM_CODEC, ref);
        expect(h, refBack.equals(ref), "tome ref round trip");
        expect(h, ref.ref().tomeKnowledge().containsKey("first_steps"), "tome ref content " + ref);
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void seenSkillBooksTracked(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        RpgAttachments.set(p, vanguard());
        SkillService.markSeenBooks(p);
        expect(h, RpgAttachments.get(p).seenSkillBooks().isEmpty(), "nothing seen yet");
        ItemStack book = new ItemStack(RpgItems.SKILL_BOOK.get());
        book.set(RpgItems.SKILL_ID.get(), "balanced_steadfast");
        p.getInventory().add(book);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(RpgItems.SKILL_BOOK.get())); // no id
        SkillService.markSeenBooks(p);
        expect(h, RpgAttachments.get(p).seenSkillBooks().equals(java.util.Set.of("balanced_steadfast")),
                "seen " + RpgAttachments.get(p).seenSkillBooks());
        // survives reroll and the codec
        dev.goddessrpg.creation.CreationService.reopen(p, true);
        RpgPlayerData d = RpgAttachments.get(p);
        expect(h, d.seenSkillBooks().contains("balanced_steadfast"), "reroll forgot seen books");
        RpgPlayerData back = RpgPlayerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                RpgPlayerData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, d).getOrThrow()).getOrThrow();
        expect(h, back.seenSkillBooks().equals(d.seenSkillBooks()), "codec lost seen books");
        h.succeed();
    }
}
