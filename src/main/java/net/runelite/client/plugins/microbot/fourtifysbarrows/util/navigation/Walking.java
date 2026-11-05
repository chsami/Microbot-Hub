package net.runelite.client.plugins.microbot.fourtifysbarrows.util.navigation;

import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.InteractionWorker;

import java.util.Objects;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Cancellable walking: short local steps, and Microbot's walker with the user's settings for routes. */
public class Walking
{
    public enum State
    {
        IDLE, WALKING, ARRIVED, FAILED
    }

    static final long TIMEOUT_MS = 120_000;
    static final long RETRY_DELAY_MS = 1_000;
    private final Backend backend;
    private final LongSupplier clock;
    private final Supplier<ExecutorService> workers;
    private Operation operation;
    private WorldPoint destination;
    private WorldPoint lastPosition;
    private State state = State.IDLE;
    private long lastProgressAt;
    private long nextAttemptAt;
    private String failureReason = "";

    @Inject
    public Walking(Client client, ClientThread clientThread)
    {
        this(client, clientThread, () -> true);
    }

    public Walking(Client client, ClientThread clientThread, BooleanSupplier eligible)
    {
        this(new NativeBackend(client, clientThread, eligible), () -> System.nanoTime() / 1_000_000,
            () -> Executors.newSingleThreadExecutor(action ->
            {
                Thread thread = new Thread(action, "fourtify-walking");
                thread.setDaemon(true);
                return thread;
            }));
    }

    Walking(Backend backend, LongSupplier clock, Supplier<ExecutorService> workers)
    {
        this.backend = backend;
        this.clock = clock;
        this.workers = workers;
    }

    /** Poll from the client thread; the blocking Microbot API call runs on an owned worker. */
    public synchronized State walkTo(WorldPoint target)
    {
        if (!backend.isClientThread())
        {
            return fail("Walking decisions require the client thread");
        }
        WorldPoint position = backend.position();
        if (target == null || position == null)
        {
            return fail("Walking requires an alive logged-in player");
        }
        if (!Objects.equals(destination, target))
        {
            cancel();
            destination = target;
            lastPosition = position;
            lastProgressAt = clock.getAsLong();
        }
        if (state == State.FAILED)
        {
            return state;
        }
        if (position.distanceTo(target) <= 1)
        {
            stopWork();
            state = State.ARRIVED;
            return state;
        }
        long now = clock.getAsLong();
        if (!position.equals(lastPosition))
        {
            lastPosition = position;
            lastProgressAt = now;
        }
        if (now - lastProgressAt >= TIMEOUT_MS)
        {
            return fail("Walking timed out without movement");
        }
        state = State.WALKING;
        if (operation != null)
        {
            if (!operation.finished)
            {
                return state;
            }
            if (!operation.cancelled)
            {
                if (operation.failure != null)
                {
                    return fail(operation.failure instanceof RouteFailure ? operation.failure.getMessage() : "Microbot walking failed");
                }
                nextAttemptAt = now + RETRY_DELAY_MS;
            }
            operation = null;
        }
        if (now < nextAttemptAt)
        {
            return state;
        }
        try
        {
            Operation pending = new Operation(target, workers.get());
            operation = pending;
            pending.future = pending.executor.submit(() -> run(pending));
        }
        catch (RuntimeException failure)
        {
            return fail("Walking worker is unavailable");
        }
        return state;
    }

    private void run(Operation pending)
    {
        if (!pending.started.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            if (!pending.cancelled && !Thread.currentThread().isInterrupted())
            {
                if (backend.position() == null)
                {
                    throw new IllegalStateException("Walking session is no longer eligible");
                }
                if (!pending.cancelled && !Thread.currentThread().isInterrupted())
                {
                    backend.walkTo(pending.destination);
                }
            }
        }
        catch (RuntimeException failure)
        {
            pending.failure = failure;
        }
        finally
        {
            try
            {
                if (pending.cancelled || pending.failure != null)
                {
                    backend.cancel(pending.destination);
                }
            }
            catch (RuntimeException failure)
            {
                pending.failure = failure;
            }
            finally
            {
                pending.executor.shutdown();
                pending.finished = true;
            }
        }
    }

    private State fail(String reason)
    {
        stopWork();
        failureReason = reason;
        state = State.FAILED;
        return state;
    }

