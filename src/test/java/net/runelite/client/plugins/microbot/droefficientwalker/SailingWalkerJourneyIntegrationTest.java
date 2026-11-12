package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises direct Walker calls, including the maintained facade's stale logical target. */
public class SailingWalkerJourneyIntegrationTest {
    private static final WorldPoint PORT = new WorldPoint(2686, 3162, 0);
    private static final WorldPoint BANK = new WorldPoint(2661, 3162, 0);

    public static final class FakeWalker {
        String status = "IDLE";
        WorldPoint raw, logical, corrected;
        boolean syntheticArrival;
        WorldPoint syntheticTarget;
        int cancellations;
        public String getStatus() { return syntheticArrival ? "ARRIVED" : status; }
        public String getNavigationPhase() { return status; }
        public String getPlanningFailure() { return "NONE"; }
        public WorldPoint getDestination() { return syntheticArrival ? syntheticTarget : raw; }
        WorldPoint getLogicalDestination() { return logical == null ? raw : logical; }
        public WorldPoint resolveDestination(WorldPoint requested) { return corrected == null ? requested : corrected; }
        public boolean walkTo(WorldPoint requested) {
            if (syntheticTarget != null && !syntheticTarget.equals(requested)) {
                syntheticArrival = false; syntheticTarget = null;
            }
            raw = requested; logical = requested; status = "PLANNING"; return true;
        }
        public boolean walkToNearestBank() {
            raw = BANK; status = "PLANNING";
            // Match the maintained facade: this request does not reset nearby recovery.
            return true;
        }
        public void cancel() {
            cancellations++; raw = null; logical = null; status = "IDLE";
            syntheticArrival = false; syntheticTarget = null;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final FakeWalker walker = new FakeWalker();
        final EfficientWalker backend = mock(EfficientWalker.class);
        final SailingWalkerJourney journey = new SailingWalkerJourney(backend, message -> { });
        final MockedStatic<Microbot> dro;
        Fixture() throws Exception {
            ClientThread thread = mock(ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                    Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));
            dro = mockStatic(Microbot.class);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            when(backend.getStatus()).thenAnswer(invocation -> EfficientWalker.Status.valueOf(walker.getStatus()));
            when(backend.getNavigationPhase()).thenAnswer(invocation -> walker.getNavigationPhase());
            when(backend.getDestination()).thenAnswer(invocation -> walker.getDestination());
            when(backend.getLogicalDestination()).thenAnswer(invocation -> walker.getLogicalDestination());
            when(backend.getPlanningFailure()).thenReturn(EfficientWalker.PlanningFailure.NONE);
            when(backend.resolveDestination(any())).thenAnswer(invocation -> walker.resolveDestination(invocation.getArgument(0)));
            when(backend.walkToWithoutBankSupplies(any())).thenAnswer(invocation -> walker.walkTo(invocation.getArgument(0)));
            when(backend.walkTo(any())).thenAnswer(invocation -> walker.walkTo(invocation.getArgument(0)));
            when(backend.walkToNearestBank()).thenAnswer(invocation -> walker.walkToNearestBank());
            doAnswer(invocation -> { walker.cancel(); return null; }).when(backend).cancel();
            journey.permit(() -> true);
        }
        @Override public void close() { dro.close(); }
    }

