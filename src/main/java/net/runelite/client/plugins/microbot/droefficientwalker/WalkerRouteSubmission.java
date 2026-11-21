package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.coords.WorldPoint;

/** Staggers fresh route publication without waiting on the client thread. */
final class WalkerRouteSubmission implements AutoCloseable
{
    static final int MIN_DELAY_MILLIS = 15;
    static final int MAX_DELAY_MILLIS = 250;

    interface Cancellation
    {
        void cancel();
    }

    interface Scheduler extends AutoCloseable
    {
        Cancellation schedule(Runnable callback, int delayMillis);
        @Override void close();
    }

    static final class Ticket
    {
        final long ownerToken;
        final String command;
        final WorldPoint target;
        // -1 means scheduling/publication has not yet reached the corresponding stage.
        volatile int requestedDelayMillis = -1;
        volatile long actualQueueMillis = -1;
        private long queuedAtNanos;
        private final Consumer<Ticket> publication;
        private Cancellation scheduled;
        private boolean dispatched;
        private boolean publishing;

        private Ticket(long ownerToken, String command, WorldPoint target, Consumer<Ticket> publication)
        {
            this.ownerToken = ownerToken;
            this.command = command;
            this.target = target;
            this.publication = publication;
        }

        private boolean matches(long ownerToken, String command, WorldPoint target)
        {
            return this.ownerToken == ownerToken && this.command.equals(command)
                && Objects.equals(this.target, target);
        }
    }

    private final Scheduler scheduler;
    private final IntSupplier delayMillis;
    private final LongSupplier nanoClock;
    private final Consumer<Runnable> dispatcher;
    private Ticket pending;
    private boolean closed;

    WalkerRouteSubmission(Consumer<Runnable> dispatcher)
    {
        this(new DelayedScheduler(),
            () -> ThreadLocalRandom.current().nextInt(MIN_DELAY_MILLIS, MAX_DELAY_MILLIS + 1), dispatcher);
    }

    WalkerRouteSubmission(Scheduler scheduler, IntSupplier delayMillis, Consumer<Runnable> dispatcher)
    {
        this(scheduler, delayMillis, dispatcher, System::nanoTime);
    }

    WalkerRouteSubmission(Scheduler scheduler, IntSupplier delayMillis, Consumer<Runnable> dispatcher,
                          LongSupplier nanoClock)
    {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.delayMillis = Objects.requireNonNull(delayMillis, "delayMillis");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /**
     * Identical pending work keeps its original timer and publication callback. A new
     * ticket replaces other pending work. The publisher must also check isCurrent(ticket)
     * under its navigation lock immediately before mutating engine state.
     */
    Ticket submit(long ownerToken, String command, WorldPoint target, Consumer<Ticket> publication)
    {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(publication, "publication");
        Ticket ticket;
        Cancellation previous;
        synchronized (this)
        {
            if (closed) return null;
            if (pending != null && pending.matches(ownerToken, command, target)) return pending;
            previous = takeScheduled(pending);
            ticket = new Ticket(ownerToken, command, target, publication);
            pending = ticket;
        }
        try
        {
            cancel(previous);
            if (!isCurrent(ticket)) return null;
            int delay = delayMillis.getAsInt();
            if (delay < MIN_DELAY_MILLIS || delay > MAX_DELAY_MILLIS)
                throw new IllegalArgumentException("Route submission delay must be between 15 and 250 ms");
            if (!isCurrent(ticket)) return null;
            ticket.requestedDelayMillis = delay;
            ticket.queuedAtNanos = nanoClock.getAsLong();
            Cancellation scheduled = Objects.requireNonNull(
                scheduler.schedule(() -> dispatch(ticket), delay), "scheduled cancellation");
            boolean current;
            synchronized (this)
            {
                current = isCurrent(ticket);
                if (current) ticket.scheduled = scheduled;
            }
            // cancel/close/replacement can race with the scheduler returning its handle.
            if (!current) scheduled.cancel();
            // The scheduler accepted the ticket even if an immediate dispatcher already
            // completed it. Later ownership is determined by isCurrent, not this return.
            return ticket;
        }
        catch (RejectedExecutionException rejected)
        {
            complete(ticket);
            return null;
        }
        catch (RuntimeException | Error failure)
        {
            complete(ticket);
            throw failure;
        }
    }

    synchronized Ticket pending() { return pending; }

    synchronized boolean isPending() { return pending != null; }

    synchronized boolean isPending(long ownerToken)
    {
        return pending != null && pending.ownerToken == ownerToken;
    }

    synchronized boolean isCurrent(Ticket ticket)
    {
        return !closed && ticket != null && pending == ticket;
    }

    void cancel()
    {
        Cancellation scheduled;
        synchronized (this)
        {
            scheduled = takeScheduled(pending);
            pending = null;
        }
        cancel(scheduled);
    }

    @Override public void close()
    {
        Cancellation scheduled;
        synchronized (this)
        {
            if (closed) return;
            closed = true;
            scheduled = takeScheduled(pending);
            pending = null;
        }
        try
        {
            cancel(scheduled);
        }
        finally
        {
            scheduler.close();
        }
    }

    private void dispatch(Ticket ticket)
    {
        synchronized (this)
        {
            if (!isCurrent(ticket) || ticket.dispatched) return;
            ticket.dispatched = true;
        }
        try
        {
            dispatcher.accept(() -> publish(ticket));
        }
        catch (RuntimeException | Error failure)
        {
            complete(ticket);
            throw failure;
        }
    }

    private void publish(Ticket ticket)
    {
        synchronized (this)
        {
            if (!isCurrent(ticket) || ticket.publishing) return;
            ticket.publishing = true;
        }
        try
        {
            // Include both timer delay and the dispatcher's queue before actual publication.
            ticket.actualQueueMillis = TimeUnit.NANOSECONDS.toMillis(
                Math.max(0L, nanoClock.getAsLong() - ticket.queuedAtNanos));
            ticket.publication.accept(ticket);
        }
        finally
        {
            complete(ticket);
        }
    }

    private synchronized void complete(Ticket ticket)
    {
        ticket.scheduled = null;
        if (pending == ticket) pending = null;
    }

    /** The caller holds this monitor; cancellation itself always runs after releasing it. */
    private Cancellation takeScheduled(Ticket ticket)
    {
        if (ticket == null) return null;
        Cancellation scheduled = ticket.scheduled;
        ticket.scheduled = null;
        return scheduled;
    }

    private static void cancel(Cancellation scheduled)
    {
        if (scheduled != null) scheduled.cancel();
    }

    private static final class DelayedScheduler implements Scheduler
    {
        private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "EfficientWalker-route-submission");
            thread.setDaemon(true);
            return thread;
        });

        private DelayedScheduler()
        {
            executor.setRemoveOnCancelPolicy(true);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override public Cancellation schedule(Runnable callback, int delayMillis)
        {
            java.util.concurrent.ScheduledFuture<?> task = executor.schedule(callback, delayMillis, TimeUnit.MILLISECONDS);
            return () -> task.cancel(false);
        }

        @Override public void close()
        {
            executor.shutdownNow();
        }
    }
}
