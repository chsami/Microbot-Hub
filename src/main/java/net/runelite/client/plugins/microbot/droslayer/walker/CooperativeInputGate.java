package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nonblocking handoff gate for a caller's complete input-producing steps.
 * Suspending fails while any entered step is still in progress. This includes helpers
 * which wait internally; do not lend navigation until they have returned.
 */
public final class CooperativeInputGate {
    private final AtomicInteger steps = new AtomicInteger();

    public final class Step implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private Step() { }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) steps.decrementAndGet();
        }
    }

    /** Null means navigation owns the gate; skip the complete caller step. */
    public Step enterStep() {
        int current;
        do {
            current = steps.get();
            if (current < 0 || current == Integer.MAX_VALUE) return null;
        } while (!steps.compareAndSet(current, current + 1));
        return new Step();
    }

    /** Call outside an entered step. It never waits for existing input to finish. */
    public boolean trySuspend() { return steps.compareAndSet(0, -1); }
    public boolean isSuspended() { return steps.get() < 0; }

    /** A rejected request started no voyage, so its still-suspended gate can be reopened. */
    public boolean resumeAfterRejection(NavigationSessions.Result result) {
        return result != null && !result.accepted && steps.compareAndSet(-1, 0);
    }

    /** Resume only after a confirmed stationary arrival or completed stop cleanup. */
    public boolean resumeAfterRelease(NavigationSessions.Snapshot status) {
        return status != null && status.released
                && (status.state == NavigationSessions.State.ARRIVED
                || status.state == NavigationSessions.State.CANCELLED
                || status.state == NavigationSessions.State.FAILED)
                && steps.compareAndSet(-1, 0);
    }
}