    private void stopWork()
    {
        Operation pending = operation;
        if (pending == null)
        {
            if (destination != null)
            {
                backend.cancel(destination);
            }
            return;
        }
        boolean firstCancellation = !pending.cancelled;
        pending.cancelled = true;
        if (pending.future != null)
        {
            pending.future.cancel(true);
        }
        pending.executor.shutdownNow();
        if (firstCancellation)
        {
            backend.cancel(pending.destination);
        }
        if (pending.started.compareAndSet(false, true))
        {
            pending.finished = true;
        }
        if (pending.finished)
        {
            operation = null;
        }
    }

    public synchronized void cancel()
    {
        stopWork();
        destination = null;
        lastPosition = null;
        nextAttemptAt = 0;
        failureReason = "";
        state = State.IDLE;
    }

    public synchronized String getFailureReason()
    {
        return failureReason;
    }

    public synchronized String getDiagnostic()
    {
        String worker = operation == null ? "none" : operation.finished ? "finished"
            : operation.cancelled ? "cancelling" : operation.started.get() ? "running" : "queued";
        return state + ", worker=" + worker + "; " + backend.diagnostic();
    }

    public synchronized boolean isActive()
    {
        return state == State.WALKING;
    }

    public synchronized boolean isStopping()
    {
        return operation != null && operation.cancelled && !operation.finished;
    }

    interface Backend
    {
        boolean isClientThread();
        WorldPoint position();
        boolean walkTo(WorldPoint target);
        default String diagnostic()
        {
            return "";
        }
        void cancel(WorldPoint target);
    }

    private static final class Operation
    {
        private final WorldPoint destination;
        private final ExecutorService executor;
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile boolean cancelled;
        private volatile boolean finished;
        private volatile RuntimeException failure;
        private Future<?> future;

        private Operation(WorldPoint destination, ExecutorService executor)
        {
            this.destination = destination;
            this.executor = executor;
        }
    }

    private static final class RouteFailure extends IllegalStateException
    {
        private RouteFailure(String message)
        {
            super(message);
        }
    }

    static final class NativeBackend implements Backend
    {
        static final int LOCAL_DISTANCE = 64;
        static final String CANCEL_REASON = "fourtify:owned-walk-cancelled";
        private final Client client;
        private final ClientThread clientThread;
        private final BooleanSupplier eligible;
        private final Function<WorldPoint, WalkerState> walker;
        private final Supplier<WorldPoint> activeRoute;
        private final Runnable clearRoute;
        private final Executor cleanup;
        private final BiPredicate<WorldPoint, Boolean> movement;
        private final BooleanSupplier runEnabled;
        private final BiFunction<WorldPoint, WorldPoint, WorldPoint> localWaypoint;
        private final AtomicLong generation = new AtomicLong();
        private final AtomicLong attempts = new AtomicLong();
        private volatile String diagnostic = "No walking attempt";
        private WorldPoint ownedTarget;

        private NativeBackend(Client client, ClientThread clientThread, BooleanSupplier eligible)
        {
            this(client, clientThread, eligible, target -> Rs2Walker.walkWithState(target, 1), Rs2Walker::getCurrentTarget,
                () -> Rs2Walker.clearWalkingRoute(CANCEL_REASON), ForkJoinPool.commonPool(),
                Rs2Walker::walkFastCanvas, Rs2Player::isRunEnabled,
                (start, target) -> Rs2Tile.getReachableTilesFromTile(start, LOCAL_DISTANCE).keySet().stream()
                    .filter(point -> point.distanceTo(target) <= 1
                        && LocalPoint.fromWorld(client.getTopLevelWorldView(), point) != null)
                    .min(Comparator.comparingInt((WorldPoint point) -> point.distanceTo(target))
                        .thenComparingInt(start::distanceTo)).orElse(null));
        }

        NativeBackend(Client client, ClientThread clientThread, BooleanSupplier eligible,
                        Function<WorldPoint, WalkerState> walker, Supplier<WorldPoint> activeRoute,
                        Runnable clearRoute, Executor cleanup,
                        BiPredicate<WorldPoint, Boolean> movement, BooleanSupplier runEnabled,
                        BiFunction<WorldPoint, WorldPoint, WorldPoint> localWaypoint)
        {
            this.client = client;
            this.clientThread = clientThread;
            this.eligible = eligible;
            this.walker = walker;
            this.activeRoute = activeRoute;
            this.clearRoute = clearRoute;
            this.cleanup = cleanup;
            this.movement = movement;
            this.runEnabled = runEnabled;
            this.localWaypoint = localWaypoint;
        }

