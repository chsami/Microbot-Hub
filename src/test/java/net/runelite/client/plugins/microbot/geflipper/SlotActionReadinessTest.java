package net.runelite.client.plugins.microbot.geflipper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Readiness.CANCELLED;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Readiness.READY;
import static net.runelite.client.plugins.microbot.geflipper.SlotActionExecutor.Readiness.TIMED_OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotActionReadinessTest {
    private static final int TIMEOUT_MILLIS = 1800;
    private static final long TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);

    @Test
    void readyOperationDoesNotNeedToConsumeTheTimeout() {
        AtomicLong clock = new AtomicLong();

        assertEquals(READY, SlotActionReadiness.await(() -> true, () -> true,
            (condition, timeout) -> {
                assertEquals(TIMEOUT_MILLIS, timeout);
                assertTrue(condition.getAsBoolean());
                return true;
            }, clock::get, TIMEOUT_MILLIS));
    }

    @Test
    void onlyAFullTimeoutAllowsFallback() {
        for (long elapsed : new long[]{0, TIMEOUT_NANOS - 1, TIMEOUT_NANOS, TIMEOUT_NANOS + 1}) {
            AtomicLong clock = new AtomicLong();

            assertEquals(elapsed < TIMEOUT_NANOS ? CANCELLED : TIMED_OUT,
                SlotActionReadiness.await(() -> true, () -> false,
                    (condition, timeout) -> {
                        assertFalse(condition.getAsBoolean());
                        clock.set(elapsed);
                        return false;
                    }, clock::get, TIMEOUT_MILLIS));
        }
    }

    @Test
    void invalidInitialActionDoesNotReadReadinessOrStartWaiting() {
        assertEquals(CANCELLED, SlotActionReadiness.await(() -> false,
            () -> { throw new AssertionError("Readiness must not be read"); },
            (condition, timeout) -> { throw new AssertionError("Wait must not start"); },
            () -> { throw new AssertionError("Clock must not be read"); }, TIMEOUT_MILLIS));
    }

    @Test
    void cancellationStaysCancelledEvenIfTheActionBecomesValidAgain() {
        for (boolean signalled : new boolean[]{false, true}) {
            AtomicBoolean valid = new AtomicBoolean(true);
            AtomicLong clock = new AtomicLong();
            AtomicInteger readinessReads = new AtomicInteger();

            assertEquals(CANCELLED, SlotActionReadiness.await(valid::get,
                () -> { readinessReads.incrementAndGet(); return false; },
                (condition, timeout) -> {
                    valid.set(false);
                    assertTrue(condition.getAsBoolean());
                    valid.set(true);
                    assertTrue(condition.getAsBoolean());
                    clock.set(TIMEOUT_NANOS);
                    return signalled;
                }, clock::get, TIMEOUT_MILLIS));

            assertEquals(0, readinessReads.get());
        }
    }

    @Test
    void actionInvalidatedAfterWaitCannotClickOrFallback() {
        for (boolean signalled : new boolean[]{false, true}) {
            AtomicBoolean valid = new AtomicBoolean(true);
            AtomicLong clock = new AtomicLong();

            assertEquals(CANCELLED, SlotActionReadiness.await(valid::get, () -> true,
                (condition, timeout) -> {
                    valid.set(false);
                    clock.set(TIMEOUT_NANOS);
                    return signalled;
                }, clock::get, TIMEOUT_MILLIS));
        }
    }

    @Test
    void readyMenuThatChangesBeforeFinalValidationIsCancelled() {
        AtomicInteger reads = new AtomicInteger();

        assertEquals(CANCELLED, SlotActionReadiness.await(() -> true,
            () -> reads.incrementAndGet() == 1,
            (condition, timeout) -> {
                assertTrue(condition.getAsBoolean());
                return true;
            }, () -> 0, TIMEOUT_MILLIS));

        assertEquals(2, reads.get());
    }

    @Test
    void operationInvalidatedDuringTheFreshReadyCheckIsCancelled() {
        AtomicBoolean valid = new AtomicBoolean(true);

        assertEquals(CANCELLED, SlotActionReadiness.await(valid::get,
            () -> { valid.set(false); return true; },
            (condition, timeout) -> true, () -> 0, TIMEOUT_MILLIS));
    }

    @Test
    void invalidationDuringTheFinalClockReadIsCancelled() {
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicInteger reads = new AtomicInteger();

        assertEquals(CANCELLED, SlotActionReadiness.await(valid::get, () -> false,
            (condition, timeout) -> false, () -> {
                if (reads.incrementAndGet() == 1) return 0;
                valid.set(false);
                return TIMEOUT_NANOS;
            }, TIMEOUT_MILLIS));
    }

    @Test
    void monotonicClockWrapDoesNotPreventACompletedTimeout() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - TIMEOUT_NANOS / 2);

        assertEquals(TIMED_OUT, SlotActionReadiness.await(() -> true, () -> false,
            (condition, timeout) -> {
                clock.addAndGet(TIMEOUT_NANOS);
                return false;
            }, clock::get, TIMEOUT_MILLIS));
    }

    @Test
    void readinessFailurePropagatesInsteadOfBecomingATimeout() {
        IllegalStateException failure = new IllegalStateException("readiness unavailable");

        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> SlotActionReadiness.await(() -> true,
                () -> { throw failure; },
                (condition, timeout) -> condition.getAsBoolean(), () -> 0, TIMEOUT_MILLIS)));
    }

    @Test
    void waitFailurePropagatesInsteadOfBecomingATimeout() {
        IllegalStateException failure = new IllegalStateException("wait unavailable");

        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> SlotActionReadiness.await(() -> true, () -> false,
                (condition, timeout) -> { throw failure; }, () -> 0, TIMEOUT_MILLIS)));
    }

    @Test
    void invalidTimeoutDoesNotStartTheWait() {
        for (int timeout : new int[]{0, -1}) {
            assertEquals(CANCELLED, SlotActionReadiness.await(() -> true, () -> false,
                (condition, ignored) -> { throw new AssertionError("Wait must not start"); },
                () -> 0, timeout));
        }
    }
}
