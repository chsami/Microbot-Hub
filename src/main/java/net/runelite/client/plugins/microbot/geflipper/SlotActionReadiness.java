package net.runelite.client.plugins.microbot.geflipper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Distinguishes a full menu-swap timeout from a cancelled or interrupted gesture. */
final class SlotActionReadiness {
    interface Wait {
        boolean until(BooleanSupplier condition, int timeoutMillis);
    }

    private SlotActionReadiness() {
    }

    static SlotActionExecutor.Readiness await(BooleanSupplier valid, BooleanSupplier ready,
                                             Wait wait, LongSupplier nanoTime, int timeoutMillis) {
        if (timeoutMillis <= 0 || !valid.getAsBoolean()) {
            return SlotActionExecutor.Readiness.CANCELLED;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        long started = nanoTime.getAsLong();
        boolean signalled = wait.until(() -> {
            if (cancelled.get()) return true;
            if (!valid.getAsBoolean()) {
                cancelled.set(true);
                return true;
            }
            return ready.getAsBoolean();
        }, timeoutMillis);
        if (cancelled.get() || !valid.getAsBoolean()) {
            return SlotActionExecutor.Readiness.CANCELLED;
        }
        if (signalled) {
            return ready.getAsBoolean() && valid.getAsBoolean()
                ? SlotActionExecutor.Readiness.READY : SlotActionExecutor.Readiness.CANCELLED;
        }
        long elapsed = nanoTime.getAsLong() - started;
        if (elapsed < TimeUnit.MILLISECONDS.toNanos(timeoutMillis) || !valid.getAsBoolean()) {
            return SlotActionExecutor.Readiness.CANCELLED;
        }
        return SlotActionExecutor.Readiness.TIMED_OUT;
    }
}