        @Override
        public boolean isClientThread()
        {
            return client != null && client.isClientThread();
        }

        @Override
        public WorldPoint position()
        {
            return onClientThread(this::readPosition);
        }

        private <T> T onClientThread(Supplier<T> read)
        {
            if (isClientThread())
            {
                return read.get();
            }
            try
            {
                return InteractionWorker.onClientThread(clientThread, read, "Could not validate walking session");
            }
            catch (IllegalStateException failure)
            {
                // The shared helper wraps the read's failure; a route failure keeps its own message.
                Throwable cause = failure.getCause() == null ? null : failure.getCause().getCause();
                if (cause instanceof RouteFailure)
                {
                    throw (RouteFailure) cause;
                }
                throw failure;
            }
        }

        private WorldPoint readPosition()
        {
            Player player = client.getLocalPlayer();
            if (client.getGameState() != GameState.LOGGED_IN || player == null || player.isDead()
                || client.getTopLevelWorldView() == null
                || player.getWorldView() != client.getTopLevelWorldView() || !eligible.getAsBoolean())
            {
                return null;
            }
            return player.getWorldLocation();
        }

        @Override
        public boolean walkTo(WorldPoint target)
        {
            long attempt = attempts.incrementAndGet();
            diagnostic = attempt + ": validating player; target=" + target;
            long expected = generation.get();
            WorldPoint start = position();
            if (start == null || Thread.currentThread().isInterrupted())
            {
                diagnostic = attempt + ": stopped before local checks";
                return false;
            }
            WorldPoint active = activeRoute.get();
            synchronized (this)
            {
                if (active != null && !active.equals(ownedTarget))
                {
                    throw new RouteFailure("Stop the active Microbot route before Barrows travel.");
                }
            }
            diagnostic = attempt + ": checking local route; start=" + start + "; target=" + target;
            WorldPoint local = onClientThread(() ->
            {
                if (generation.get() != expected || !start.equals(readPosition()))
                {
                    return null;
                }
                boolean samePlane = start.getPlane() == target.getPlane();
                boolean inRange = start.distanceTo(target) <= LOCAL_DISTANCE;
                boolean loaded = LocalPoint.fromWorld(client.getTopLevelWorldView(), target) != null;
                diagnostic = attempt + ": local samePlane=" + samePlane + ", inRange=" + inRange + ", loaded=" + loaded;
                WorldPoint selected = samePlane && inRange && loaded ? localWaypoint.apply(start, target) : null;
                if (selected != null && (selected.distanceTo(target) > 1
                    || LocalPoint.fromWorld(client.getTopLevelWorldView(), selected) == null))
                {
                    throw new RouteFailure("Microbot returned an invalid local arrival tile; travel stopped.");
                }
                diagnostic += ", reachable=" + (selected != null);
                return generation.get() == expected && start.equals(readPosition()) ? selected : null;
            });
            if (generation.get() != expected || Thread.currentThread().isInterrupted() || !start.equals(position()))
            {
                diagnostic = attempt + ": local check became stale or cancelled";
                return false;
            }
            if (local != null)
            {
                diagnostic = attempt + ": dispatching local walk to " + local;
                boolean dispatched = movement.test(local, runEnabled.getAsBoolean());
                diagnostic = attempt + ": local dispatch=" + dispatched + "; target=" + local;
                return dispatched;
            }
            String localDiagnostic = diagnostic;
            synchronized (this)
            {
                if (generation.get() != expected)
                {
                    diagnostic = attempt + ": cancelled before Microbot travel";
                    return false;
                }
                ownedTarget = target;
            }
            diagnostic = localDiagnostic + "; walking with Microbot";
            WalkerState result = walker.apply(target);
            diagnostic = localDiagnostic + "; Microbot walker " + result;
            if (result == WalkerState.UNREACHABLE)
            {
                throw new RouteFailure("Microbot found no route to the destination. Move closer or check the blocked path.");
            }
            return result == WalkerState.ARRIVED;
        }

        @Override
        public String diagnostic()
        {
            return diagnostic;
        }

        @Override
        public synchronized void cancel(WorldPoint target)
        {
            generation.incrementAndGet();
            if (ownedTarget != null)
            {
                ownedTarget = null;
                // Route cleanup can wait for client-thread reads, so it runs off the caller's thread.
                cleanup.execute(clearRoute);
            }
        }
    }
}
