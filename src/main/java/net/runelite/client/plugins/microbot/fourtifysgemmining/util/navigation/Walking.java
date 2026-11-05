package net.runelite.client.plugins.microbot.fourtifysgemmining.util.navigation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

/** One plugin's cancellable worker for the normal Microbot walker. */
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
        if (getCancellationFailure() != null)
        {
            return fail(getCancellationFailure());
        }
        if (!backend.isClientThread())
        {
            return fail("walking could not start");
        }
        WorldPoint position = backend.position();
        if (target == null || position == null)
        {
            return fail("you logged out or died");
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
            return fail("walking timed out after " + TIMEOUT_MS / 1000 + " seconds without moving");
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
                    return fail("Microbot walking failed");
                }
                if (operation.rejection != null)
                {
                    return fail(operation.rejection);
                }
                nextAttemptAt = now + RETRY_DELAY_MS;
            }
            operation = null;
        }
        if (now < nextAttemptAt)
        {
            return state;
        }
        if (backend.isCancelling())
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
            return fail("walking could not start");
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
                    pending.rejection = rejection(backend.walkTo(pending.destination));
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
                if (pending.cancelled || pending.failure != null || pending.rejection != null)
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

    private static String rejection(WalkerState result)
    {
        if (result == WalkerState.UNREACHABLE)
        {
            return "Microbot found no walkable route";
        }
        return result == WalkerState.EXIT ? "the Microbot walker could not run" : null;
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
        backend.retryCancellation();
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

    public synchronized boolean isActive()
    {
        return state == State.WALKING;
    }

    public synchronized boolean isStopping()
    {
        return operation != null && operation.cancelled && !operation.finished || backend.isCancelling();
    }

    public synchronized String getCancellationFailure()
    {
        return backend.cancellationFailure();
    }

    interface Backend
    {
        boolean isClientThread();
        WorldPoint position();
        WalkerState walkTo(WorldPoint target);
        default boolean isCancelling()
        {
            return false;
        }
        default String cancellationFailure()
        {
            return null;
        }
        default void retryCancellation()
        {
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
        private volatile String rejection;
        private Future<?> future;

        private Operation(WorldPoint destination, ExecutorService executor)
        {
            this.destination = destination;
            this.executor = executor;
        }
    }

    private static final class NativeBackend implements Backend
    {
        private final Client client;
        private final ClientThread clientThread;
        private final BooleanSupplier eligible;
        private WorldPoint ownedTarget;
        private WorldPoint cancelledTarget;
        private CompletableFuture<Void> cleanup = CompletableFuture.completedFuture(null);

        private NativeBackend(Client client, ClientThread clientThread, BooleanSupplier eligible)
        {
            this.client = client;
            this.clientThread = clientThread;
            this.eligible = eligible;
        }

        @Override
        public boolean isClientThread()
        {
            return client != null && client.isClientThread();
        }

        @Override
        public WorldPoint position()
        {
            if (isClientThread())
            {
                return readPosition();
            }
            CompletableFuture<WorldPoint> snapshot = new CompletableFuture<>();
            clientThread.invokeLater(() -> {
                try
                {
                    snapshot.complete(readPosition());
                }
                catch (RuntimeException failure)
                {
                    snapshot.completeExceptionally(failure);
                }
            });
            try
            {
                return snapshot.get(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                return null;
            }
            catch (ExecutionException | TimeoutException failure)
            {
                throw new IllegalStateException("Could not validate walking session", failure);
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
        public WalkerState walkTo(WorldPoint target)
        {
            synchronized (this)
            {
                if (!cleanup.isDone() || Thread.currentThread().isInterrupted())
                {
                    return WalkerState.EXIT;
                }
                cleanup.join();
                WorldPoint active = Rs2Walker.getCurrentTarget();
                if (active != null && !active.equals(ownedTarget))
                {
                    return WalkerState.EXIT;
                }
                cancelledTarget = null;
                ownedTarget = target;
            }
            if (Thread.currentThread().isInterrupted())
            {
                return WalkerState.EXIT;
            }
            return Rs2Walker.walkWithState(target, 1);
        }

        @Override
        public synchronized boolean isCancelling()
        {
            return !cleanup.isDone();
        }

        @Override
        public synchronized String cancellationFailure()
        {
            return cleanup.isCompletedExceptionally() ? "Microbot could not stop walking" : null;
        }

        @Override
        public synchronized void retryCancellation()
        {
            if (cleanup.isCompletedExceptionally() && cancelledTarget != null)
            {
                cancel(cancelledTarget);
            }
        }

        @Override
        public synchronized void cancel(WorldPoint target)
        {
            if (target.equals(ownedTarget) || target.equals(cancelledTarget))
            {
                ownedTarget = null;
                cancelledTarget = target;
                // Microbot API route cleanup can wait for client-thread snapshots; keep it off that thread.
                cleanup = cleanup.handle((ignored, failure) -> null).thenRunAsync(() ->
                {
                    // Microbot API transports can change the active target during an owned trip.
                    // Ownership was checked above; clear its route even after retargeting.
                    Rs2Walker.clearWalkingRoute("fourtify:owned-walk-cancelled");
                });
            }
        }
    }
}
