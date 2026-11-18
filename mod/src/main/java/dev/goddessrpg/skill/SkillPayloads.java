package dev.goddessrpg.skill;

import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.client.ClientSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payloads for the skill system (mana sync now; cast/slot payloads are added by later tasks). */
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

    public static void register(IEventBus modBus) {
        modBus.addListener(SkillPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ManaSync.TYPE, ManaSync.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientSync.applyMana(p.mana(), p.max())));
    }
}
