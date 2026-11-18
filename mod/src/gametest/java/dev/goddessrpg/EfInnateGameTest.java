package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.event.types.player.SkillCastEvent;
import yesman.epicfight.registry.entries.EpicFightSkills;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillSlots;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

@GameTestHolder(GoddessRpg.MODID)
public class EfInnateGameTest {
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-innate-player"), ClientInformation.createDefault());
    }

    private static SkillCastEvent post(ServerPlayerPatch pp, SkillContainer c) {
        return EpicFightEventHooks.Player.CAST_SKILL.post(new SkillCastEvent(pp, c, new CompoundTag()));
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void innateSkillBlocked(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        ServerPlayerPatch pp = EpicFightCapabilities.getServerPlayerPatch(p);
        if (pp == null) {
            h.fail("no Epic Fight player patch on fake player");
            return;
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        SkillContainer innate = pp.getSkill(SkillSlots.WEAPON_INNATE);
        innate.setSkill(EpicFightSkills.SWEEPING_EDGE.get());
        if (!post(pp, innate).isCanceled()) {
            h.fail("innate cast event was not cancelled");
        }
        if (innate.requestCasting(pp, new CompoundTag())) {
            h.fail("innate requestCasting was accepted");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dodgeCastNotCancelled(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        ServerPlayerPatch pp = EpicFightCapabilities.getServerPlayerPatch(p);
        if (pp == null) {
            h.fail("no Epic Fight player patch on fake player");
            return;
        }
        SkillContainer dodge = pp.getSkill(SkillSlots.DODGE);
        dodge.setSkill(EpicFightSkills.ROLL.get());
        if (post(pp, dodge).isCanceled()) {
            h.fail("dodge cast was cancelled");
        }
        h.succeed();
    }
}
