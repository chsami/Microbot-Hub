package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.client.plugins.microbot.accountstarter.logic.FocusPlan;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import org.junit.jupiter.api.Test;

class FocusPlanTest {
    @Test
    void alreadyFocusedNeedsNothing() {
        assertFalse(FocusPlan.needed(ApTask.DEFENCE_5, 0, 7, 0));
        assertFalse(FocusPlan.needed(ApTask.MINING_5, 1, 13, 3));
    }

    @Test
    void wrongPathOrTaskOrNoneNeedsFocus() {
        assertTrue(FocusPlan.needed(ApTask.DEFENCE_5, 2047, 7, 0));
        assertTrue(FocusPlan.needed(ApTask.DEFENCE_5, 1, 7, 0));
        assertTrue(FocusPlan.needed(ApTask.DEFENCE_5, 0, 10, 0));
        assertTrue(FocusPlan.needed(ApTask.FISHING_5, 1, 1, 2));
        assertTrue(FocusPlan.needed(ApTask.FISHING_5, 0, 1, 1));
    }

    @Test
    void rowsStepTwelveFromEleven() {
        assertEquals(11, FocusPlan.rowIndex(ApTask.ATTACK_5));
        assertEquals(23, FocusPlan.rowIndex(ApTask.ATTACK_10));
        assertEquals(179, FocusPlan.rowIndex(ApTask.MAGIC_20));
        assertEquals(11, FocusPlan.rowIndex(ApTask.FISHING_5));
        assertEquals(35, FocusPlan.rowIndex(ApTask.MINING_5));
        assertEquals("Select Gatherer path", FocusPlan.selectAction(ApPath.GATHERER));
    }
}
