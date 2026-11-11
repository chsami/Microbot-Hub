package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.navigation;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WalkingTest
{
    private static final WorldPoint START = new WorldPoint(2825, 3000, 0);
    private static final WorldPoint TARGET = new WorldPoint(2852, 2954, 0);
    private final AtomicLong now = new AtomicLong();
    private final List<ManualExecutor> workers = new ArrayList<>();
    private FakeBackend backend;
    private Walking walking;

    @BeforeEach
    public void setUp()
    {
        backend = new FakeBackend();
        walking = new Walking(backend, now::get, () ->
        {
            ManualExecutor worker = new ManualExecutor();
            workers.add(worker);
            return worker;
        });
    }

    @Test
    public void runsTheSdkCallOnAWorkerAndSuppressesDuplicateRequests()
    {
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET));
        assertEquals(0, backend.calls);
        walking.walkTo(TARGET);
        assertEquals(1, workers.size());
        workers.get(0).runAll();
        assertEquals(1, backend.calls);
        assertEquals(TARGET, backend.target);
        assertTrue(workers.get(0).isShutdown());
        assertTrue(walking.isActive());
    }

    @Test
    public void aSuccessfulSdkResultDoesNotFabricateArrival()
    {
        backend.result = WalkerState.ARRIVED;
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET));
        assertTrue(walking.isActive());
        backend.position = TARGET;
        assertEquals(Walking.State.ARRIVED, walking.walkTo(TARGET));
        assertFalse(walking.isActive());
    }

    @Test
    public void observedArrivalWithinSdkToleranceNeedsNoWorker()
    {
        backend.position = new WorldPoint(TARGET.getX() + 1, TARGET.getY(), 0);
        assertEquals(Walking.State.ARRIVED, walking.walkTo(TARGET));
        assertTrue(workers.isEmpty());
    }

    @Test
    public void aDifferentPlaneIsNotArrivalAndCanUseNormalTransportRouting()
    {
        backend.position = new WorldPoint(TARGET.getX(), TARGET.getY(), 1);
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET));
        workers.get(0).runAll();
        assertEquals(1, backend.calls);
    }

    @Test
    public void unreachableSdkResultFailsWithoutWaitingForTheStallTimeout()
    {
        backend.result = WalkerState.UNREACHABLE;
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        now.set(Walking.RETRY_DELAY_MS);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        assertTrue(walking.getFailureReason().contains("route"));
        assertFalse(walking.isActive());
        assertEquals(1, backend.cancels);
        assertEquals(1, workers.size());
    }

    @Test
    public void exitSdkResultFailsTheWalk()
    {
        backend.result = WalkerState.EXIT;
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        assertTrue(walking.getFailureReason().contains("walker"));
        assertFalse(walking.isActive());
    }

    @Test
    public void movingSdkResultCanBeRetriedAfterItsDelay()
    {
        backend.result = WalkerState.MOVING;
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET));
        assertEquals(1, workers.size());
        now.set(Walking.RETRY_DELAY_MS - 1);
        walking.walkTo(TARGET);
        assertEquals(1, workers.size());
        now.incrementAndGet();
        walking.walkTo(TARGET);
        assertEquals(2, workers.size());
    }

    @Test
    public void nonArrivalRetriesUseTheWholeTimeoutWithoutClearingAnOngoingSdkRoute()
    {
        for (int attempt = 0; attempt < 8; attempt++)
        {
            assertEquals(Walking.State.WALKING, walking.walkTo(TARGET));
            workers.get(attempt).runAll();
            assertEquals(Walking.State.WALKING, walking.walkTo(TARGET));
            assertEquals(0, backend.cancels);
            now.addAndGet(Walking.RETRY_DELAY_MS);
        }
        now.set(Walking.TIMEOUT_MS);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        assertEquals(8, backend.calls);
        assertEquals(1, backend.cancels);
        assertTrue(walking.getFailureReason().contains("timed out"));
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        walking.cancel();
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET));
    }

    @Test
    public void cancellationBeforeWorkerStartPreventsAnySdkCall()
    {
        walking.walkTo(TARGET);
        walking.cancel();
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
        assertEquals(0, backend.cancels);
        assertFalse(walking.isActive());
        assertTrue(workers.get(0).isShutdown());
    }

    @Test
    public void unavailablePlayerBeforeWorkerStartPreventsAnySdkCall()
    {
        walking.walkTo(TARGET);
        backend.position = null;
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        assertFalse(walking.isActive());
    }

    @Test
    public void queuedSdkCleanupBlocksReplacementUntilItFinishes()
    {
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        backend.cancelling = true;
        walking.cancel();
        assertTrue(walking.isStopping());
        WorldPoint replacement = new WorldPoint(2820, 3000, 0);
        for (int attempt = 0; attempt < 5; attempt++)
        {
            assertEquals(Walking.State.WALKING, walking.walkTo(replacement));
        }
        assertEquals(1, workers.size());
        assertEquals(1, backend.calls);

        backend.cancelling = false;
        assertFalse(walking.isStopping());
        assertEquals(Walking.State.WALKING, walking.walkTo(replacement));
        assertEquals(2, workers.size());
        workers.get(1).runAll();
        assertEquals(2, backend.calls);
    }

    @Test
    public void exceptionalCleanupRemainsVisibleAfterItsFutureFinishes()
    {
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        backend.cancelling = true;
        walking.cancel();
        assertTrue(walking.isStopping());
        backend.cancelling = false;
        backend.cancellationFailure = "Travel cleanup failed";
        assertFalse(walking.isStopping());
        assertEquals("Travel cleanup failed", walking.getCancellationFailure());
    }

    @Test
    public void resetRetriesFailedCleanupAndBlocksTravelUntilThatRetryCompletes()
    {
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        backend.cancelling = true;
        walking.cancel();
        backend.cancelling = false;
        backend.cancellationFailure = "Travel cleanup failed";
        assertEquals("Travel cleanup failed", walking.getCancellationFailure());

        walking.cancel();
        assertEquals(1, backend.cancellationRetries);
        assertTrue(walking.isStopping());
        assertNull(walking.getCancellationFailure());
        WorldPoint replacement = new WorldPoint(2820, 3000, 0);
        for (int attempt = 0; attempt < 3; attempt++)
        {
            assertEquals(Walking.State.WALKING, walking.walkTo(replacement));
        }
        walking.cancel();
        assertEquals(1, backend.cancellationRetries);
        assertEquals(1, workers.size());
        assertEquals(1, backend.calls);

        backend.cancelling = false;
        assertFalse(walking.isStopping());
        assertEquals(Walking.State.WALKING, walking.walkTo(replacement));
        assertEquals(2, workers.size());
        workers.get(0).runAll();
        workers.get(1).runAll();
        assertEquals(2, backend.calls);
        assertEquals(replacement, backend.target);
        assertEquals(1, backend.cancellationRetries);
    }

    @Test
    public void cancellingAFinishedSdkCallAlsoCleansUpItsRemainingOwnedRoute()
    {
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        walking.cancel();
        assertEquals(1, backend.cancels);
        walking.cancel();
        assertEquals(1, backend.cancels);
    }

    @Test
    public void cancellingAQueuedRetryAlsoClearsTheRouteFromItsFinishedPreviousAttempt()
    {
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        walking.walkTo(TARGET);
        now.addAndGet(Walking.RETRY_DELAY_MS);
        walking.walkTo(TARGET);
        assertEquals(2, workers.size());
        assertEquals(1, backend.calls);
        walking.cancel();
        workers.get(1).runAll();
        assertEquals(1, backend.calls);
        assertEquals(1, backend.cancels);
        assertFalse(walking.isActive());
    }

    @Test
    public void changingDestinationDiscardsTheQueuedOldRequest()
    {
        walking.walkTo(TARGET);
        assertEquals(Walking.State.WALKING, walking.walkTo(new WorldPoint(START.getX() + 5, START.getY(), 0)));
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
        workers.get(1).runAll();
        assertEquals(1, backend.calls);
        assertEquals(new WorldPoint(START.getX() + 5, START.getY(), 0), backend.target);
    }

    @Test
    public void timeoutCancelsQueuedWorkWithoutDispatch()
    {
        walking.walkTo(TARGET);
        now.set(Walking.TIMEOUT_MS);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
        assertTrue(walking.getFailureReason().contains("timed out"));
    }

    @Test
    public void observedMovementAllowsLongTripsButStationaryTravelStillTimesOut()
    {
        walking.walkTo(TARGET);
        for (int step = 1; step <= 3; step++)
        {
            now.addAndGet(Walking.TIMEOUT_MS - 1);
            backend.position = new WorldPoint(START.getX() + step, START.getY(), 0);
            assertEquals(Walking.State.WALKING, walking.walkTo(TARGET));
        }
        now.addAndGet(Walking.TIMEOUT_MS);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
    }

    @Test
    public void unavailablePlayerAndWrongThreadNeverStartWalking()
    {
        backend.position = null;
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        assertTrue(workers.isEmpty());
        backend.position = START;
        backend.clientThread = false;
        walking.cancel();
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        assertTrue(workers.isEmpty());
    }

    @Test
    public void workerFailurePausesUntilCancelled()
    {
        backend.failure = new IllegalStateException("Microbot API failure");
        walking.walkTo(TARGET);
        workers.get(0).runAll();
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET));
        assertTrue(walking.getFailureReason().contains("failed"));
        assertFalse(walking.isActive());
    }

    @Test
    @Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void cancelledRunningWorkerCannotOverlapAReplacementEvenIfItIgnoresInterruption() throws Exception
    {
        assertCancelledWorkerCannotOverlapReplacement(new WorldPoint(2800, 3000, 0));
    }

    @Test
    @Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void cancelledWorkerCannotCleanUpANewerWalkToTheSameDestination() throws Exception
    {
        assertCancelledWorkerCannotOverlapReplacement(TARGET);
    }

    private void assertCancelledWorkerCannotOverlapReplacement(WorldPoint replacement) throws Exception
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        FakeBackend running = new FakeBackend()
        {
            @Override
            public WalkerState walkTo(WorldPoint target)
            {
                super.walkTo(target);
                started.countDown();
                boolean waiting = true;
                while (waiting)
                {
                    try
                    {
                        release.await();
                        waiting = false;
                    }
                    catch (InterruptedException ignored)
                    {
                        // Model a Microbot API call that needs time to finish cancelling.
                    }
                }
                finished.countDown();
                return WalkerState.MOVING;
            }
        };
        List<ExecutorService> actualWorkers = new ArrayList<>();
        Walking active = new Walking(running, now::get, () ->
        {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            actualWorkers.add(worker);
            return worker;
        });
        try
        {
            active.walkTo(TARGET);
            assertTrue(started.await(1, TimeUnit.SECONDS));
            active.cancel();
            assertTrue(running.cancels > 0);
            assertEquals(Walking.State.WALKING, active.walkTo(replacement));
            assertEquals(1, actualWorkers.size());
            release.countDown();
            assertTrue(finished.await(1, TimeUnit.SECONDS));
            assertTrue(actualWorkers.get(0).awaitTermination(1, TimeUnit.SECONDS));
            assertEquals(Walking.State.WALKING, active.walkTo(replacement));
            assertEquals(2, actualWorkers.size());
        }
        finally
        {
            release.countDown();
            active.cancel();
            for (ExecutorService worker : actualWorkers)
            {
                worker.shutdownNow();
            }
        }
    }

    @Test
    public void nativeCancellationClearsOwnedSdkRouteAfterRetargeting() throws Exception
    {
        assertNativeCancellation(new WorldPoint(2867, 3175, 0), true);
        assertNativeCancellation(null, true);
    }

    @Test
    public void nativeCancellationLeavesAnUnownedSdkRouteAlone() throws Exception
    {
        assertNativeCancellation(new WorldPoint(2867, 3175, 0), false);
    }

    private static void assertNativeCancellation(WorldPoint currentTarget, boolean owned) throws Exception
    {
        Walking nativeWalking = new Walking(null, null);
        Field backendField = Walking.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        Walking.Backend nativeBackend = (Walking.Backend) backendField.get(nativeWalking);
        Field ownedField = nativeBackend.getClass().getDeclaredField("ownedTarget");
        ownedField.setAccessible(true);
        Field cleanupField = nativeBackend.getClass().getDeclaredField("cleanup");
        cleanupField.setAccessible(true);
        Field targetField = Rs2Walker.class.getDeclaredField("currentTarget");
        targetField.setAccessible(true);
        Object previousTarget = targetField.get(null);
        Future<?> previousRoute = Rs2PathApi.getPathfinderFuture();
        FutureTask<Void> queuedRoute = new FutureTask<>(() -> null);
        try
        {
            targetField.set(null, currentTarget);
            Rs2PathApi.setPathfinderFuture(queuedRoute);
            if (owned)
            {
                ownedField.set(nativeBackend, TARGET);
            }
            nativeBackend.cancel(TARGET);
            ((CompletableFuture<?>) cleanupField.get(nativeBackend)).get(2, TimeUnit.SECONDS);
            assertEquals(owned, queuedRoute.isCancelled());
            if (owned)
            {
                assertNull(Rs2Walker.getCurrentTarget());
                assertNull(Rs2PathApi.getPathfinderFuture());
            }
            else
            {
                assertEquals(currentTarget, Rs2Walker.getCurrentTarget());
                assertEquals(queuedRoute, Rs2PathApi.getPathfinderFuture());
            }
        }
        finally
        {
            targetField.set(null, previousTarget);
            Rs2PathApi.setPathfinderFuture(previousRoute);
        }
    }

    private static class FakeBackend implements Walking.Backend
    {
        private WorldPoint position = START;
        private boolean clientThread = true;
        private WalkerState result = WalkerState.MOVING;
        private boolean cancelling;
        private String cancellationFailure;
        private int cancellationRetries;
        private RuntimeException failure;
        private int calls;
        private volatile int cancels;
        private WorldPoint target;

        @Override
        public boolean isClientThread()
        {
            return clientThread;
        }

        @Override
        public WorldPoint position()
        {
            return position;
        }

        @Override
        public WalkerState walkTo(WorldPoint target)
        {
            this.target = target;
            calls++;
            if (failure != null)
            {
                throw failure;
            }
            return result;
        }

        @Override
        public boolean isCancelling()
        {
            return cancelling;
        }

        @Override
        public String cancellationFailure()
        {
            return cancellationFailure;
        }

        @Override
        public void retryCancellation()
        {
            if (cancellationFailure != null && !cancelling)
            {
                cancellationRetries++;
                cancelling = true;
                cancellationFailure = null;
            }
        }

        @Override
        public void cancel(WorldPoint target)
        {
            if (target.equals(this.target))
            {
                this.target = null;
                cancels++;
            }
        }
    }

    private static final class ManualExecutor extends AbstractExecutorService
    {
        private final Deque<Runnable> actions = new ArrayDeque<>();
        private boolean shutdown;

        void runAll()
        {
            while (!actions.isEmpty())
            {
                actions.removeFirst().run();
            }
        }

        @Override
        public void shutdown()
        {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow()
        {
            shutdown = true;
            return new ArrayList<>(actions);
        }

        @Override
        public boolean isShutdown()
        {
            return shutdown;
        }

        @Override
        public boolean isTerminated()
        {
            return shutdown && actions.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return isTerminated();
        }

        @Override
        public void execute(Runnable action)
        {
            if (shutdown)
            {
                throw new IllegalStateException("Worker is shut down");
            }
            actions.addLast(action);
        }
    }
}
