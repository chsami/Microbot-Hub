package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.client.callback.ClientThread;
import javax.inject.Inject;

/** Serializes blocking Microbot API interactions without blocking the client thread. */
public class InteractionWorker
{
    private final Supplier<ExecutorService> workers;
    private final Function<BooleanSupplier, Boolean> clientGate;
    private volatile long generation;
    private volatile RuntimeException failure;
    private Pending active;

    @Inject
    public InteractionWorker(ClientThread clientThread)
    {
        this(() -> Executors.newSingleThreadExecutor(action ->
        {
            Thread thread = new Thread(action, "fourtifys-barrows-interaction");
            thread.setDaemon(true);
            return thread;
        }), check -> onClientThread(clientThread, check::getAsBoolean, "Could not validate Microbot interaction"));
    }

    InteractionWorker(Supplier<ExecutorService> workers, Function<BooleanSupplier, Boolean> clientGate)
    {
        this.workers = Objects.requireNonNull(workers);
        this.clientGate = Objects.requireNonNull(clientGate);
    }

    /** True means accepted; the task must observe the resulting game state separately. */
    public synchronized boolean submit(BooleanSupplier valid, BooleanSupplier operation, Runnable onStarted)
    {
        Objects.requireNonNull(valid);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(onStarted);
        if (failure != null || isBusy())
        {
            return false;
        }
        Pending pending = null;
        try
        {
            pending = new Pending(generation, workers.get());
            active = pending;
            Pending submitted = pending;
            pending.future = pending.executor.submit(() -> run(submitted, valid, operation, onStarted));
            return true;
        }
        catch (RuntimeException unavailable)
        {
            failure = unavailable;
            if (pending != null)
            {
                pending.executor.shutdownNow();
                pending.finished = true;
            }
            return false;
        }
    }

    private void run(Pending pending, BooleanSupplier valid, BooleanSupplier operation, Runnable onStarted)
    {
        if (!pending.started.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            if (!eligible(pending))
            {
                return;
            }
            boolean allowed = Boolean.TRUE.equals(clientGate.apply(() ->
            {
                synchronized (this)
                {
                    if (!eligible(pending) || !valid.getAsBoolean() || !eligible(pending))
                    {
                        return false;
                    }
                    onStarted.run();
                    return eligible(pending);
                }
            }));
            if (allowed && eligible(pending))
            {
                operation.getAsBoolean();
            }
        }
        catch (RuntimeException unavailable)
        {
            synchronized (this)
            {
                if (!pending.cancelled && pending.generation == generation)
                {
                    failure = unavailable;
                }
            }
        }
        finally
        {
            pending.executor.shutdown();
            pending.finished = true;
        }
    }

    private boolean eligible(Pending pending)
    {
        return !pending.cancelled && !pending.finished && pending.generation == generation
            && !Thread.currentThread().isInterrupted();
    }

    public synchronized boolean isBusy()
    {
        return active != null && !active.finished;
    }

    public RuntimeException getFailure()
    {
        return failure;
    }

    public synchronized void cancel()
    {
        generation++;
        failure = null;
        if (active == null || active.finished)
        {
            return;
        }
        active.cancelled = true;
        if (active.future != null)
        {
            active.future.cancel(true);
        }
        active.executor.shutdownNow();
        if (active.started.compareAndSet(false, true))
        {
            active.finished = true;
        }
    }

    /**
     * Runs a read on the client thread and waits up to five seconds for it. Returns null when interrupted;
     * a failed or late read throws an IllegalStateException with the given message.
     */
    public static <T> T onClientThread(ClientThread clientThread, Supplier<T> read, String unavailableMessage)
    {
        CompletableFuture<T> result = new CompletableFuture<>();
        clientThread.invokeLater(() ->
        {
            if (result.isDone())
            {
                return;
            }
            try
            {
                result.complete(read.get());
            }
            catch (RuntimeException failure)
            {
                result.completeExceptionally(failure);
            }
        });
        try
        {
            return result.get(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted)
        {
            result.cancel(false);
            Thread.currentThread().interrupt();
            return null;
        }
        catch (ExecutionException | TimeoutException unavailable)
        {
            result.cancel(false);
            throw new IllegalStateException(unavailableMessage, unavailable);
        }
    }

    private static final class Pending
    {
        private final long generation;
        private final ExecutorService executor;
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile boolean cancelled;
        private volatile boolean finished;
        private Future<?> future;

        private Pending(long generation, ExecutorService executor)
        {
            this.generation = generation;
            this.executor = Objects.requireNonNull(executor);
        }
    }
}
