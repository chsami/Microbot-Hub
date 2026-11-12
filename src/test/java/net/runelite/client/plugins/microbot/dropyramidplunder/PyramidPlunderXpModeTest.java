package net.runelite.client.plugins.microbot.dropyramidplunder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.client.plugins.microbot.dropyramidplunder.PyramidPlunderPlan.Step;
import org.junit.jupiter.api.Test;

/** Better XP mode (on by default) and the quicker urn settle used in both modes. */
class PyramidPlunderXpModeTest {
    private static final double PER_URN = PyramidPlunderPlan.DEFAULT_SECONDS_PER_URN;

    @Test void betterXpModeIsOnAndSarcophagiAreOffByDefault() {
        PyramidPlunderConfig defaults = new PyramidPlunderConfig() { };
        assertTrue(defaults.betterXpMode());
        assertFalse(defaults.openSarcophagi());
    }

    @Test void urnsOnlyInTheLastTwoRoomsAndNoSarcophagi() {
        for (int room = 1; room <= 8; room++) {
            assertEquals(room >= 7, PyramidPlunderPlan.urnRoom(room, 8), "urns room " + room);
            assertFalse(PyramidPlunderPlan.opensSarcophagus(room, true, true), "sarcophagus room " + room);
        }
        assertEquals(7, PyramidPlunderPlan.firstUrnRoom(8));
    }

