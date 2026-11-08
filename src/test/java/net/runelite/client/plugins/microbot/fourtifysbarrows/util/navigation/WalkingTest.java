package net.runelite.client.plugins.microbot.fourtifysbarrows.util.navigation;

import java.util.ArrayDeque;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET, "the bank"));
        assertEquals(0, backend.calls);
        walking.walkTo(TARGET, "the bank");
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
        backend.result = true;
        walking.walkTo(TARGET, "the bank");
        workers.get(0).runAll();
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET, "the bank"));
        assertTrue(walking.isActive());
        backend.position = TARGET;
        assertEquals(Walking.State.ARRIVED, walking.walkTo(TARGET, "the bank"));
        assertFalse(walking.isActive());
    }

    @Test
    public void observedArrivalWithinSdkToleranceNeedsNoWorker()
    {
        backend.position = new WorldPoint(TARGET.getX() + 1, TARGET.getY(), 0);
        assertEquals(Walking.State.ARRIVED, walking.walkTo(TARGET, "the bank"));
        assertTrue(workers.isEmpty());
    }

    @Test
    public void aDifferentPlaneCannotBeReportedAsArrival()
    {
        backend.position = new WorldPoint(TARGET.getX(), TARGET.getY(), 1);
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET, "the bank"));
        workers.get(0).runAll();
        assertEquals(1, backend.calls);
    }

    @Test
    public void falseSdkResultCanBeRetriedAfterItsDelay()
    {
        backend.result = false;
        walking.walkTo(TARGET, "the bank");
        workers.get(0).runAll();
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET, "the bank"));
        assertEquals(1, workers.size());
        now.set(Walking.RETRY_DELAY_MS - 1);
        walking.walkTo(TARGET, "the bank");
        assertEquals(1, workers.size());
        now.incrementAndGet();
        walking.walkTo(TARGET, "the bank");
        assertEquals(2, workers.size());
    }

    @Test
    public void nonArrivalRetriesUseTheWholeTimeoutWithoutClearingAnOngoingSdkRoute()
    {
        for (int attempt = 0; attempt < 8; attempt++)
        {
            assertEquals(Walking.State.WALKING, walking.walkTo(TARGET, "the bank"));
            workers.get(attempt).runAll();
            assertEquals(Walking.State.WALKING, walking.walkTo(TARGET, "the bank"));
            assertEquals(0, backend.cancels);
            now.addAndGet(Walking.RETRY_DELAY_MS);
        }
        now.set(Walking.TIMEOUT_MS);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET, "the bank"));
        assertEquals(8, backend.calls);
        assertEquals(1, backend.cancels);
        assertTrue(walking.getFailureReason().contains("made no progress for 120 seconds"));
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET, "the bank"));
        walking.cancel();
        assertEquals(Walking.State.WALKING, walking.walkTo(TARGET, "the bank"));
    }

    @Test
    public void cancellationBeforeWorkerStartPreventsAnySdkCall()
    {
        walking.walkTo(TARGET, "the bank");
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
        walking.walkTo(TARGET, "the bank");
        backend.position = null;
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET, "the bank"));
        assertFalse(walking.isActive());
    }

    @Test
    public void cancellingAFinishedSdkCallAlsoCleansUpItsRemainingOwnedRoute()
    {
        walking.walkTo(TARGET, "the bank");
        workers.get(0).runAll();
        walking.cancel();
        assertEquals(1, backend.cancels);
        walking.cancel();
        assertEquals(1, backend.cancels);
    }

    @Test
    public void cancellingAQueuedRetryAlsoClearsTheRouteFromItsFinishedPreviousAttempt()
    {
        walking.walkTo(TARGET, "the bank");
        workers.get(0).runAll();
        walking.walkTo(TARGET, "the bank");
        now.addAndGet(Walking.RETRY_DELAY_MS);
        walking.walkTo(TARGET, "the bank");
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
        walking.walkTo(TARGET, "the bank");
        assertEquals(Walking.State.WALKING, walking.walkTo(new WorldPoint(START.getX() + 5, START.getY(), 0), "the bank"));
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
        workers.get(1).runAll();
        assertEquals(1, backend.calls);
        assertEquals(new WorldPoint(START.getX() + 5, START.getY(), 0), backend.target);
    }

    @Test
    public void timeoutCancelsQueuedWorkWithoutDispatch()
    {
        walking.walkTo(TARGET, "the bank");
        now.set(Walking.TIMEOUT_MS);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET, "the bank"));
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
        assertTrue(walking.getFailureReason().contains("made no progress for 120 seconds"));
    }

    @Test
    public void observedMovementAllowsLongTripsButStationaryTravelStillTimesOut()
    {
        walking.walkTo(TARGET, "the bank");
        for (int step = 1; step <= 3; step++)
        {
            now.addAndGet(Walking.TIMEOUT_MS - 1);
            backend.position = new WorldPoint(START.getX() + step, START.getY(), 0);
            assertEquals(Walking.State.WALKING, walking.walkTo(TARGET, "the bank"));
        }
        now.addAndGet(Walking.TIMEOUT_MS);
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET, "the bank"));
        workers.get(0).runAll();
        assertEquals(0, backend.calls);
    }

    @Test
    public void unavailablePlayerAndWrongThreadNeverStartWalking()
    {
        backend.position = null;
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET, "the bank"));
        assertTrue(workers.isEmpty());
        backend.position = START;
        backend.clientThread = false;
        walking.cancel();
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET, "the bank"));
        assertTrue(workers.isEmpty());
    }

    @Test
    public void workerFailurePausesUntilCancelled()
    {
        backend.failure = new IllegalStateException("Microbot API failure");
        walking.walkTo(TARGET, "the bank");
        workers.get(0).runAll();
        assertEquals(Walking.State.FAILED, walking.walkTo(TARGET, "the bank"));
        assertEquals("Could not walk to the bank; walk there yourself, then enable the plugin again",
            walking.getFailureReason());
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
            public boolean walkTo(WorldPoint target)
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
                return false;
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
            active.walkTo(TARGET, "the bank");
            assertTrue(started.await(1, TimeUnit.SECONDS));
            active.cancel();
            assertTrue(running.cancels > 0);
            assertEquals(Walking.State.WALKING, active.walkTo(replacement, "the bank"));
            assertEquals(1, actualWorkers.size());
            release.countDown();
            assertTrue(finished.await(1, TimeUnit.SECONDS));
            assertTrue(actualWorkers.get(0).awaitTermination(1, TimeUnit.SECONDS));
            assertEquals(Walking.State.WALKING, active.walkTo(replacement, "the bank"));
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
    public void loadedReachableTargetBeyondThirteenTilesSkipsGlobalPlanningAndKeepsRunState() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        fixture.localReachable = true;
        WorldPoint target = new WorldPoint(START.getX() + 17, START.getY(), 0);
        assertTrue(fixture.backend.walkTo(target));
        assertEquals(List.of(target), fixture.moves);
        assertEquals(0, fixture.walks);
        assertTrue(fixture.backend.diagnostic().contains("1: local dispatch=true"));
        assertFalse(fixture.lastRun);
        fixture.running = true;
        assertTrue(fixture.backend.walkTo(target));
        assertTrue(fixture.lastRun);
        assertEquals(0, fixture.walks);
        assertTrue(fixture.backend.diagnostic().contains("2: local dispatch=true"));
    }

    @Test
    public void localWalkingCanReachAnAdjacentArrivalTileWithoutPlanningThroughABankChest() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        fixture.localReachable = true;
        WorldPoint target = new WorldPoint(START.getX() + 17, START.getY(), 0);
        fixture.localArrival = target.dx(-1);
        assertTrue(fixture.backend.walkTo(target));
        assertEquals(List.of(fixture.localArrival), fixture.moves);
        assertEquals(0, fixture.walks);
        fixture.localArrival = target.dx(-2);
        assertThrows(IllegalStateException.class, () -> fixture.backend.walkTo(target));
    }

    @Test
    public void localFalseDispatchIsVisibleWithoutStartingAPlanner() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        fixture.localReachable = true;
        fixture.movementResult = false;
        WorldPoint target = new WorldPoint(START.getX() + 17, START.getY(), 0);
        assertFalse(fixture.backend.walkTo(target));
        assertEquals(0, fixture.walks);
        assertTrue(fixture.backend.diagnostic().contains("local dispatch=false"));
        Walking active = new Walking(fixture.backend, now::get, ManualExecutor::new);
        assertTrue(active.getDiagnostic().contains("worker=none"));
        assertTrue(active.getDiagnostic().contains("local dispatch=false"));
    }

    @Test
    public void localReachabilityStillRequiresLoadedTargetAndSamePlane() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        fixture.localReachable = true;
        for (WorldPoint target : List.of(new WorldPoint(START.getX() + 17, START.getY(), 1),
            new WorldPoint(START.getX() + 100, START.getY(), 0)))
        {
            fixture.walkResult = WalkerState.UNREACHABLE;
            assertThrows(IllegalStateException.class, () -> fixture.backend.walkTo(target));
        }
        assertEquals(2, fixture.walks);
        assertEquals(0, fixture.localChecks);
        assertTrue(fixture.moves.isEmpty());
    }

    @Test
    public void staleOrCancelledLocalReachabilityNeverDispatchesOrStartsGlobalPlan() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        fixture.localReachable = true;
        WorldPoint target = new WorldPoint(START.getX() + 17, START.getY(), 0);
        fixture.afterLocalCheck = () -> fixture.position = fixture.path.get(1);
        assertFalse(fixture.backend.walkTo(target));
        fixture.position = START;
        fixture.afterLocalCheck = () -> fixture.backend.cancel(target);
        assertFalse(fixture.backend.walkTo(target));
        fixture.afterLocalCheck = () -> fixture.eligible = false;
        assertFalse(fixture.backend.walkTo(target));
        assertEquals(0, fixture.walks);
        assertTrue(fixture.moves.isEmpty());
    }

    @Test
    public void interruptedLocalReachabilityNeverDispatches() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        fixture.localReachable = true;
        fixture.afterLocalCheck = () -> Thread.currentThread().interrupt();
        try
        {
            assertFalse(fixture.backend.walkTo(new WorldPoint(START.getX() + 17, START.getY(), 0)));
            assertEquals(0, fixture.walks);
            assertTrue(fixture.moves.isEmpty());
        }
        finally
        {
            Thread.interrupted();
        }
    }

    @Test
    public void routesGoToMicrobotsWalkerWithTheUserSettings() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        WorldPoint target = new WorldPoint(START.getX() + 40, START.getY(), 0);
        assertTrue(fixture.backend.walkTo(target));
        assertEquals(List.of(target), fixture.walked);
        assertTrue(fixture.moves.isEmpty());
        assertTrue(fixture.backend.diagnostic().contains("Microbot walker ARRIVED"));
        for (WalkerState state : List.of(WalkerState.MOVING, WalkerState.EXIT))
        {
            fixture.walkResult = state;
            assertFalse(fixture.backend.walkTo(target));
        }
        assertEquals(3, fixture.walks);
    }

    @Test
    public void anUnreachableDestinationFailsTheWalkWithAnActionableReason() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        fixture.walkResult = WalkerState.UNREACHABLE;
        WorldPoint target = new WorldPoint(START.getX() + 40, START.getY(), 0);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fixture.backend.walkTo(target));
        assertTrue(failure.getMessage().contains("No path found to"));
        Walking active = new Walking(fixture.backend, now::get, () ->
        {
            ManualExecutor executor = new ManualExecutor();
            workers.add(executor);
            return executor;
        });
        active.walkTo(target, "the bank");
        workers.get(0).runAll();
        assertEquals(Walking.State.FAILED, active.walkTo(target, "the bank"));
        assertTrue(active.getFailureReason().equals("No path found to the bank; move closer, then enable the plugin again"));
    }

    @Test
    public void anotherActiveMicrobotRouteIsNeverTakenOver() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        WorldPoint target = new WorldPoint(START.getX() + 40, START.getY(), 0);
        fixture.active = new WorldPoint(3200, 3200, 0);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fixture.backend.walkTo(target));
        assertTrue(failure.getMessage().equals("Another Microbot walk is running; stop it, then enable the plugin again"));
        assertEquals(0, fixture.walks);
        fixture.active = null;
        assertTrue(fixture.backend.walkTo(target));
        fixture.active = target;
        assertTrue(fixture.backend.walkTo(target), "The plugin's own route can continue");
        assertEquals(2, fixture.walks);
    }

    @Test
    public void cancellingClearsOnlyAnOwnedMicrobotRoute() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        WorldPoint target = new WorldPoint(START.getX() + 40, START.getY(), 0);
        fixture.backend.cancel(target);
        assertEquals(0, fixture.clears);
        assertTrue(fixture.backend.walkTo(target));
        fixture.backend.cancel(target);
        fixture.backend.cancel(target);
        assertEquals(1, fixture.clears);
    }

    @Test
    public void cancellationBeforeTheWalkerStartsNeverCallsIt() throws Exception
    {
        NativeFixture fixture = new NativeFixture();
        WorldPoint target = new WorldPoint(START.getX() + 40, START.getY(), 0);
        fixture.afterLocalCheck = () -> fixture.backend.cancel(target);
        fixture.localReachable = false;
        fixture.baseX = START.getX() - 10;
        assertFalse(fixture.backend.walkTo(new WorldPoint(START.getX() + 17, START.getY(), 0)));
        assertEquals(0, fixture.walks);
    }

    private static final class NativeFixture
    {
        private final List<WorldPoint> path = new ArrayList<>();
        private final List<WorldPoint> moves = new ArrayList<>();
        private final List<WorldPoint> walked = new ArrayList<>();
        private WorldPoint position = START;
        private int baseX = START.getX() - 10;
        private boolean eligible = true;
        private boolean clientThread = true;
        private boolean running;
        private boolean lastRun;
        private boolean movementResult = true;
        private int walks;
        private int clears;
        private WalkerState walkResult = WalkerState.ARRIVED;
        private WorldPoint active;
        private boolean localReachable;
        private WorldPoint localArrival;
        private int localChecks;
        private Runnable afterLocalCheck = () -> { };
        private final Walking.NativeBackend backend;

        private NativeFixture() throws Exception
        {
            for (int index = 0; index <= 12; index++)
            {
                path.add(new WorldPoint(START.getX() + index, START.getY(), 0));
            }
            WorldView view = proxy(WorldView.class, (method, arguments) ->
            {
                switch (method)
                {
                    case "getBaseX": return baseX;
                    case "getBaseY": return START.getY() - 10;
                    case "getSizeX": return 104;
                    case "getSizeY": return 104;
                    default: return null;
                }
            });
            Player player = proxy(Player.class, (method, arguments) ->
            {
                switch (method)
                {
                    case "getWorldLocation": return position;
                    case "getWorldView": return view;
                    default: return null;
                }
            });
            Client client = proxy(Client.class, (method, arguments) ->
            {
                switch (method)
                {
                    case "isClientThread": return clientThread;
                    case "getGameState": return GameState.LOGGED_IN;
                    case "getLocalPlayer": return player;
                    case "getTopLevelWorldView": return view;
                    default: return null;
                }
            });
            ClientThread gate = new ClientThread()
            {
                @Override
                public void invokeLater(Runnable action)
                {
                    action.run();
                }
            };
            backend = new Walking.NativeBackend(client, gate, () -> eligible, target ->
            {
                walks++;
                walked.add(target);
                return walkResult;
            }, () -> active, () -> clears++, Runnable::run, (point, run) ->
            {
                moves.add(point);
                lastRun = run;
                return movementResult;
            }, () -> running, (start, target) ->
            {
                localChecks++;
                afterLocalCheck.run();
                return localReachable ? localArrival == null ? target : localArrival : null;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> behavior)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (instance, method, arguments) ->
        {
            Object result = behavior.apply(method.getName(), arguments);
            if (result != null || !method.getReturnType().isPrimitive())
            {
                return result;
            }
            return method.getReturnType() == boolean.class ? false : 0;
        });
    }

    private static class FakeBackend implements Walking.Backend
    {
        private WorldPoint position = START;
        private boolean clientThread = true;
        private boolean result;
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
        public boolean walkTo(WorldPoint target)
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
