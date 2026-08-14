package net.runelite.client.plugins.microbot.actions;

import net.runelite.client.plugins.microbot.actions.fixtures.FixtureState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Overlapping-tick prevention: {@link ActionScript#gameTick()} guards with an {@code AtomicBoolean}
 * so a second externally-driven tick that arrives while the previous one is still in flight is
 * dropped, and the guard is released once the in-flight tick finishes so the next one runs.
 *
 * <p>We drive this with a deferred executor that captures submitted tick bodies without running them,
 * which lets us hold a tick "in progress" deterministically (no threads, no sleeps) and observe how
 * many bodies were submitted.
 */
class ActionScriptTickTest {

    @Test
    void dropsAnOverlappingTickAndResumesAfterTheInFlightOneCompletes() {
        DeferredExecutor executor = new DeferredExecutor();
        ProbeActionScript script = new ProbeActionScript(executor);

        // Tick 1: guard is free, so exactly one tick body is submitted.
        script.gameTick();
        assertEquals(1, executor.queued.size(), "the first tick should submit one body");

        // Tick 2 arrives while tick 1's body has NOT run yet (still in progress): it must be dropped.
        script.gameTick();
        assertEquals(1, executor.queued.size(), "an overlapping tick must not submit a second body");

        // Let the in-flight body run. It short-circuits (not logged in) and its finally releases the guard.
        executor.runAll();

        // Tick 3: the previous tick has completed, so a fresh tick is allowed again.
        script.gameTick();
        assertEquals(1, executor.queued.size(), "after completion the guard is released and the next tick runs");
    }

    /** Concrete {@link ActionScript} that lets the test inject the executor; pipeline pieces are unused. */
    private static final class ProbeActionScript extends ActionScript<FixtureState> {
        ProbeActionScript(ScheduledExecutorService executor) {
            this.scheduledExecutorService = executor;
        }

        @Override
        protected Class<? extends Action<FixtureState>> actionType() {
            return null; // never reached: the tick body short-circuits before discovery/runner use
        }

        @Override
        protected FixtureState createState() {
            return null;
        }
    }

    /**
     * A {@link ScheduledExecutorService} that captures submitted tasks instead of running them, so the
     * test controls exactly when a tick body executes. {@code AbstractExecutorService.submit} routes
     * through {@link #execute}, so capturing there is enough for {@code gameTick()}'s {@code submit}.
     */
    private static final class DeferredExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        final List<Runnable> queued = new ArrayList<>();
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        /** Runs every captured task and clears the queue (mirrors the executor draining its work). */
        void runAll() {
            List<Runnable> copy = new ArrayList<>(queued);
            queued.clear();
            copy.forEach(Runnable::run);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = new ArrayList<>(queued);
            queued.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && queued.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        // Scheduling methods are unused by gameTick(); fail loudly if a future change starts relying on them.
        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}
