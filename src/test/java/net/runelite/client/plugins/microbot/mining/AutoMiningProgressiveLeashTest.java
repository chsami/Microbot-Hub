package net.runelite.client.plugins.microbot.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMiningProgressiveLeashTest {

    private static final long GRACE = AutoMiningScript.NO_ROCK_GRACE_MILLIS;

    @Test
    void leashFollowsDistanceToStrayInsteadOfStoppingAtFiveTiles() {
        assertEquals(20, AutoMiningScript.progressiveLeash(20));
        assertEquals(6, AutoMiningScript.progressiveLeash(6));
        assertEquals(5, AutoMiningScript.progressiveLeash(5));
    }

    @Test
    void leashIsAtLeastOneTile() {
        assertEquals(1, AutoMiningScript.progressiveLeash(0));
        assertEquals(1, AutoMiningScript.progressiveLeash(-4));
    }

    @Test
    void walksToTheAnchorWhenNothingHasBeenReachableForAWhileAndThePlayerIsFarFromIt() {
        assertTrue(AutoMiningScript.shouldReturnToAnchor(true, false, false, GRACE, 4));
        assertTrue(AutoMiningScript.shouldReturnToAnchor(true, false, false, GRACE * 3, 20));
    }

    @Test
    void ignoresAShortGapWithNoRock() {
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, false, false, 0, 15));
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, false, false, GRACE - 1, 15));
    }

    @Test
    void staysAtTheRocksWhileTheyRespawnOnceItHasMinedHere() {
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, false, true, GRACE, 4));
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, false, true, GRACE * 60, 17));
    }

    @Test
    void staysPutOnceWithinArrivalDistanceSoTheWalkCannotRepeat() {
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, false, false, GRACE, 3));
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, false, false, GRACE, 0));
    }

    @Test
    void neverInterruptsARockTheScriptSelected() {
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, true, false, GRACE, 15));
    }

    @Test
    void doesNotApplyOutsideProgressiveMode() {
        assertFalse(AutoMiningScript.shouldReturnToAnchor(false, false, false, GRACE, 15));
    }
}
