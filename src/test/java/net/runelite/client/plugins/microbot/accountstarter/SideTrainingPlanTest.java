package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Collections;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.run.SideTraining;
import net.runelite.client.plugins.microbot.accountstarter.run.SideTraining.Next;
import org.junit.jupiter.api.Test;

class SideTrainingPlanTest {
    private static Choices choices(boolean bury, boolean fires, boolean cook) {
        return Choices.from(Preset.SKILLER, Collections.emptyMap(), bury, fires, cook);
    }

    @Test
    void logsWithATinderboxJustGoToTheBank() {
        Map<Integer, Integer> inv = Map.of(ItemID.BRONZE_AXE, 1, ItemID.TINDERBOX, 1, ItemID.LOGS, 26);
        assertEquals(Next.BANK, SideTraining.plan(inv, 0, choices(false, true, false), 1, false));
        assertEquals(Next.KEEP_GOING, SideTraining.plan(inv, 2, choices(false, true, false), 1, false));
    }

    @Test
    void noFireLightingStepExists() {
        for (Next n : Next.values()) assertNotEquals("LIGHT_FIRES", n.name());
    }

    @Test
    void rawShrimpsCookWhenAllowed() {
        Map<Integer, Integer> inv = Map.of(ItemID.NET, 1, ItemID.RAW_SHRIMP, 27);
        assertEquals(Next.COOK, SideTraining.plan(inv, 0, choices(false, false, true), 1, true));
        assertEquals(Next.BANK, SideTraining.plan(inv, 0, choices(false, false, true), 1, false));
        Map<Integer, Integer> anch = Map.of(ItemID.NET, 1, ItemID.RAW_ANCHOVIES, 27);
        assertEquals(Next.COOK, SideTraining.plan(anch, 0, choices(false, false, true), 1, true));
    }

    @Test
    void cookOffBanksFish() {
        Map<Integer, Integer> inv = Map.of(ItemID.NET, 1, ItemID.RAW_SHRIMP, 27);
        assertEquals(Next.BANK, SideTraining.plan(inv, 0, choices(true, true, false), 1, true));
    }

    @Test
    void bonesBuryOnlyWhenAllowed() {
        Map<Integer, Integer> inv = Map.of(ItemID.BONES, 28);
        assertEquals(Next.BURY, SideTraining.plan(inv, 0, choices(true, false, false), 1, false));
        assertEquals(Next.BANK, SideTraining.plan(inv, 0, choices(false, false, false), 1, false));
    }
}
