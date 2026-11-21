package net.runelite.client.plugins.microbot.dropyramidplunder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.client.plugins.microbot.dropyramidplunder.PyramidPlunderPlan.Step;
import org.junit.jupiter.api.Test;

class PyramidPlunderPlanTest {
    private static final double PER_URN = PyramidPlunderPlan.DEFAULT_SECONDS_PER_URN;

    @Test void everyTenLevelsFromTwentyOneOpensARoom() {
        assertEquals(0, PyramidPlunderPlan.maxRoom(20));
        assertEquals(1, PyramidPlunderPlan.maxRoom(21));
        assertEquals(1, PyramidPlunderPlan.maxRoom(30));
        assertEquals(2, PyramidPlunderPlan.maxRoom(31));
        assertEquals(6, PyramidPlunderPlan.maxRoom(71));
        assertEquals(7, PyramidPlunderPlan.maxRoom(90));
        assertEquals(8, PyramidPlunderPlan.maxRoom(91));
        assertEquals(8, PyramidPlunderPlan.maxRoom(99));
    }

    @Test void urnsAreLootedOnlyInTheLastTwoRooms() {
        assertFalse(PyramidPlunderPlan.urnRoom(6, 8));
        assertTrue(PyramidPlunderPlan.urnRoom(7, 8));
        assertTrue(PyramidPlunderPlan.urnRoom(8, 8));
        assertTrue(PyramidPlunderPlan.urnRoom(1, 1));
        assertTrue(PyramidPlunderPlan.urnRoom(1, 2));
        assertEquals(12, PyramidPlunderPlan.urnCount(3));
        assertEquals(13, PyramidPlunderPlan.urnCount(8));
        assertEquals(825, PyramidPlunderPlan.urnXp(8));
        assertEquals(550, PyramidPlunderPlan.chestXp(8));
    }

    @Test void sarcophagiStartAtRoomFourAndOnlyWhenEnabled() {
        assertFalse(PyramidPlunderPlan.opensSarcophagus(3, true));
        assertTrue(PyramidPlunderPlan.opensSarcophagus(4, true));
        assertTrue(PyramidPlunderPlan.opensSarcophagus(8, true));
        assertFalse(PyramidPlunderPlan.opensSarcophagus(8, false));
    }

    @Test void theTrapComesFirstAndTransitRoomsGoChestSarcophagusDoor() {
        double reserve = PyramidPlunderPlan.lastRoomReserveSeconds(8, PER_URN, true);
        assertEquals(Step.TRAP, PyramidPlunderPlan.next(5, 8, true, 250, reserve, PER_URN, 13, true, true));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(5, 8, false, 250, reserve, PER_URN, 13, true, true));
        assertEquals(Step.SARCOPHAGUS, PyramidPlunderPlan.next(5, 8, false, 250, reserve, PER_URN, 13, false, true));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(5, 8, false, 250, reserve, PER_URN, 13, false, false));
    }

    @Test void thePenultimateRoomLootsUrnsOnlyWhileTheLastRoomStaysAffordable() {
        double reserve = PyramidPlunderPlan.lastRoomReserveSeconds(8, PER_URN, true);
        assertEquals(Step.URN, PyramidPlunderPlan.next(7, 8, false, 190, reserve, PER_URN, 13, true, true));
        // Out of spare time: containers, then the door, with urns left behind.
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(7, 8, false, reserve + 20, reserve, PER_URN, 9, true, true));
        assertEquals(Step.SARCOPHAGUS, PyramidPlunderPlan.next(7, 8, false, reserve + 20, reserve, PER_URN, 9, false, true));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(7, 8, false, reserve + 5, reserve, PER_URN, 9, false, false));
        // All urns done early: containers and on.
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(7, 8, false, 200, reserve, PER_URN, 0, true, false));
    }

    @Test void theLastRoomKeepsContainersForTheEndButNeverLosesThemToTheTimer() {
        double reserve = PyramidPlunderPlan.lastRoomReserveSeconds(8, PER_URN, true);
        assertEquals(Step.URN, PyramidPlunderPlan.next(8, 8, false, 100, reserve, PER_URN, 13, true, true));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(8, 8, false, 18, reserve, PER_URN, 4, true, true));
        assertEquals(Step.SARCOPHAGUS, PyramidPlunderPlan.next(8, 8, false, 12, reserve, PER_URN, 4, false, true));
        assertEquals(Step.URN, PyramidPlunderPlan.next(8, 8, false, 3, reserve, PER_URN, 4, false, false));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(8, 8, false, 60, reserve, PER_URN, 0, true, true));
        assertEquals(Step.LEAVE, PyramidPlunderPlan.next(8, 8, false, 60, reserve, PER_URN, 0, false, false));
    }

    @Test void aSingleRoomAccountLootsItsOnlyRoom() {
        double reserve = PyramidPlunderPlan.lastRoomReserveSeconds(1, PER_URN, true);
        assertEquals(Step.URN, PyramidPlunderPlan.next(1, 1, false, 280, reserve, PER_URN, 13, true, false));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(1, 1, false, 200, reserve, PER_URN, 0, true, false));
        assertEquals(Step.LEAVE, PyramidPlunderPlan.next(1, 1, false, 200, reserve, PER_URN, 0, false, false));
    }

    @Test void theLastRoomReserveFollowsTheMeasuredUrnPaceWithinBounds() {
        // Recorded pace: 8 + 13 * 7.3 + 5 + 10 seconds.
        assertEquals(117.9, PyramidPlunderPlan.lastRoomReserveSeconds(8, PER_URN, true), 0.01);
        assertEquals(107.9, PyramidPlunderPlan.lastRoomReserveSeconds(8, PER_URN, false), 0.01);
        assertEquals(PyramidPlunderPlan.MIN_RESERVE_SECONDS, PyramidPlunderPlan.lastRoomReserveSeconds(8, 1, false), 0.01);
        assertEquals(PyramidPlunderPlan.MAX_RESERVE_SECONDS, PyramidPlunderPlan.lastRoomReserveSeconds(8, 30, true), 0.01);
        assertEquals(300, PyramidPlunderPlan.secondsLeft(0), 0.01);
        assertEquals(0, PyramidPlunderPlan.secondsLeft(520), 0.01);
    }

    @Test void urnPaceLearnsSlowlyAndIgnoresTooFewUrns() {
        assertEquals(PER_URN, PyramidPlunderPlan.learnSecondsPerUrn(PER_URN, 12, 3), 0.001);
        double slower = PyramidPlunderPlan.learnSecondsPerUrn(PER_URN, 130, 13);
        assertTrue(slower > PER_URN && slower < 10.0);
        double faster = PyramidPlunderPlan.learnSecondsPerUrn(PER_URN, 65, 13);
        assertTrue(faster < PER_URN && faster > 5.0);
    }
}
