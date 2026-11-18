package dev.goddessrpg.skill;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.client.ClientSync;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.data.TomeData;
import dev.goddessrpg.rules.InquiryData.Question;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.tome.TomeRef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payloads for the skill system: mana sync, cast requests, cooldown sync, and for the goddess tome the skill
 * definitions and reference data (server -> client on login and after /reload) plus slot changes (client -> server).
 */
public final class SkillPayloads {
    private SkillPayloads() {}

    public record ManaSync(double mana, double max) implements CustomPacketPayload {
        public static final Type<ManaSync> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "mana_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ManaSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, ManaSync::mana, ByteBufCodecs.DOUBLE, ManaSync::max, ManaSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the player pressed the key of slot {@code slot} (0..3). Everything is re-checked on the server. */
    public record CastRequestPayload(int slot) implements CustomPacketPayload {
        public static final Type<CastRequestPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "cast_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CastRequestPayload> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(CastRequestPayload::new, CastRequestPayload::slot);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> owner: skill id -> server game time when it is ready again. */
    public record CooldownSyncPayload(Map<String, Long> readyAtTick) implements CustomPacketPayload {
        public static final Type<CooldownSyncPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "cooldown_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> STREAM_CODEC =
                ByteBufCodecs.<RegistryFriendlyByteBuf, String, Long, Map<String, Long>>map(HashMap::new,
                                ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG)
                        .map(CooldownSyncPayload::new, p -> new HashMap<>(p.readyAtTick()));

        public CooldownSyncPayload {
            readyAtTick = Map.copyOf(readyAtTick);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: put a skill into slot {@code slot} (0..3), or clear it. Validated by {@link SkillService#onSlotSetRequest}. */
    public record SlotSetPayload(int slot, Optional<String> skillId) implements CustomPacketPayload {
        public static final Type<SlotSetPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "slot_set"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotSetPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SlotSetPayload::slot,
                ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(256)), SlotSetPayload::skillId,
                SlotSetPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: every loaded skill definition, so the tome can draw the mind map, names and hints. */
    public record SkillDefsPayload(List<SkillDef> skills) implements CustomPacketPayload {
        public static final Type<SkillDefsPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "skill_defs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SkillDefsPayload> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(SkillDef.CODEC.listOf()).map(SkillDefsPayload::new, SkillDefsPayload::skills);

        public SkillDefsPayload {
            skills = List.copyOf(skills);
        }

        public static SkillDefsPayload fromRegistry() {
            return new SkillDefsPayload(List.copyOf(SkillRegistry.all().values()));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: level cap, tome knowledge values and inquiry text keys (see {@link TomeRef}). */
    public record TomeRefPayload(TomeRef ref) implements CustomPacketPayload {
        public static final Type<TomeRefPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, "tome_ref"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TomeRefPayload> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(TomeRef.CODEC).map(TomeRefPayload::new, TomeRefPayload::ref);

        private static List<TomeRef.QuestionKeys> keys(List<Question> qs) {
            return qs.stream().map(q -> new TomeRef.QuestionKeys(q.textKey(),
                    q.answers().stream().map(a -> a.textKey()).toList())).toList();
        }

        public static TomeRefPayload fromServer() {
            Map<String, List<TomeRef.QuestionKeys>> s2 = new HashMap<>();
            Map<String, List<TomeRef.QuestionKeys>> s3 = new HashMap<>();
            for (RpgClass c : RpgClass.values()) {
                s2.put(c.id(), keys(RpgDataLoader.stage2(c)));
                s3.put(c.id(), keys(RpgDataLoader.stage3(c)));
            }
            Map<String, Double> tomes = new HashMap<>();
            RpgDataLoader.tomeIds().forEach(id -> RpgDataLoader.tome(id).map(TomeData::knowledge).ifPresent(k -> tomes.put(id, k)));
            return new TomeRefPayload(new TomeRef(RpgDataLoader.tuning().maxLevel(), RpgDataLoader.tuning().knowledgeCap(),
                    tomes, keys(RpgDataLoader.stage1()), s2, s3));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(SkillPayloads::onRegister);
        // fires for a joining player (after data is loaded) and for everyone after /reload
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> e.getRelevantPlayers().forEach(p -> {
            sendTomeData(p);
            SkillService.grantMetProficiencySkills(p);
            dev.goddessrpg.skill.SkillEffects.refreshPassives(p);
        }));
    }

    public static void sendTomeData(ServerPlayer player) {
        if (player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, SkillDefsPayload.fromRegistry(), TomeRefPayload.fromServer());
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ManaSync.TYPE, ManaSync.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientSync.applyMana(p.mana(), p.max())));
        registrar.playToClient(CooldownSyncPayload.TYPE, CooldownSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.goddessrpg.skill.client.ClientSkillState.applyCooldowns(p.readyAtTick())));
        registrar.playToServer(CastRequestPayload.TYPE, CastRequestPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        SkillService.onCastRequest(sp, p.slot());
                    }
                }));
        registrar.playToServer(SlotSetPayload.TYPE, SlotSetPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        SkillService.onSlotSetRequest(sp, p);
                    }
                }));
        // the handlers only touch the client-side store when run, so a dedicated server never loads it
        registrar.playToClient(SkillDefsPayload.TYPE, SkillDefsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.goddessrpg.tome.client.ClientTomeData.applySkills(p.skills())));
        registrar.playToClient(TomeRefPayload.TYPE, TomeRefPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.goddessrpg.tome.client.ClientTomeData.applyRef(p.ref())));
    }
}
