package net.runelite.client.plugins.microbot.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMiningProgressiveLeashTest {

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
    void walksToTheAnchorWhenNothingIsReachableAndThePlayerIsFarFromIt() {
        assertTrue(AutoMiningScript.shouldReturnToAnchor(true, false, 4));
        assertTrue(AutoMiningScript.shouldReturnToAnchor(true, false, 20));
    }

    @Test
    void staysPutOnceWithinArrivalDistanceSoTheWalkCannotRepeat() {
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, false, 3));
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, false, 0));
    }

    @Test
    void neverInterruptsARockTheScriptSelected() {
        assertFalse(AutoMiningScript.shouldReturnToAnchor(true, true, 15));
    }

    @Test
    void doesNotApplyOutsideProgressiveMode() {
        assertFalse(AutoMiningScript.shouldReturnToAnchor(false, false, 15));
    }
}