    @Test public void preparedLocalDockUsesExistingWalkerPolicyAndRetainsOwnership() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.journey.walkToPreparedLocal(PORT));
            verify(f.backend).walkToWithoutBankSupplies(PORT);
            verify(f.backend, never()).walkTo(any());
            assertTrue(f.journey.ownershipIntact(f.journey.observe()));
            f.journey.cancelOwned();
            assertEquals(1, f.walker.cancellations);
        }
    }
    @Test public void nearestDockSelectionDoesNotRunAWeightedRouteComparison() throws Exception {
        try(Fixture f=new Fixture()) {
            WorldPoint gateway=PortCatalog.landGateway("VOID_KNIGHTS_OUTPOST");
            EfficientWalker.CollisionProbe collision=mock(EfficientWalker.CollisionProbe.class);
            when(f.backend.navigationCollision()).thenReturn(collision);
            when(collision.standable(gateway)).thenReturn(true);
            SailingWalkerJourney.Estimate estimate=f.journey.estimateNearestDock(
                    PortCatalog.byKey("VOID_KNIGHTS_OUTPOST").sea,new WorldPoint(3167,3487,0),gateway,()->false);
            assertEquals(gateway,estimate.approach);
            assertEquals(0,estimate.cost);
            verify(f.backend,never()).navigationAdvisor(any());
            verify(f.backend,never()).walkTo(any());
        }
    }
    @Test public void aBlockedGatewayIsNotAcceptedAndCancelledSelectionDoesNoPlanning() throws Exception {
        try(Fixture f=new Fixture()) {
            WorldPoint gateway=PortCatalog.landGateway("VOID_KNIGHTS_OUTPOST");
            when(f.backend.navigationCollision()).thenReturn(mock(EfficientWalker.CollisionProbe.class));
            assertNull(f.journey.estimateNearestDock(PortCatalog.byKey("VOID_KNIGHTS_OUTPOST").sea,
                    BANK,gateway,()->false));
            clearInvocations(f.backend);
            try {
                f.journey.estimateNearestDock(PortCatalog.byKey("VOID_KNIGHTS_OUTPOST").sea,BANK,gateway,()->true);
                fail("Cancelled selection must stop before reading collision");
            } catch(java.util.concurrent.CancellationException expected) { }
            verify(f.backend,never()).navigationCollision();
        }
    }

    @Test public void adjustedDockArrivalThenNearestBankUsesTheNewEngineTarget() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.journey.walkTo(PORT));
            f.walker.raw = PORT.dx(-1); f.walker.status = "WALKING";
            assertEquals(PORT, f.journey.observe().destination);
            assertTrue(f.journey.ownershipIntact(f.journey.observe()));
            f.walker.raw = null; f.walker.status = "ARRIVED";
            assertTrue(f.journey.arrivedOwned(f.journey.observe(), PORT.dx(-1)));
            f.journey.releaseCompleted();

            assertTrue(f.journey.walkToBank());
            assertEquals(PORT, f.walker.getLogicalDestination(), "The bank request still exposes the stale dock logical target");
            assertEquals(BANK, f.journey.observe().destination);
            assertTrue(f.journey.ownershipIntact(f.journey.observe()));
            f.walker.raw = null; f.walker.status = "ARRIVED";
            assertTrue(f.journey.arrivedOwned(f.journey.observe(), BANK));
            assertFalse(f.journey.arrivedOwned(f.journey.observe(), PORT));
        }
    }

    @Test public void replacedBankJourneyCannotBeCancelledBySailing() throws Exception {
        try (Fixture f = new Fixture()) {
            f.walker.logical = PORT;
            assertTrue(f.journey.walkToBank());
            f.walker.raw = BANK.dx(10); f.walker.status = "WALKING";
            assertFalse(f.journey.ownershipIntact(f.journey.observe()));
            f.journey.cancelOwned();
            assertEquals(0, f.walker.cancellations);
            assertFalse(f.journey.ownsRoute());
        }
    }

    @Test public void correctedDestinationOwnsTheAcceptedTargetAndArrivalMayClearIt() throws Exception {
        try (Fixture f = new Fixture()) {
            f.walker.corrected = PORT.dx(2);
            assertTrue(f.journey.walkTo(PORT));
            assertEquals(f.walker.corrected, f.journey.observe().destination);
            assertTrue(f.journey.ownershipIntact(f.journey.observe()));
            f.walker.raw = null; f.walker.logical = null; f.walker.status = "ARRIVED";
            assertTrue(f.journey.arrivedOwned(f.journey.observe(), f.walker.corrected));
        }
    }

    @Test public void cancellingOwnedCompletedJourneyClearsSyntheticArrivalBeforeNearestBank() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.journey.walkTo(PORT));
            f.walker.syntheticArrival = true; f.walker.syntheticTarget = PORT;
            SailingWalkerJourney.JourneyState arrival = f.journey.observe();
            assertTrue(f.journey.arrivedOwned(arrival, PORT.dx(-1)));
            // Even a new engine bank target is hidden behind the facade's watchdog latch.
            f.walker.raw = BANK; f.walker.status = "PLANNING";
            assertEquals("ARRIVED", f.walker.getStatus());
            assertEquals(PORT, f.walker.getDestination());

            f.journey.cancelOwned();
            assertEquals(1, f.walker.cancellations);
            assertFalse(f.walker.syntheticArrival);
            assertFalse(f.journey.ownsRoute());
            assertTrue(f.journey.walkToBank());
            SailingWalkerJourney.JourneyState bank = f.journey.observe();
            assertEquals("PLANNING", bank.status);
            assertEquals(BANK, bank.destination);
            assertTrue(f.journey.ownershipIntact(bank));
        }
    }

    @Test public void replacementAfterArrivalObservationWinsOverCompletedJourneyCleanup() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.journey.walkTo(PORT));
            f.walker.syntheticArrival = true; f.walker.syntheticTarget = PORT;
            assertTrue(f.journey.arrivedOwned(f.journey.observe(), PORT));
            WorldPoint replacement = PORT.dx(10);
            // A caller starts a different journey after Sailing read ARRIVED.
            assertTrue(f.walker.walkTo(replacement));
            f.journey.cancelOwned();
            assertEquals(0, f.walker.cancellations);
            assertEquals(replacement, f.walker.getDestination());
            assertEquals("PLANNING", f.walker.getStatus());
            assertFalse(f.journey.ownsRoute());
        }
    }

    @Test public void existingUnownedWalkIsRejectedWithoutCancellingOrRetargeting() throws Exception {
        try (Fixture f = new Fixture()) {
            f.walker.walkTo(BANK);
            try { f.journey.walkTo(PORT); fail("Existing journey must retain ownership"); }
            catch (IllegalStateException expected) { }
            assertEquals(BANK, f.walker.raw);
            assertEquals(0, f.walker.cancellations);
            verify(f.backend, never()).walkTo(any());
        }
    }

    @Test public void revokedPermissionCannotStartLandOrBankTravel() throws Exception {
        try (Fixture f = new Fixture()) {
            f.journey.permit(() -> false);
            assertFalse(f.journey.walkTo(PORT));
            assertFalse(f.journey.walkToBank());
            verify(f.backend, never()).walkTo(any());
            verify(f.backend, never()).walkToNearestBank();
            assertFalse(f.journey.ownsRoute());
        }
    }
}
