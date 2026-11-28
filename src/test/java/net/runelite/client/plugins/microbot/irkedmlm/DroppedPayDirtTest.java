package net.runelite.client.plugins.microbot.irkedmlm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.api.coords.WorldPoint;

/**
 * Pins when dropped pay-dirt is worth going back for.
 *
 * <p>Reported live: with the sack full the bot dropped the pay-dirt it was carrying, emptied the
 * sack, then walked off and left the pile on the floor. That is several minutes of mining abandoned,
 * and no player would do it. The rules below are what stop the fix turning into its own problem —
 * chasing a pile that has despawned, or one the run has already walked away from.
 */
class DroppedPayDirtTest {

    private static final WorldPoint BOX = new WorldPoint(3759, 5665, 0);
    private static final long T0 = 5_000_000L;

    @Test
    void aFreshPileIsWorthCollecting() {
        DroppedPayDirt d = new DroppedPayDirt();
        d.note(BOX, false, 27, T0);
        assertTrue(d.isPending(T0 + 1_000), "a pile just dropped must still be claimed");
        assertTrue(d.shouldCollect(T0 + 1_000, 27, true), "should go back for 27 pay-dirt at the drop site");
        assertEquals(27, d.getCount());
    }

    @Test
    void nothingDroppedMeansNothingToCollect() {
        DroppedPayDirt d = new DroppedPayDirt();
        assertTrue(!d.isPending(T0), "no drop recorded, nothing pending");
        assertTrue(!d.shouldCollect(T0, 28, true), "must not hunt for a pile that was never dropped");
        d.note(BOX, false, 0, T0); // a drop that moved nothing
        assertTrue(!d.isPending(T0), "a zero-item drop must not create a claim");
    }

    @Test
    void aDespawnedPileIsWrittenOff() {
        DroppedPayDirt d = new DroppedPayDirt();
        d.note(BOX, false, 27, T0);
        long late = T0 + DroppedPayDirt.CLAIM_TTL_MS + 1;
        assertTrue(d.isExpired(late), "past the TTL the pile has despawned");
        assertTrue(!d.shouldCollect(late, 27, true), "must not stand around waiting for a pile that is gone");
    }

    @Test
    void aPileIsNotChasedOnceWeHaveMovedOn() {
        // Walking back across the mine for 27 pay-dirt costs more than it's worth and reads as a bot.
        DroppedPayDirt d = new DroppedPayDirt();
        d.note(BOX, false, 27, T0);
        assertTrue(!d.shouldCollect(T0 + 1_000, 27, false), "must not walk back from the mining spot");
    }

    @Test
    void collectionNeedsSomewhereToPutIt() {
        DroppedPayDirt d = new DroppedPayDirt();
        d.note(BOX, false, 27, T0);
        assertTrue(!d.shouldCollect(T0 + 1_000, 0, true), "a full inventory cannot receive the pile");
    }

    @Test
    void aStuckCollectionGivesUp() {
        // Unreachable pile, or pickups that never register: the run must not stall here.
        DroppedPayDirt d = new DroppedPayDirt();
        d.note(BOX, false, 27, T0);
        d.beginCollecting(T0 + 500);
        assertTrue(!d.collectTimedOut(T0 + 5_000), "gave up far too early");
        long stuck = T0 + 500 + DroppedPayDirt.COLLECT_TIMEOUT_MS + 1;
        assertTrue(d.collectTimedOut(stuck), "collection never timed out — the run would hang here");
        assertTrue(!d.shouldCollect(stuck, 27, true), "a timed-out collection must stop being attempted");
    }

    @Test
    void aSecondDropReplacesTheClaim() {
        DroppedPayDirt d = new DroppedPayDirt();
        d.note(BOX, false, 27, T0);
        WorldPoint elsewhere = new WorldPoint(3748, 5672, 0);
        d.note(elsewhere, true, 12, T0 + 60_000);
        assertEquals(12, d.getCount());
        assertEquals(elsewhere, d.getWhere());
        // and the TTL restarts from the newer drop, not the old one
        assertTrue(d.isPending(T0 + 60_000 + 10_000), "the newer claim must be live");
    }

    /**
     * The reported failure: pay-dirt was dropped upstairs, the sack was emptied downstairs, and the
     * bot then stood on the lower floor spam-clicking a pile it could only reach by ladder.
     *
     * <p>Both MLM levels are the same plane at different tile heights, so the upstairs pile is only
     * about twelve world tiles from the downstairs deposit box — near enough that every WorldPoint
     * distance check says "you're standing next to it". The floor has to be carried explicitly with
     * the claim, because no coordinate comparison can recover it.
     */
    @Test
    void theFloorThePileIsOnIsRemembered() {
        DroppedPayDirt upstairs = new DroppedPayDirt();
        upstairs.note(new WorldPoint(3755, 5677, 0), true, 27, T0);
        assertTrue(upstairs.isOnUpperFloor(), "an upstairs drop must be recorded as upstairs");

        DroppedPayDirt downstairs = new DroppedPayDirt();
        downstairs.note(BOX, false, 27, T0);
        assertTrue(!downstairs.isOnUpperFloor(), "a downstairs drop must be recorded as downstairs");

        // The trap this guards: the upstairs pile is close enough to the deposit box that proximity
        // alone would green-light collection from the wrong level.
        assertTrue(BOX.distanceTo(new WorldPoint(3755, 5677, 0)) <= 12,
                "the two levels really are within proximity range of each other — the floor flag is the "
                        + "only thing separating them");
        assertTrue(upstairs.shouldCollect(T0 + 1_000, 27, true),
                "proximity says yes; the caller's floor check is what must say no");

        downstairs.clear();
        assertTrue(!downstairs.isOnUpperFloor(), "clear() must reset the floor with the claim");
    }

    @Test
    void moreIsDroppedOnAPileAlreadyWaitingNearby() {
        DroppedPayDirt d = new DroppedPayDirt();
        WorldPoint near = new WorldPoint(BOX.getX() + 4, BOX.getY(), 0);
        WorldPoint far = new WorldPoint(BOX.getX() + 30, BOX.getY(), 0);
        assertEquals(near, d.dropTile(near, false, 10, T0), "nothing waiting: right here");
        d.note(BOX, false, 10, T0);
        assertEquals(BOX, d.dropTile(near, false, 10, T0 + 1_000));
        assertEquals(far, d.dropTile(far, false, 10, T0 + 1_000), "too far to walk back");
        assertEquals(near, d.dropTile(near, true, 10, T0 + 1_000), "other floor");
        assertEquals(near, d.dropTile(near, false, 10, T0 + DroppedPayDirt.CLAIM_TTL_MS + 1), "despawned");
    }

    @Test
    void aDropAddsToTheClaimOnlyOnItsOwnTileAndFloor() {
        DroppedPayDirt d = new DroppedPayDirt();
        WorldPoint elsewhere = new WorldPoint(3748, 5672, 0);
        assertTrue(d.canAdd(BOX, false, T0), "no claim yet");
        d.add(BOX, false, 10, T0);
        assertTrue(d.canAdd(BOX, false, T0 + 1_000));
        assertTrue(!d.canAdd(elsewhere, false, T0 + 1_000), "would leave a second, unclaimed pile");
        assertTrue(!d.canAdd(BOX, true, T0 + 1_000), "same tile, other floor");
        d.add(BOX, false, 1, T0 + 1_000);
        assertEquals(11, d.getCount());
        assertTrue(d.canAdd(elsewhere, false, T0 + 1_000 + DroppedPayDirt.CLAIM_TTL_MS + 1), "old claim gone");
    }
}