    @Test void offKeepsTheNormalPlan() {
        assertTrue(PyramidPlunderPlan.opensSarcophagus(4, true, false));
        assertFalse(PyramidPlunderPlan.opensSarcophagus(4, false, false));
        assertFalse(PyramidPlunderPlan.urnRoom(6, 8));
        assertTrue(PyramidPlunderPlan.urnRoom(7, 8));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(5, 8, false, false, 250, 100, PER_URN, 13, true, true));
        assertEquals(Step.SARCOPHAGUS, PyramidPlunderPlan.next(5, 8, false, false, 250, 100, PER_URN, 13, false, true));
    }

    @Test void theUrnRoomsFollowTheThievingLevel() {
        // 71-80 Thieving reaches room 6: rooms 5 and 6 behave as rooms 7 and 8 do at 91.
        int maxRoom = PyramidPlunderPlan.maxRoom(75);
        assertEquals(6, maxRoom);
        assertEquals(5, PyramidPlunderPlan.firstUrnRoom(maxRoom));
        assertFalse(PyramidPlunderPlan.opensSarcophagus(5, true, true));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(4, maxRoom, true, false, 250, 100, PER_URN, 13, true, false));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(4, maxRoom, true, false, 245, 100, PER_URN, 13, false, false));
        assertEquals(Step.URN, PyramidPlunderPlan.next(5, maxRoom, true, false, 200, 100, PER_URN, 13, true, false));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(5, maxRoom, true, false, 80, 100, PER_URN, 6, true, false));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(5, maxRoom, true, false, 76, 100, PER_URN, 6, false, false));
        assertEquals(Step.URN, PyramidPlunderPlan.next(6, maxRoom, true, false, 40, 100, PER_URN, 3, false, false));
        // One reachable room is the last room; two are rooms 1 and 2.
        assertEquals(1, PyramidPlunderPlan.firstUrnRoom(1));
        assertEquals(Step.URN, PyramidPlunderPlan.next(1, 1, true, false, 40, 100, PER_URN, 3, false, false));
        assertEquals(Step.URN, PyramidPlunderPlan.next(1, 2, true, false, 200, 100, PER_URN, 9, true, false));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(1, 2, true, false, 80, 100, PER_URN, 9, true, false));
    }

    @Test void earlyRoomsSearchTheChestThenMoveOn() {
        // Room 3 in Better XP mode: the Grand Gold Chest is searched, the urns and sarcophagus are not.
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(3, 8, true, false, 280, 120, PER_URN, 12, true, false));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(3, 8, true, false, 275, 120, PER_URN, 12, false, false));
    }

    @Test void roomSevenIsLeftWithAboutOneFifteenToOneTwentyOnTheTimer() {
        double reserve = PyramidPlunderPlan.lastRoomReserveSeconds(8, PER_URN, false, true);
        double chest = PyramidPlunderPlan.CHEST_SECONDS;
        // Another urn only while it and the chest still leave 1:15.
        assertEquals(Step.URN, PyramidPlunderPlan.next(7, 8, true, false, 75 + chest + PER_URN + 1, reserve, PER_URN, 9, true, false));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(7, 8, true, false, 75 + chest + PER_URN - 1, reserve, PER_URN, 9, true, false));
        // At a learned four seconds an urn the doors are started between 1:15 and 1:19.
        assertEquals(Step.URN, PyramidPlunderPlan.next(7, 8, true, false, 79.5, reserve, 4, 9, false, false));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(7, 8, true, false, 79, reserve, 4, 9, false, false));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(7, 8, true, false, 75, reserve, PER_URN, 9, false, false));
        // The room 8 reserve of the normal plan does not apply: it would have left much earlier.
        assertEquals(Step.URN, PyramidPlunderPlan.next(7, 8, true, false, reserve + 5, reserve, PER_URN, 9, true, false));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(7, 8, false, false, reserve + 5, reserve, PER_URN, 9, true, false));
        // Every urn looted early: the chest and on.
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(7, 8, true, false, 150, reserve, PER_URN, 0, true, false));
        // Room 8 empties every urn until the timer.
        assertEquals(Step.URN, PyramidPlunderPlan.next(8, 8, true, false, 40, reserve, PER_URN, 3, false, false));
        // Room 7 as the last room (81-90 Thieving) does too.
        assertEquals(Step.URN, PyramidPlunderPlan.next(7, 7, true, false, 40, reserve, PER_URN, 3, false, false));
    }

    @Test void anExperienceDropCompletesTheUrnAndReleasesTheNextOneQuickly() {
        assertTrue(PyramidPlunderAction.xpGained(1_000_825, 1_000_000));
        assertFalse(PyramidPlunderAction.xpGained(1_000_000, 1_000_000));
        assertFalse(PyramidPlunderAction.xpGained(1_000_000, 0), "no baseline read");
        // The next urn skips the working pause only straight after a loot.
        assertTrue(PyramidPlunderAction.chained(10_300, 10_000));
        assertFalse(PyramidPlunderAction.chained(10_000 + PyramidPlunderAction.URN_CHAIN_MS, 10_000));
        assertFalse(PyramidPlunderAction.chained(10_300, 0), "nothing looted yet");
    }

    @Test void lootedUrnCompletesAtOnceAndAFailedSearchRetriesSooner() {
        PyramidPlunderAction urn = new PyramidPlunderAction(0, true);
        assertEquals(PyramidPlunderAction.Result.WAIT, urn.poll(300, true, false));
        assertEquals(PyramidPlunderAction.Result.COMPLETE, urn.poll(400, true, true), "no settle wait once looted");

        PyramidPlunderAction failed = new PyramidPlunderAction(0, true);
        assertEquals(PyramidPlunderAction.Result.WAIT, failed.poll(1000, true, false));
        assertEquals(PyramidPlunderAction.Result.WAIT, failed.poll(1200, false, false));
        assertEquals(PyramidPlunderAction.Result.RETRY, failed.poll(1300, false, false));

        // Other actions keep the original 600 ms settle.
        PyramidPlunderAction chest = new PyramidPlunderAction(0);
        assertEquals(PyramidPlunderAction.Result.WAIT, chest.poll(400, true, true));
        assertEquals(PyramidPlunderAction.Result.WAIT, chest.poll(900, false, true));
        assertEquals(PyramidPlunderAction.Result.COMPLETE, chest.poll(1000, false, true));
    }
}
