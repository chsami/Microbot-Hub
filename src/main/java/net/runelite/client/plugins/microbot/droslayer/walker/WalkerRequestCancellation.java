package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

/** A cancelled planning request must never resume as a new route after its caller stops. */
final class WalkerRequestCancellation {
    private final AtomicLong generation = new AtomicLong();
    long capture() { return generation.get(); }
    void cancel() { generation.incrementAndGet(); }
    void check(long request) {
        checkInterrupted();
        if (generation.get() != request) throw new CancellationException("Walker request cancelled");
    }
    static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Walker caller interrupted");
    }
    static void rethrowCancellation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Walker client-thread lookup interrupted");
            }
            if (cause instanceof CancellationException) throw (CancellationException) cause;
        }
        checkInterrupted();
    }
}
