package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WalkerJourneyPolicyTest {
    private static final WorldPoint DOCK = new WorldPoint(2686, 3162, 0);

    @Test public void completedRouteMayClearDestinationBeforeSailingObservesArrival() {
        WorldPoint target = new WorldPoint(3173, 2368, 0);
        assertTrue(WalkerJourneyPolicy.arrivedOwned(false, target, null, "ARRIVED", target));
        assertFalse(WalkerJourneyPolicy.arrivedOwned(false, target, null, "ARRIVED", DOCK));
        assertFalse(WalkerJourneyPolicy.arrivedOwned(false, target, null, "ARRIVED", new WorldPoint(target.getX(), target.getY(), 1)));
    }

    @Test public void adjacentFallbackRemainsTheOriginalLogicalRequest() {
        WorldPoint fallback = DOCK.dx(-1).dy(-1);
        assertTrue(WalkerJourneyPolicy.ownershipIntact(false, DOCK, DOCK, "PLANNING"));
        assertTrue(WalkerJourneyPolicy.ownershipIntact(false, DOCK, DOCK, "WALKING"));
        assertTrue(WalkerJourneyPolicy.arrivedOwned(false, DOCK, null, "ARRIVED", fallback));
        assertFalse(WalkerJourneyPolicy.arrivedOwned(false, DOCK, null, "ARRIVED", DOCK.dx(3)));
    }

    @Test public void replacementIsRejectedEvenIfItsWalkerStateSaysArrived() {
        WorldPoint replacement = DOCK.dx(1);
        assertFalse(WalkerJourneyPolicy.ownershipIntact(false, DOCK, replacement, "WALKING"));
        assertFalse(WalkerJourneyPolicy.arrivedOwned(false, DOCK, replacement, "ARRIVED", DOCK));
        assertFalse(WalkerJourneyPolicy.ownershipIntact(false, DOCK, null, "PLANNING"));
        assertFalse(WalkerJourneyPolicy.ownershipIntact(false, DOCK, null, "IDLE"));
    }

    @Test public void bankCoordinatorCanBeBetweenLegsWithoutReclaimingAnIdleRoute() {
        assertTrue(WalkerJourneyPolicy.ownershipIntact(true, DOCK, null, "PLANNING"));
        assertTrue(WalkerJourneyPolicy.ownershipIntact(true, null, DOCK, "PLANNING"));
        assertFalse(WalkerJourneyPolicy.ownershipIntact(true, DOCK, DOCK.dx(1), "PLANNING"));
        assertFalse(WalkerJourneyPolicy.ownershipIntact(true, DOCK, null, "IDLE"));
    }

    @Test public void blockedNoneMeansRetryTransitionRatherThanDefinitivePlanningFailure() {
        assertTrue(WalkerJourneyPolicy.retryTransition("BLOCKED", "NONE"));
        assertFalse(WalkerJourneyPolicy.retryTransition("BLOCKED", "SEARCH_LIMIT"));
        assertFalse(WalkerJourneyPolicy.retryTransition("BLOCKED", "TARGET_NOT_STANDABLE"));
        assertFalse(WalkerJourneyPolicy.retryTransition("WALKING", "NONE"));
    }

    @Test public void gangplankCenterAndDisconnectedOrBlockedCandidatesCannotBeApproaches() {
        Map<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(DOCK, 1);
        reachable.put(DOCK.dx(-1), 4); // Closest, but collision planner rejects this tile.
        reachable.put(DOCK.dx(-1).dy(-1), 5);
        reachable.put(DOCK.dx(1).dy(-1), 8);
        reachable.put(DOCK.dx(2), 2);
        WorldPoint selected = WalkerJourneyPolicy.dockApproach(DOCK, reachable, p -> !p.equals(DOCK.dx(-1)));
        assertEquals(DOCK.dx(-1).dy(-1), selected);
        assertNotEquals(DOCK, selected);
        // The unlisted (1,0) tile is not locally reachable and cannot be selected.
        assertNotEquals(DOCK.dx(1), selected);
    }

    @Test public void noReachableStandableRingFailsWithoutRequestingTheObjectTile() {
        Map<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(DOCK, 0);
        reachable.put(DOCK.dx(5), 5);
        reachable.put(new WorldPoint(DOCK.getX() + 1, DOCK.getY(), 1), 1);
        assertNull(WalkerJourneyPolicy.dockApproach(DOCK, reachable, p -> true));
        reachable.put(DOCK.dx(1), 1);
        assertNull(WalkerJourneyPolicy.dockApproach(DOCK, reachable, p -> false));
    }
}
