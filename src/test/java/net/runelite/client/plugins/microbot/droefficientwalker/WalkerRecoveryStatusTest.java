package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Public facade receipts while recovery owns an intermediate destination. */
class WalkerRecoveryStatusTest
{
    private static final WorldPoint TARGET = HouseLocation.PRIFDDINAS.getPortalLocation().dx(2);

    @Test void intermediateBankArrivalRemainsPlanningAcrossPublicReads() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.bankState("OPENING_BANK");
            set(f.base.coordinator, "pendingTarget", TARGET);
            f.engineStatus("ARRIVED");
            assertEquals(EfficientWalker.Status.PLANNING, f.walker.getStatus());
            assertEquals(TARGET, f.walker.getLogicalDestination());
            assertEquals(EfficientWalker.Status.PLANNING, f.walker.getStatus());
            f.bankState("WAITING_FINAL_PREVIEW");
            assertEquals(EfficientWalker.Status.PLANNING, f.walker.getStatus());
        }
    }

    @Test void terminalBankFailureRemainsBlocked() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            // The coordinator has completed/cleared its detour; this is a terminal receipt.
            f.bankState("IDLE");
            f.engineStatus("BLOCKED");
            assertEquals(EfficientWalker.Status.BLOCKED, f.walker.getStatus());
            // An explicit supply failure also wins while the coordinator is still busy.
            f.bankState("OPENING_BANK");
            set(f.walker, "caveLightFailure", "Required bank supplies unavailable");
            assertEquals(EfficientWalker.Status.BLOCKED, f.walker.getStatus());
        }
    }

    @Test void freshSameTargetPreviewClearsSyntheticArrivalAndDuplicateKeepsTicket() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.arrived();
            assertTrue(f.walker.previewRouteTo(TARGET));
            assertNull(get(f.watchdog, "syntheticDestination"));
            assertEquals(EfficientWalker.Status.PLANNING, f.walker.getStatus());
            WalkerRouteSubmission.Ticket ticket = f.submissions.pending();
            assertNotNull(ticket);
            assertTrue(f.walker.previewRouteTo(TARGET));
            assertSame(ticket, f.submissions.pending());
        }
    }

    @Test void freshSuppliedRouteClearsSameTargetSyntheticArrival() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.arrived();
            assertTrue(f.walker.followSuppliedRoute(List.of(TARGET)));
            assertNull(get(f.watchdog, "syntheticDestination"));
            assertEquals(EfficientWalker.Status.PLANNING, f.walker.getStatus());
            assertEquals("followSuppliedRoute", f.submissions.pending().command);
        }
    }

    @Test void repeatedCompletedWalkRetainsAcceptedAdjacentArrival() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.arrived();
            assertTrue(f.walker.walkTo(TARGET));
            assertTrue(f.walker.walkTo(TARGET));
            assertEquals(TARGET, get(f.watchdog, "syntheticDestination"));
            assertEquals(EfficientWalker.Status.ARRIVED, f.walker.getStatus());
            assertNull(f.submissions.pending());
        }
    }

    @Test void replacementOwnerAndCancellationDiscardStaleArrival() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.arrived();
            long owner = f.walker.startOwnedWalk(TARGET, "recovery-status-test");
            assertTrue(owner > 0);
            assertNull(get(f.watchdog, "syntheticDestination"));
            assertEquals(EfficientWalker.Status.PLANNING, f.walker.getStatus());
            f.walker.cancel();
            assertNull(get(f.watchdog, "syntheticDestination"));
            assertNull(f.submissions.pending());
            assertEquals(EfficientWalker.Status.IDLE, f.walker.getStatus());
            assertNull(f.walker.getDestination());
        }
    }

    @Test void settledCurrentBlockedBankPreviewStartsSupplyAnalysisOnce() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.blockedBankPreview(TARGET);
            java.util.concurrent.ExecutorService worker = mock(java.util.concurrent.ExecutorService.class);
            when(worker.submit(any(java.util.concurrent.Callable.class)))
                .thenReturn(new java.util.concurrent.CompletableFuture<>());
            set(f.base.coordinator, "bankExecutor", worker);
            call(f.base.coordinator, "finishBankReplanIfReady");
            assertEquals("ANALYZING_TRANSPORT_REQUIREMENTS", get(f.base.coordinator, "state").toString());
            assertEquals(true, get(f.base.coordinator, "transportRequirementsChecked"));
            verify(worker, times(1)).submit(any(java.util.concurrent.Callable.class));
        }
    }

    @Test void queuedOrDifferentDestinationBlockedBankPreviewDoesNotProbeSupplies() throws Exception
    {
        try (Fixture f = new Fixture())
        {
            f.blockedBankPreview(TARGET.dx(10));
            call(f.base.coordinator, "finishBankReplanIfReady");
            assertEquals("WAITING_BANK_REPLAN", get(f.base.coordinator, "state").toString());
            assertEquals(false, get(f.base.coordinator, "transportRequirementsChecked"));
            f.blockedBankPreview(TARGET);
            f.submissions.submit(0, "previewRouteTo", TARGET, ticket -> {});
            call(f.base.coordinator, "finishBankReplanIfReady");
            assertEquals("WAITING_BANK_REPLAN", get(f.base.coordinator, "state").toString());
            assertEquals(false, get(f.base.coordinator, "transportRequirementsChecked"));
        }
    }

    private static final class Fixture implements AutoCloseable
    {
        final HousePortalRecoveryTest.Fixture base = new HousePortalRecoveryTest.Fixture();
        final EfficientWalker walker = base.walker;
        final Object watchdog = get(walker, "finalTileWatchdog");
        final WalkerRouteSubmission submissions;

        Fixture() throws Exception
        {
            base.inside.set(false);
            base.raw.set(TARGET.dx(-1));
            base.cached.set(TARGET.dx(-1));
            base.house.when(MaxPohSupport::enabled).thenReturn(false);
            WalkerRouteSubmission.Scheduler scheduler = mock(WalkerRouteSubmission.Scheduler.class);
            when(scheduler.schedule(any(Runnable.class), anyInt())).thenReturn(() -> {});
            submissions = new WalkerRouteSubmission(scheduler, () -> 15, Runnable::run);
            ((WalkerRouteSubmission) get(walker, "routeSubmission")).close();
            set(walker, "routeSubmission", submissions);
            WalkerRequestOwnership ownership = (WalkerRequestOwnership) get(walker, "requestOwnership");
            WalkerRequestOwnership.Request request = ownership.begin(TARGET, WalkerRequestOwnership.LEGACY_OWNER, false);
            ownership.finished(request);
        }

        void arrived() throws Exception
        {
            engineStatus("IDLE");
            set(watchdog, "syntheticDestination", TARGET);
            set(watchdog, "trackedDestination", TARGET);
            assertEquals(EfficientWalker.Status.ARRIVED, walker.getStatus());
        }

        void blockedBankPreview(WorldPoint engineTarget) throws Exception
        {
            bankState("WAITING_BANK_REPLAN");
            set(base.coordinator, "pendingTarget", TARGET);
            set(base.coordinator, "stateStartedTick", 0);
            engineStatus("BLOCKED");
            Object engine = get(walker, "engine");
            Field active = WalkerTestReflection.declaredField(engine.getClass(), "activeRequest");
            java.lang.reflect.Constructor<?> constructor = active.getType()
                .getDeclaredConstructor(WorldPoint.class, boolean.class, boolean.class);
            constructor.setAccessible(true);
            active.setAccessible(true);
            active.set(engine, constructor.newInstance(engineTarget, false, false));
        }

        void bankState(String state) throws Exception { setEnum(base.coordinator, "state", state); }
        void engineStatus(String status) throws Exception { setEnum(get(walker, "engine"), "status", status); }
        @Override public void close() { base.close(); }
    }

    private static Object call(Object object, String name) throws Exception
    {
        java.lang.reflect.Method method = WalkerTestReflection.declaredMethod(object.getClass(), name);
        method.setAccessible(true); return method.invoke(object);
    }

    private static Object get(Object object, String name) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true); return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true); field.set(object, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(Object object, String name, String value) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true); field.set(object, Enum.valueOf((Class) field.getType(), value));
    }
}
