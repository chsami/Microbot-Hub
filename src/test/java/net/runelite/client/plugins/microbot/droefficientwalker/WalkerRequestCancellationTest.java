package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WalkerRequestCancellationTest {
    public static void assertCancelled(Runnable action) {
        try { action.run(); fail("Expected cancellation"); }
        catch (CancellationException expected) { }
    }

    @Test public void cancellationInvalidatesPendingPlanningButAllowsFreshRequest() {
        WalkerRequestCancellation requests = new WalkerRequestCancellation();
        long pending = requests.capture();
        requests.cancel();
        assertCancelled(() -> requests.check(pending));
        requests.check(requests.capture());
    }

    @Test public void wrappedInterruptedClientLookupStopsInsteadOfFallingBack() {
        try {
            assertCancelled(() -> WalkerRequestCancellation.rethrowCancellation(
                new RuntimeException("Interrupted waiting for client thread", new InterruptedException())));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test public void alreadyInterruptedCallerCannotContinueAndOrdinaryFailureCanFallback() {
        WalkerRequestCancellation.rethrowCancellation(new IllegalStateException("ordinary failure"));
        try {
            Thread.currentThread().interrupt();
            assertCancelled(() -> WalkerRequestCancellation.rethrowCancellation(
                new RuntimeException("Interrupted waiting for client thread")));
        } finally { Thread.interrupted(); }
    }
}
