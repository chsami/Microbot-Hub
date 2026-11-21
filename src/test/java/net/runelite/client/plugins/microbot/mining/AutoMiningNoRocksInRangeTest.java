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
    void staysQuietWhileRocksItAlreadyFoundRespawn() {
        assertFalse(AutoMiningScript.shouldWarnNoRocksInRange(false, false, true, GRACE * 60));
    }

    @Test
    void staysQuietAfterABankTripWhenTheRocksItFoundEarlierAreMinedOut() {
        boolean rockSeenAtAnchor = true;
        long noRockMillisAfterBankTrip = GRACE;
        assertFalse(AutoMiningScript.shouldWarnNoRocksInRange(false, false, rockSeenAtAnchor, noRockMillisAfterBankTrip));
        assertFalse(AutoMiningScript.shouldWarnNoRocksInRange(false, false, rockSeenAtAnchor, noRockMillisAfterBankTrip * 60));
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
                "Auto Mining: no tin rocks to mine within 20 tiles of where you started. If the rocks here are just mined out, it will carry on when they respawn. If there are none nearby, stop the script, stand next to the rocks and start it again, or turn on Progressive mode to let it walk to a mine for your Mining level.",
                AutoMiningScript.noRocksInRangeMessage("tin rocks", 20));
    }

    @Test
    void messageNamesTheDistanceToStraySettingInsteadOfSayingZeroTiles() {
        assertEquals(
                "Auto Mining: no tin rocks to mine next to the tile you started on (Distance to Stray is 0). If the rocks here are just mined out, it will carry on when they respawn. If there are none nearby, stop the script, stand next to the rocks and start it again, or turn on Progressive mode to let it walk to a mine for your Mining level.",
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
    void statusSaysItIsWaitingWithNoRocksInRangeInsteadOfMining() {
        assertEquals("Waiting for tin rocks - none in range. Stand at the rocks or turn on Progressive mode",
                AutoMiningScript.statusText("tin rocks", "Lumbridge Swamp West Mine", false, true));
        assertEquals("Mining tin rocks @ Lumbridge Swamp West Mine",
                AutoMiningScript.statusText("tin rocks", "Lumbridge Swamp West Mine", true, true));
    }
}
