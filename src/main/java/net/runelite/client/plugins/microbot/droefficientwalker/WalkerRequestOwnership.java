package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import net.runelite.api.coords.WorldPoint;

/**
 * Short, in-memory ownership checks. Never hold this monitor while waiting for the
 * client thread, reading collision data or preparing a route.
 */
final class WalkerRequestOwnership
{
    static final String LEGACY_OWNER = "legacy";

    static final class Request
    {
        final long id;
        final WorldPoint target;
        final String owner;
        // The initial danger boundary must survive engine route trimming/replans.
        volatile WorldPoint safetyOrigin;
        volatile boolean reachedSafety;
        int preparations;
        boolean cancelled;
        Runnable tickAction;

        private Request(long id, WorldPoint target, String owner)
        {
            this.id = id;
            this.target = target;
            this.owner = owner;
        }
    }

    private long epoch;
    private Request current;
    private long lastCancelled;
    private final ThreadLocal<Context> context = new ThreadLocal<>();

    private static final class Context
    {
        final Request request;
        final long epoch;
        Context(Request request, long epoch) { this.request = request; this.epoch = epoch; }
    }

    synchronized Request begin(WorldPoint target, String owner, boolean replace)
    {
        if (replace || current == null || !LEGACY_OWNER.equals(current.owner)
            || !Objects.equals(target, current.target))
        {
            current = new Request(++epoch, target, owner);
        }
        current.preparations++;
        return current;
    }

    synchronized void finished(Request request)
    {
        if (request != null && request.preparations > 0) request.preparations--;
    }

    synchronized boolean prepare(Request request)
    {
        if (!isCurrent(request)) return false;
        request.preparations++;
        return true;
    }

    synchronized Request current() { return current; }

    synchronized boolean isCurrent(Request request) { return request != null && current == request; }

    synchronized boolean isCurrent(long id) { return current != null && current.id == id; }

    synchronized boolean preparing() { return current != null && current.preparations > 0; }

    synchronized long epoch() { return epoch; }

    synchronized long cancel()
    {
        if (current != null)
        {
            current.cancelled = true;
            lastCancelled = current.id;
        }
        current = null;
        return ++epoch;
    }

    synchronized String detachedStatus(long id)
    {
        return id == lastCancelled ? "CANCELLED" : "REPLACED";
    }

    Request context() { return context.get() == null ? null : context.get().request; }

    boolean hasContext() { return context.get() != null; }

    Request capture()
    {
        Context bound = context.get();
        return bound != null ? bound.request : current();
    }

    synchronized long captureEpoch()
    {
        Context bound = context.get();
        return bound != null ? bound.epoch : epoch;
    }

    synchronized boolean isContextCurrent()
    {
        Context bound = context.get();
        return bound == null || bound.epoch == epoch
            && (bound.request == null || current == bound.request);
    }

    void check()
    {
        if (!isContextCurrent())
            throw new CancellationException("Walker request was replaced or cancelled");
        WalkerRequestCancellation.checkInterrupted();
    }

    Scope bind(Request request)
    {
        return bind(request, request == null ? epoch() : request.id);
    }

    Scope bind(Request request, long expectedEpoch)
    {
        Context previous = context.get();
        context.set(new Context(request, expectedEpoch));
        return () -> {
            if (previous == null) context.remove();
            else context.set(previous);
        };
    }

    interface Scope extends AutoCloseable
    {
        @Override void close();
    }
}
