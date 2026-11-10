package net.runelite.client.plugins.microbot.dropyramidplunder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.dropyramidplunder.PyramidPlunderAction.Result.*;

class PyramidPlunderActionTest {
    @Test void confirmedIdleActionDoesNotPayTheFailedClickRetryDelay() {
        PyramidPlunderAction action = new PyramidPlunderAction(0);
        assertEquals(COMPLETE, action.poll(600, false, true));
    }

    @Test void trapReceiptDoesNotReleaseChestWhileStillCrossing() {
        PyramidPlunderAction action = new PyramidPlunderAction(0);
        assertEquals(WAIT, action.poll(600, true, true));
        assertEquals(WAIT, action.poll(1800, true, false));
        assertEquals(WAIT, action.poll(2100, false, false));
        assertEquals(COMPLETE, action.poll(2400, false, false));
    }

    @Test void ladderWaitsThroughTravelAndArrivalAnimation() {
        PyramidPlunderAction action = new PyramidPlunderAction(0);
        assertEquals(WAIT, action.poll(1000, false, false));
        assertEquals(WAIT, action.poll(4000, true, false));
        assertEquals(WAIT, action.poll(6000, true, true));
        assertEquals(COMPLETE, action.poll(6600, false, true));
    }

    @Test void failedUrnCanRetryOnceIdleWithoutWaitingForFullTimeout() {
        PyramidPlunderAction action = new PyramidPlunderAction(0);
        assertEquals(WAIT, action.poll(600, true, false));
        assertEquals(RETRY, action.poll(1200, false, false));
    }

    @Test void missedClickGetsTwoTicksToStart() {
        PyramidPlunderAction action = new PyramidPlunderAction(0);
        assertEquals(WAIT, action.poll(600, false, false));
        assertEquals(RETRY, action.poll(1200, false, false));
    }

    @Test void continuouslyBusyActionWithoutReceiptHasABoundedTimeout() {
        PyramidPlunderAction action = new PyramidPlunderAction(0);
        assertEquals(WAIT, action.poll(29_999, true, false));
        assertEquals(TIMEOUT, action.poll(30_000, true, false));
    }

    @Test void receivedActionCompletesWhilePlayerStaysBusy() {
        // 2026-11-10 room 6: the sarcophagus opened, then the player stayed busy until the 30 s timeout.
        PyramidPlunderAction action = new PyramidPlunderAction(0);
        assertEquals(WAIT, action.poll(600, true, true));
        assertEquals(WAIT, action.poll(3_599, true, false));
        assertEquals(COMPLETE, action.poll(3_600, true, false));
    }

    @Test void unavailableObjectStateIsNotACompletionReceipt() {
        assertFalse(PyramidPlunderAction.changed(-1, 20946));
        assertFalse(PyramidPlunderAction.changed(20946, 20946));
        assertTrue(PyramidPlunderAction.changed(20947, 20946));
    }


    @Test void foodRestockBoundaryIncludesThreeAndEmptyInventory() {
        assertFalse(PyramidPlunderAction.needsFood(4, 3));
        assertTrue(PyramidPlunderAction.needsFood(3, 3));
        assertTrue(PyramidPlunderAction.needsFood(0, 3));
        assertTrue(PyramidPlunderAction.needsFood(0, 0));
    }
}
