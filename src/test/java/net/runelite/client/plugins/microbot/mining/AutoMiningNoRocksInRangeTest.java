package net.runelite.client.plugins.microbot.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMiningNoRocksInRangeTest {

    private static final long GRACE = AutoMiningScript.NO_ROCK_GRACE_MILLIS;

    @Test
    void warnsWhenNoRockHasBeenInRangeSinceTheStartWithProgressiveModeOff() {
        assertTrue(AutoMiningScript.shouldWarnNoRocksInRange(false, false, false, GRACE));
        assertTrue(AutoMiningScript.shouldWarnNoRocksInRange(false, false, false, GRACE * 10));
    }

    @Test
    void waitsOutTheGracePeriodBeforeWarning() {
        assertFalse(AutoMiningScript.shouldWarnNoRocksInRange(false, false, false, 0));
        assertFalse(AutoMiningScript.shouldWarnNoRocksInRange(false, false, false, GRACE - 1));
    }

    @Test
    void staysQuietWhileRocksItAlreadyMinedRespawn() {
        assertFalse(AutoMiningScript.shouldWarnNoRocksInRange(false, false, true, GRACE * 60));
    }

    @Test
    void staysQuietWhenARockIsFound() {
        assertFalse(AutoMiningScript.shouldWarnNoRocksInRange(false, true, false, GRACE));
    }

    @Test
    void progressiveModeWalksInsteadOfWarning() {
        assertFalse(AutoMiningScript.shouldWarnNoRocksInRange(true, false, false, GRACE));
    }

    @Test
    void messageTellsThePlayerWhatToDo() {
        assertEquals(
                "Auto Mining: no tin rocks within 20 tiles of where you started. Stop the script, stand next to the rocks and start it again, or turn on Progressive mode to let it walk to a mine for your Mining level.",
                AutoMiningScript.noRocksInRangeMessage("tin rocks", 20));
    }

    @Test
    void messageDoesNotSayZeroTiles() {
        assertEquals(
                "Auto Mining: no tin rocks next to the tile you started on. Stop the script, stand next to the rocks and start it again, or turn on Progressive mode to let it walk to a mine for your Mining level.",
                AutoMiningScript.noRocksInRangeMessage("tin rocks", 0));
    }

    @Test
    void statusOnlyNamesTheMineInProgressiveMode() {
        assertEquals("Mining tin rocks @ Lumbridge Swamp West Mine",
                AutoMiningScript.statusText("tin rocks", "Lumbridge Swamp West Mine", true, false));
        assertEquals("Mining tin rocks @ current area",
                AutoMiningScript.statusText("tin rocks", "Lumbridge Swamp West Mine", false, false));
        assertEquals("Mining tin rocks @ current area",
                AutoMiningScript.statusText("tin rocks", null, true, false));
    }

    @Test
    void statusSaysNoRocksInRangeInsteadOfMining() {
        assertEquals("No tin rocks in range - stand at the rocks or turn on Progressive mode",
                AutoMiningScript.statusText("tin rocks", "Lumbridge Swamp West Mine", false, true));
        assertEquals("Mining tin rocks @ Lumbridge Swamp West Mine",
                AutoMiningScript.statusText("tin rocks", "Lumbridge Swamp West Mine", true, true));
    }
}
