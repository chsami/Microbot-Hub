package net.runelite.client.plugins.microbot.irkedmlm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMStatus;
import net.runelite.client.plugins.microbot.irkedmlm.session.SessionSnapshot;

/**
 * Pins SessionSnapshot's positional constructor. Every field is an int or long of the same type, so a
 * reordered argument in the script's updateSnapshot() would compile cleanly and silently draw the wrong
 * number in the overlay. Distinct values per slot make that impossible to miss.
 */
class SessionSnapshotTest {

    @Test
    void constructorArgumentsMapToTheExpectedFields() {
        SessionSnapshot snap = new SessionSnapshot(
                MLMStatus.MINING,
                MLMMiningSpot.WEST_LOWER,
                11, 12, false,
                13L,
                14L, 15, 16, 17,
                21, 22, 23, 24, 25,
                "Mining Vein",
                31L, 32L);

        assertEquals(MLMStatus.MINING, snap.getStatus());
        assertEquals(MLMMiningSpot.WEST_LOWER, snap.getMiningSpot());
        assertEquals(11, snap.getSackCount());
        assertEquals(12, snap.getMaxSackSize());
        assertEquals(13L, snap.getStartTimeMs());
        assertEquals(14L, snap.getTotalValueGained());
        assertEquals(15, snap.getGainedNuggets());
        assertEquals(16, snap.getStartXp());
        assertEquals(17, snap.getCurrentXp());
        assertEquals(21, snap.getRuniteCount());
        assertEquals(22, snap.getAdamantiteCount());
        assertEquals(23, snap.getMithrilCount());
        assertEquals(24, snap.getGoldCount());
        assertEquals(25, snap.getCoalCount());
        assertEquals("Mining Vein", snap.getSubStateLabel());
        assertEquals(31L, snap.getStatusEnteredMs());
        assertEquals(32L, snap.getLastOreMs());
    }

    @Test
    void emptySnapshotIsUsableBeforeTheScriptStarts() {
        SessionSnapshot empty = SessionSnapshot.empty();

        assertEquals(MLMStatus.IDLE, empty.getStatus());
        assertEquals(108, empty.getMaxSackSize());
        // UNKNOWN, not 0: an un-started snapshot has not observed the sack, and rendering 0 would
        // read as "sack empty" — the conflation the sack-state rework removed.
        assertEquals(SackState.UNKNOWN, empty.getSackCount());
        assertEquals("Idle", empty.getSubStateLabel());
        assertTrue(empty.getStartTimeMs() > 0, "runtime must not render as a negative duration");
        assertTrue(empty.getStatusEnteredMs() > 0, "phase timer must not render as a negative duration");
        assertEquals(0L, empty.getLastOreMs());
    }
}
