package net.runelite.client.plugins.microbot.geflipper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class MuleConnectionMonitorTest {
    @Test
    void senderPollsInBackgroundWithoutOverlappingOrReservingAWorkerForEachUpdate() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Thread> probeThread = new AtomicReference<>();
        try (MuleConnectionMonitor monitor = new MuleConnectionMonitor((expected, own, port) -> {
            calls.incrementAndGet();
            probeThread.set(Thread.currentThread());
            entered.countDown();
            try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return reply(true, true, "TargetMule", 301, "");
        })) {
            monitor.updateSender(1, "TestFlipper", "TargetMule", 43599);
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertNotEquals(Thread.currentThread(), probeThread.get());
            assertEquals(MuleConnectionMonitor.State.CHECKING, monitor.snapshot().state);
            for (int i = 0; i < 20; i++) monitor.updateSender(1, "TestFlipper", "TargetMule", 43599);
            assertEquals(1, calls.get());
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (monitor.snapshot().state == MuleConnectionMonitor.State.CHECKING && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(5);
            }
            assertEquals(MuleConnectionMonitor.State.READY, monitor.snapshot().state);
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void identityReadinessAndBusyStatesAreKeptSeparate() {
        Object[][] cases = {
            {reply(true, true, "TargetMule", 301, ""), MuleConnectionMonitor.State.READY, true},
            {reply(true, false, "TargetMule", 301, ""), MuleConnectionMonitor.State.STANDBY, true},
            {reply(true, true, "TargetMule", 301, "OtherSender"), MuleConnectionMonitor.State.BUSY, false},
            {reply(true, false, "TargetMule", 301, "TestFlipper"), MuleConnectionMonitor.State.STANDBY, true},
            {reply(false, false, "TargetMule", 0, ""), MuleConnectionMonitor.State.NEEDS_SETUP, false},
            {reply(false, false, "", 0, ""), MuleConnectionMonitor.State.NEEDS_SETUP, false},
            {reply(true, true, "OtherMule", 301, ""), MuleConnectionMonitor.State.WRONG_MULE, false},
            {MuleNetwork.noResponse("192.0.2.1", "unavailable"), MuleConnectionMonitor.State.OFFLINE, false}
        };
        for (Object[] test : cases) {
            ManualScheduler scheduler = new ManualScheduler();
            try (MuleConnectionMonitor monitor = new MuleConnectionMonitor((expected, own, port) ->
                (MuleNetwork.MuleReply) test[0], () -> 1_000L, scheduler)) {
                monitor.updateSender(1, "TestFlipper", "TargetMule", 43599);
                scheduler.runLatest();
                assertEquals(test[1], monitor.snapshot().state);
                assertEquals(test[2], monitor.snapshot().availableForPreparation);
                assertFalse(monitor.snapshot().label().contains("TargetMule"));
                assertFalse(monitor.snapshot().label().contains("192.0.2"));
            }
        }
    }

    @Test
    void aCachedSuccessExpiresAfterFortyFiveSecondsWithoutRenderingNetworkCalls() {
        long[] now = {1_000L};
        AtomicInteger calls = new AtomicInteger();
        ManualScheduler scheduler = new ManualScheduler();
        try (MuleConnectionMonitor monitor = new MuleConnectionMonitor((expected, own, port) -> {
            calls.incrementAndGet();
            return reply(true, true, "TargetMule", 301, "");
        }, () -> now[0], scheduler)) {
            monitor.updateSender(1, "TestFlipper", "TargetMule", 43599);
            assertEquals(15_000L, scheduler.periodMillis);
            scheduler.runLatest();
            now[0] += MuleConnectionMonitor.FRESHNESS_MS - 1;
            assertTrue(monitor.snapshot().connected);
            now[0]++;
            assertEquals(MuleConnectionMonitor.State.STALE, monitor.snapshot().state);
            assertFalse(monitor.snapshot().connected);
            assertFalse(monitor.snapshot().availableForPreparation);
            assertEquals(1, calls.get());
            scheduler.runLatest();
            assertEquals(MuleConnectionMonitor.State.READY, monitor.snapshot().state);
            assertEquals(2, calls.get());
        }
    }

    private enum Change { TARGET, ACCOUNT, PORT, RECEIVER, OFF, RESET, CLOSE }

    @ParameterizedTest
    @EnumSource(Change.class)
    void oldInFlightResultsCannotOverwriteReplacementOwnership(Change change) {
        ManualScheduler scheduler = new ManualScheduler();
        AtomicReference<MuleConnectionMonitor> owner = new AtomicReference<>();
        AtomicBoolean first = new AtomicBoolean(true);
        try (MuleConnectionMonitor monitor = new MuleConnectionMonitor((expected, own, port) -> {
            if (first.getAndSet(false)) {
                switch (change) {
                    case TARGET: owner.get().updateSender(1, "TestFlipper", "Replacement", 43599); break;
                    case ACCOUNT: owner.get().updateSender(2, "TestFlipper", "TargetMule", 43599); break;
                    case PORT: owner.get().updateSender(1, "TestFlipper", "TargetMule", 43600); break;
                    case RECEIVER: owner.get().updateReceiver(new MuleNetwork.ServerHealth(true, true, true, false, false, false)); break;
                    case OFF: owner.get().disable(); break;
                    case RESET: owner.get().reset(); break;
                    case CLOSE: owner.get().close(); break;
                    default: throw new AssertionError(change);
                }
            }
            return reply(true, true, expected, 301, "");
        }, () -> 1_000L, scheduler)) {
            owner.set(monitor);
            monitor.updateSender(1, "TestFlipper", "TargetMule", 43599);
            scheduler.runLatest();
            MuleConnectionMonitor.State expected = change == Change.RECEIVER ? MuleConnectionMonitor.State.LISTENING
                : change == Change.OFF || change == Change.RESET || change == Change.CLOSE ? MuleConnectionMonitor.State.OFF
                : MuleConnectionMonitor.State.CHECKING;
            assertEquals(expected, monitor.snapshot().state);
            assertFalse(monitor.snapshot().availableForPreparation);
        }
    }

    @Test
    void receiverHealthIsPassiveAndRecentContactDoesNotMeanItCanPrepareASenderTransfer() {
        AtomicInteger calls = new AtomicInteger();
        try (MuleConnectionMonitor monitor = new MuleConnectionMonitor((expected, own, port) -> {
            calls.incrementAndGet();
            return null;
        }, () -> 1_000L, new ManualScheduler())) {
            monitor.updateReceiver(new MuleNetwork.ServerHealth(true, true, true, false, false, false));
            assertEquals(MuleConnectionMonitor.State.LISTENING, monitor.snapshot().state);
            monitor.updateReceiver(new MuleNetwork.ServerHealth(true, true, true, false, true, false));
            assertEquals(MuleConnectionMonitor.State.SENDER_SEEN, monitor.snapshot().state);
            assertTrue(monitor.snapshot().connected);
            assertFalse(monitor.snapshot().availableForPreparation);
            monitor.updateReceiver(new MuleNetwork.ServerHealth(true, false, true, false, true, false));
            assertEquals(MuleConnectionMonitor.State.NEEDS_SETUP, monitor.snapshot().state);
            assertFalse(monitor.snapshot().connected);
            assertEquals(0, calls.get());
        }
    }

    @Test
    void invalidOrUnknownSenderIdentityDoesNotStartAProbe() {
        ManualScheduler scheduler = new ManualScheduler();
        try (MuleConnectionMonitor monitor = new MuleConnectionMonitor((expected, own, port) ->
            fail("Invalid ownership must not probe"), () -> 1_000L, scheduler)) {
            monitor.updateSender(0, "TestFlipper", "TargetMule", 43599);
            assertEquals(MuleConnectionMonitor.State.NEEDS_SETUP, monitor.snapshot().state);
            monitor.updateSender(1, "", "TargetMule", 43599);
            monitor.updateSender(1, "TestFlipper", "Bad|Name", 43599);
            monitor.updateSender(1, "TestFlipper", "TestFlipper", 43599);
            assertTrue(scheduler.tasks.isEmpty());
        }
    }

    private static MuleNetwork.MuleReply reply(boolean ok, boolean ready, String mule, int world, String lease) {
        return new MuleNetwork.MuleReply(ok, ready, mule, world, lease, "192.0.2.1", "private message ignored");
    }

    private static final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        final List<Task> tasks = new ArrayList<>();
        long periodMillis;
        boolean shutdown;

        void runLatest() { tasks.get(tasks.size() - 1).run(); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long delay, long period, TimeUnit unit) {
            assertEquals(0, delay);
            periodMillis = unit.toMillis(period);
            Task task = new Task(command);
            tasks.add(task);
            return task;
        }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
        @Override public void execute(Runnable command) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long delay, long period, TimeUnit unit) { throw new UnsupportedOperationException(); }
    }

    private static final class Task implements ScheduledFuture<Object> {
        final Runnable command;
        boolean cancelled;
        Task(Runnable command) { this.command = command; }
        void run() { if (!cancelled) command.run(); }
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
        @Override public boolean cancel(boolean interrupt) { cancelled = true; return true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return cancelled; }
        @Override public Object get() { throw new UnsupportedOperationException(); }
        @Override public Object get(long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
    }
}
