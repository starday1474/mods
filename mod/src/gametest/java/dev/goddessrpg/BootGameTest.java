package dev.goddessrpg;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GoddessRpg.MODID)
public class BootGameTest {
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void epicFightLoaded(GameTestHelper h) {
        if (!ModList.get().isLoaded("epicfight")) {
            h.fail("Epic Fight is not loaded");
        }
        h.succeed();
    }
}
