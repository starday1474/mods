package dev.goddessrpg;

import com.mojang.authlib.GameProfile;
import dev.goddessrpg.compat.epicfight.EfSkillPolicy;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yesman.epicfight.registry.entries.EpicFightSkills;
import yesman.epicfight.skill.SkillSlots;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

@GameTestHolder(GoddessRpg.MODID)
public class EfSkillGameTest {
    /** Never placed in the PlayerList (see PlayerDataGameTest): login sync over a mock connection throws. */
    private static ServerPlayer fakePlayer(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-ef-player"), ClientInformation.createDefault());
    }

    private static ServerPlayerPatch patch(GameTestHelper h, ServerPlayer p) {
        ServerPlayerPatch pp = EpicFightCapabilities.getServerPlayerPatch(p);
        if (pp == null) {
            h.fail("no Epic Fight player patch on fake player");
        }
        return pp;
    }

    private static String id(ServerPlayerPatch pp, SkillSlots slot) {
        var skill = pp.getSkill(slot).getSkill();
        return skill == null ? "none" : skill.getRegistryName().toString();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dodgeAndGuardGivenOnLogin(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        EfSkillPolicy.ensureBaseSkills(p);
        ServerPlayerPatch pp = patch(h, p);
        if (!id(pp, SkillSlots.DODGE).equals("epicfight:roll") || !id(pp, SkillSlots.GUARD).equals("epicfight:guard")) {
            h.fail("dodge=" + id(pp, SkillSlots.DODGE) + " guard=" + id(pp, SkillSlots.GUARD));
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void dodgeRestoredOnLogin(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        ServerPlayerPatch pp = patch(h, p);
        pp.getSkill(SkillSlots.DODGE).setSkill(EpicFightSkills.STEP.get());
        if (!id(pp, SkillSlots.DODGE).equals("epicfight:step")) {
            h.fail("precondition: step not set, got " + id(pp, SkillSlots.DODGE));
        }
        EfSkillPolicy.ensureBaseSkills(p);
        if (!id(pp, SkillSlots.DODGE).equals("epicfight:roll")) {
            h.fail("dodge not restored: " + id(pp, SkillSlots.DODGE));
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void skillbookBlocked(GameTestHelper h) {
        ServerPlayer p = fakePlayer(h);
        ServerPlayerPatch pp = patch(h, p);
        ItemStack book = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("epicfight:skillbook")));
        if (book.isEmpty() || !EfSkillPolicy.isBlockedSkillbook(book)) {
            h.fail("skillbook item not recognised");
        }
        if (EfSkillPolicy.isBlockedSkillbook(new ItemStack(net.minecraft.world.item.Items.STICK))) {
            h.fail("stick must not be blocked");
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, book);
        long before = pp.getPlayerSkills().listAcquiredSkills().count();
        PlayerInteractEvent.RightClickItem ev = new PlayerInteractEvent.RightClickItem(p, InteractionHand.MAIN_HAND);
        EfSkillPolicy.onRightClickItem(ev);
        if (!ev.isCanceled()) {
            h.fail("right click with skillbook was not cancelled");
        }
        if (pp.getPlayerSkills().listAcquiredSkills().count() != before) {
            h.fail("player acquired a skill");
        }
        h.succeed();
    }
}
