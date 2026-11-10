package net.runelite.client.plugins.microbot.dropyramidplunder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.client.plugins.microbot.dropyramidplunder.PyramidPlunderPlan.Step;
import org.junit.jupiter.api.Test;

/** Better XP mode (on by default) and the quicker urn settle used in both modes. */
class PyramidPlunderXpModeTest {
    private static final double PER_URN = PyramidPlunderPlan.DEFAULT_SECONDS_PER_URN;
    private static final double NONE = Double.POSITIVE_INFINITY;

    @Test void betterXpModeIsOnByDefault() {
        assertTrue(new PyramidPlunderConfig() { }.betterXpMode());
    }

    @Test void urnsOnlyInRoomsSevenAndEightAndNoSarcophagi() {
        for (int room = 1; room <= 8; room++) {
            assertEquals(room >= 7, PyramidPlunderPlan.urnRoom(room, 8, true), "urns room " + room);
            assertFalse(PyramidPlunderPlan.opensSarcophagus(room, 8, true, true), "sarcophagus room " + room);
        }
        assertFalse(PyramidPlunderPlan.opensSarcophagus(7, 7, true, true));
    }

    @Test void offKeepsTheNormalPlan() {
        assertTrue(PyramidPlunderPlan.opensSarcophagus(4, 8, true, false));
        assertFalse(PyramidPlunderPlan.urnRoom(6, 8, false));
        assertTrue(PyramidPlunderPlan.urnRoom(7, 8, false));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(5, 8, false, false, 250, 100, PER_URN, 13, true, true, NONE));
        assertEquals(Step.SARCOPHAGUS, PyramidPlunderPlan.next(5, 8, false, false, 250, 100, PER_URN, 13, false, true, NONE));
    }

    @Test void belowRoomSevenTheNormalRoomsAreUsed() {
        assertFalse(PyramidPlunderPlan.xpRooms(6, true));
        assertTrue(PyramidPlunderPlan.urnRoom(5, 6, true));
        assertTrue(PyramidPlunderPlan.opensSarcophagus(4, 6, true, true), "no room 7: the normal sarcophagus rule");
    }

    @Test void earlyRoomsSearchTheChestThenMoveOn() {
        // Room 3 in Better XP mode: the Grand Gold Chest is searched, the urns and sarcophagus are not.
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(3, 8, true, false, 280, 120, PER_URN, 12, true, false, NONE));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(3, 8, true, false, 275, 120, PER_URN, 12, false, false, NONE));
    }

    @Test void roomSevenGetsNinetySecondsForItsUrns() {
        assertEquals(90, PyramidPlunderPlan.roomUrnSecondsLeft(7, 8, true, 0), 0.001);
        assertEquals(30, PyramidPlunderPlan.roomUrnSecondsLeft(7, 8, true, 60), 0.001);
        assertEquals(0, PyramidPlunderPlan.roomUrnSecondsLeft(7, 8, true, 95), 0.001);
        assertEquals(NONE, PyramidPlunderPlan.roomUrnSecondsLeft(8, 8, true, 200), 0.001, "the last room loots until the timer");
        assertEquals(NONE, PyramidPlunderPlan.roomUrnSecondsLeft(7, 7, true, 200), 0.001, "room 7 as the last room");
        assertEquals(NONE, PyramidPlunderPlan.roomUrnSecondsLeft(7, 8, false, 200), 0.001);

        double reserve = PyramidPlunderPlan.lastRoomReserveSeconds(8, PER_URN, true, true);
        assertEquals(Step.URN, PyramidPlunderPlan.next(7, 8, true, false, 280, reserve, PER_URN, 9, true, false, 30));
        // Out of room 7 time: the remaining urns are skipped, the chest is still searched, then on to room 8.
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(7, 8, true, false, 280, reserve, PER_URN, 5, true, false, 0));
        assertEquals(Step.DOOR, PyramidPlunderPlan.next(7, 8, true, false, 275, reserve, PER_URN, 5, false, false, 0));
        // Room 7 always gets its urn time, even when the room 8 reserve would have stopped it (normal mode)...
        assertEquals(Step.URN, PyramidPlunderPlan.next(7, 8, true, false, reserve + 5, reserve, PER_URN, 9, true, false, 80));
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(7, 8, false, false, reserve + 5, reserve, PER_URN, 9, true, false, NONE));
        // ...and only the run timer itself ends it early.
        assertEquals(Step.CHEST, PyramidPlunderPlan.next(7, 8, true, false, 15, reserve, PER_URN, 9, true, false, 80));
        // Room 8 empties every urn.
        assertEquals(Step.URN, PyramidPlunderPlan.next(8, 8, true, false, 40, reserve, PER_URN, 3, false, false, NONE));
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
