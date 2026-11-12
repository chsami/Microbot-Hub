package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WalkerRouteSubmissionTest {
    private static final WorldPoint TARGET = new WorldPoint(3200, 3200, 0);
    static final class Job implements WalkerRouteSubmission.Cancellation {
        final Runnable callback;
        final int delay;
        int cancellations;
        Job(Runnable callback, int delay) { this.callback = callback; this.delay = delay; }
        public void cancel() { cancellations++; }
        // A cancelled timer may already have queued its callback.
        void fire() { callback.run(); }
    }
    static class Scheduler implements WalkerRouteSubmission.Scheduler {
        final List<Job> jobs = new ArrayList<>();
        int closes;
        boolean inline, reject;
        Runnable beforeReturn;
        public WalkerRouteSubmission.Cancellation schedule(Runnable callback, int delay) {
            if (reject) throw new RejectedExecutionException();
            Job job = new Job(callback, delay); jobs.add(job);
            if (inline) callback.run();
            if (beforeReturn != null) beforeReturn.run();
            return job;
        }
        public void close() { closes++; }
    }

    @Test void timingIncludesDispatchQueueAndDuplicateKeepsOriginalStartAndDelay() {
        Scheduler scheduler = new Scheduler(); List<Runnable> queue = new ArrayList<>();
        var nanos = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
        AtomicInteger delay = new AtomicInteger(15);
        var submission = new WalkerRouteSubmission(scheduler, delay::get, queue::add, nanos::get);
        var ticket = submission.submit(1, "walk", TARGET, t -> {
            assertEquals(15, t.requestedDelayMillis);
            assertEquals(87L, t.actualQueueMillis);
        });
        assertEquals(15, ticket.requestedDelayMillis); assertEquals(-1L, ticket.actualQueueMillis);
        nanos.addAndGet(15_000_000L); scheduler.jobs.get(0).fire();
        delay.set(250); nanos.addAndGet(20_000_000L);
        assertSame(ticket, submission.submit(1, "walk", TARGET, t -> fail("Duplicate callback")));
        assertEquals(-1L, ticket.actualQueueMillis);
        nanos.addAndGet(52_999_999L); queue.get(0).run();
        assertEquals(87L, ticket.actualQueueMillis);
    }

    @Test void synchronousPublicationObservesTimingBeforeSchedulerReturns() {
        Scheduler scheduler = new Scheduler(); scheduler.inline = true;
        var submission = new WalkerRouteSubmission(scheduler, () -> 250, Runnable::run, () -> 42L);
        var ticket = submission.submit(1, "walk", TARGET, t -> {
            assertEquals(250, t.requestedDelayMillis); assertEquals(0L, t.actualQueueMillis);
        });
        assertEquals(0L, ticket.actualQueueMillis);
    }

    @Test void cancelledQueuedPublicationHasNoActualPublicationTiming() {
        Scheduler scheduler = new Scheduler(); List<Runnable> queue = new ArrayList<>();
        var nanos = new java.util.concurrent.atomic.AtomicLong();
        var submission = new WalkerRouteSubmission(scheduler, () -> 15, queue::add, nanos::get);
        var ticket = submission.submit(1, "walk", TARGET, t -> fail("Cancelled callback"));
        scheduler.jobs.get(0).fire(); submission.cancel(); nanos.set(100_000_000L);
        queue.get(0).run(); assertEquals(-1L, ticket.actualQueueMillis);
    }

    @Test void acceptsBothDelayEndpointsAndPublishesOnlyOnce() {
        for (int delay : new int[]{15, 250}) {
            Scheduler scheduler = new Scheduler(); AtomicInteger publications = new AtomicInteger();
            var submission = new WalkerRouteSubmission(scheduler, () -> delay, Runnable::run);
            var ticket = submission.submit(1, "walk", TARGET, t -> publications.incrementAndGet());
            assertEquals(delay, scheduler.jobs.get(0).delay); assertTrue(submission.isCurrent(ticket));
            scheduler.jobs.get(0).fire(); scheduler.jobs.get(0).fire();
            assertEquals(1, publications.get()); assertFalse(submission.isPending());
        }
    }

    @Test void identicalPendingRequestKeepsOriginalTimerAndPublisher() {
        Scheduler scheduler = new Scheduler(); List<Runnable> queue = new ArrayList<>();
        AtomicInteger original = new AtomicInteger(), duplicate = new AtomicInteger(), delays = new AtomicInteger();
        var submission = new WalkerRouteSubmission(scheduler, () -> { delays.incrementAndGet(); return 15; }, queue::add);
        var first = submission.submit(1, "walk", TARGET, t -> original.incrementAndGet());
        scheduler.jobs.get(0).fire();
        assertSame(first, submission.submit(1, "walk", TARGET, t -> duplicate.incrementAndGet()));
        assertEquals(1, scheduler.jobs.size()); assertEquals(1, delays.get());
        queue.get(0).run(); queue.get(0).run();
        assertEquals(1, original.get()); assertEquals(0, duplicate.get());
    }

    @Test void eachOwnershipCommandOrTargetChangeReplacesAndCancelsPendingHandle() {
        Scheduler scheduler = new Scheduler(); AtomicInteger publications = new AtomicInteger();
        var submission = new WalkerRouteSubmission(scheduler, () -> 15, Runnable::run);
        var first = submission.submit(1, "walk", TARGET, t -> publications.incrementAndGet());
        submission.submit(2, "walk", TARGET, t -> publications.incrementAndGet());
        submission.submit(2, "bank", TARGET, t -> publications.incrementAndGet());
        var last = submission.submit(2, "bank", TARGET.dx(1), t -> publications.incrementAndGet());
        assertFalse(submission.isCurrent(first)); assertTrue(submission.isCurrent(last));
        assertTrue(submission.isPending(2)); assertFalse(submission.isPending(1));
        for (int i = 0; i < 3; i++) {
            assertEquals(1, scheduler.jobs.get(i).cancellations); scheduler.jobs.get(i).fire();
        }
        assertEquals(0, publications.get()); scheduler.jobs.get(3).fire();
        assertEquals(1, publications.get());
    }

    @Test void immediateCallbackBeforeHandleReturnStillReturnsAcceptedTicketAndReleasesHandle() {
        Scheduler scheduler = new Scheduler(); scheduler.inline = true;
        AtomicInteger publications = new AtomicInteger();
        var submission = new WalkerRouteSubmission(scheduler, () -> 15, Runnable::run);
        var ticket = submission.submit(1, "walk", TARGET, t -> {
            assertTrue(submission.isCurrent(t)); publications.incrementAndGet();
        });
        assertNotNull(ticket); assertFalse(submission.isCurrent(ticket));
        assertEquals(1, publications.get()); assertEquals(1, scheduler.jobs.get(0).cancellations);
    }

    @Test void cancelBeforeSchedulerReturnsReleasesLateHandleAndSuppressesCallback() {
        Scheduler scheduler = new Scheduler(); AtomicInteger publications = new AtomicInteger();
        var submission = new WalkerRouteSubmission(scheduler, () -> 15, Runnable::run);
        scheduler.beforeReturn = submission::cancel;
        assertNotNull(submission.submit(1, "walk", TARGET, t -> publications.incrementAndGet()));
        assertEquals(1, scheduler.jobs.get(0).cancellations);
        scheduler.jobs.get(0).fire(); assertEquals(0, publications.get());
    }

    @Test void queuedCallbackCannotPublishAfterCancelOrClose() {
        for (boolean close : new boolean[]{false, true}) {
            Scheduler scheduler = new Scheduler(); List<Runnable> queue = new ArrayList<>();
            AtomicInteger publications = new AtomicInteger();
            var submission = new WalkerRouteSubmission(scheduler, () -> 15, queue::add);
            submission.submit(1, "walk", TARGET, t -> publications.incrementAndGet());
            scheduler.jobs.get(0).fire(); assertEquals(1, queue.size());
            if (close) { submission.close(); submission.close(); } else submission.cancel();
            queue.get(0).run(); assertEquals(0, publications.get()); assertFalse(submission.isPending());
            assertEquals(1, scheduler.jobs.get(0).cancellations); assertEquals(close ? 1 : 0, scheduler.closes);
            if (close) assertNull(submission.submit(2, "walk", TARGET, t -> fail("Closed publication")));
        }
    }

    @Test void reentrantPublisherCanReplaceItselfWithoutOldCompletionClearingNewTicket() {
        Scheduler scheduler = new Scheduler(); AtomicInteger publications = new AtomicInteger();
        var submission = new WalkerRouteSubmission(scheduler, () -> 15, Runnable::run);
        submission.submit(1, "walk", TARGET, t ->
            submission.submit(2, "walk", TARGET, next -> publications.incrementAndGet()));
        scheduler.jobs.get(0).fire(); assertTrue(submission.isPending(2));
        scheduler.jobs.get(1).fire(); assertEquals(1, publications.get()); assertFalse(submission.isPending());
    }

    @Test void schedulerRejectionClearsTicketAndAllowsRetry() {
        Scheduler scheduler = new Scheduler(); scheduler.reject = true;
        var submission = new WalkerRouteSubmission(scheduler, () -> 15, Runnable::run);
        assertNull(submission.submit(1, "walk", TARGET, t -> fail("Rejected publication")));
        assertNull(submission.pending()); scheduler.reject = false;
        assertNotNull(submission.submit(1, "walk", TARGET, t -> {})); assertTrue(submission.isPending());
    }

    @Test void invalidDelayDoesNotReachSchedulerOrLeavePendingWork() {
        for (int delay : new int[]{14, 251}) {
            Scheduler scheduler = new Scheduler();
            var submission = new WalkerRouteSubmission(scheduler, () -> delay, Runnable::run);
            assertThrows(IllegalArgumentException.class, () -> submission.submit(1, "walk", TARGET, t -> {}));
            assertFalse(submission.isPending()); assertTrue(scheduler.jobs.isEmpty());
        }
    }

    @Test void publisherFailureClearsPendingAndDispatcherFailureAlsoClearsPending() {
        Scheduler scheduler = new Scheduler();
        var submission = new WalkerRouteSubmission(scheduler, () -> 15, Runnable::run);
        submission.submit(1, "walk", TARGET, t -> { throw new IllegalStateException("Publication failed"); });
        assertThrows(IllegalStateException.class, () -> scheduler.jobs.get(0).fire());
        assertFalse(submission.isPending());
        var rejected = new WalkerRouteSubmission(scheduler, () -> 15, callback -> { throw new RejectedExecutionException(); });
        rejected.submit(2, "walk", TARGET, t -> fail("Dispatcher rejected"));
        assertThrows(RejectedExecutionException.class, () -> scheduler.jobs.get(1).fire());
        assertFalse(rejected.isPending());
    }
}
