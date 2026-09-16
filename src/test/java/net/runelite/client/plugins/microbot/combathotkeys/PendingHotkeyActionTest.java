package net.runelite.client.plugins.microbot.combathotkeys;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingHotkeyActionTest {
    @Test
    void discardedQueuedRequestDoesNotBlockRestart() {
        PendingHotkeyAction pending = new PendingHotkeyAction();
        Queue<Runnable> queue = new ArrayDeque<>();
        AtomicInteger casts = new AtomicInteger();
        assertTrue(pending.submit(queue::add, casts::incrementAndGet));
        assertFalse(pending.submit(queue::add, casts::incrementAndGet));
        pending.cancel();
        queue.clear(); // shutdownNow discards queued work without running its finally block
        assertTrue(pending.submit(queue::add, casts::incrementAndGet));
        queue.remove().run();
        assertEquals(1, casts.get());
    }

    @Test
    void oldQueuedRequestCannotCastOrReleaseNewRequest() {
        PendingHotkeyAction pending = new PendingHotkeyAction();
        Queue<Runnable> queue = new ArrayDeque<>();
        AtomicInteger casts = new AtomicInteger();
        pending.submit(queue::add, casts::incrementAndGet);
        pending.cancel();
        assertTrue(pending.submit(queue::add, casts::incrementAndGet));
        queue.remove().run();
        assertEquals(0, casts.get());
        assertFalse(pending.submit(queue::add, casts::incrementAndGet));
        queue.remove().run();
        assertEquals(1, casts.get());
    }

    @Test
    void oldRunningRequestCannotReleaseNewRequest() {
        PendingHotkeyAction pending = new PendingHotkeyAction();
        Queue<Runnable> queue = new ArrayDeque<>();
        pending.submit(queue::add, () -> {
            pending.cancel();
            assertTrue(pending.submit(queue::add, () -> {}));
        });
        queue.remove().run();
        assertFalse(pending.submit(queue::add, () -> {}));
        queue.remove().run();
        assertTrue(pending.submit(queue::add, () -> {}));
    }

    @Test
    void refusedOrRejectedSubmissionCanBeRetried() {
        PendingHotkeyAction pending = new PendingHotkeyAction();
        Queue<Runnable> queue = new ArrayDeque<>();
        assertFalse(pending.submit(action -> false, () -> fail("refused")));
        assertFalse(pending.submit(action -> { throw new RejectedExecutionException(); }, () -> fail("rejected")));
        assertTrue(pending.submit(queue::add, () -> {}));
    }

    @Test
    void failingActionReleasesRequest() {
        PendingHotkeyAction pending = new PendingHotkeyAction();
        Queue<Runnable> queue = new ArrayDeque<>();
        pending.submit(queue::add, () -> { throw new IllegalStateException(); });
        assertThrows(IllegalStateException.class, () -> queue.remove().run());
        assertTrue(pending.submit(queue::add, () -> {}));
    }
}
