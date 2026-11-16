package dev.goddessrpg.player;

import dev.goddessrpg.GoddessRpg;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class RpgAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, GoddessRpg.MODID);

    public static final Supplier<AttachmentType<RpgPlayerData>> DATA = REGISTER.register("rpg_data",
            () -> AttachmentType.builder(() -> RpgPlayerData.EMPTY)
                    .serialize(RpgPlayerData.CODEC)
                    .copyOnDeath()
                    .build());

    private RpgAttachments() {}

    public static RpgPlayerData get(Player player) {
        return player.getData(DATA);
    }

    /** Stores the data and pushes it to the owning client. */
    public static void set(ServerPlayer player, RpgPlayerData data) {
        player.setData(DATA, data);
        RpgSync.send(player);
    }
}
