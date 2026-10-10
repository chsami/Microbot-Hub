package net.runelite.client.plugins.microbot.actions;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.actions.fixtures.FixtureState;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link ActionScript#gameTick()} guard behaviour. Externally-driven ticks are dispatched to the
 * script's executor (never run inline on the caller/client thread), an {@code AtomicBoolean} guard
 * drops a tick that arrives while the previous one is still in flight, the guard is released once the
 * in-flight tick finishes (including the logged-out/short-circuit path), and a rejected submission does
 * not leave the guard permanently stuck.
 *
 * <p>Most cases use a deferred executor that captures submitted tick bodies without running them, which
 * lets us hold a tick "in progress" deterministically (no threads, no sleeps) and observe submissions.
 */
class ActionScriptTickTest {

    @Test
    void tickBodyIsDispatchedToTheExecutorAndNotRunInline() {
        DeferredExecutor executor = new DeferredExecutor();
        ProbeActionScript script = new ProbeActionScript(executor);

        script.gameTick();

        // The body was handed to the executor and is still pending — gameTick() did not run it on the
        // calling thread. In production that executor is the script's worker pool, off the client thread.
        assertEquals(1, executor.queued.size(), "gameTick must submit the tick body to the executor");
        assertEquals(0, script.createStateCalls(), "the tick body must not have executed inline");
    }

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

    @Test
    void loggedOutTickSkipsCleanlyAndReleasesTheGuard() {
        DeferredExecutor executor = new DeferredExecutor();
        ProbeActionScript script = new ProbeActionScript(executor);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(false);

            script.gameTick();
            executor.runAll(); // runs the body within the mocked-static scope (same thread)

            // Logged out: the body returned before touching the pipeline — no state was built, no throw.
            assertEquals(0, script.createStateCalls(), "a logged-out tick must not build state or run the pipeline");

            // ...and the guard was released, so the next tick is accepted rather than stuck.
            script.gameTick();
            assertEquals(1, executor.queued.size(), "the guard must be released after a logged-out tick");
        }
    }

    @Test
    void uninitialisedTickDoesNotThrowAndReleasesTheGuard() {
        DeferredExecutor executor = new DeferredExecutor();
        // Note: initialize() is deliberately NOT called, so the runner is null.
        ProbeActionScript script = new ProbeActionScript(executor);

        script.gameTick();
        executor.runAll(); // must not throw even though the pipeline was never built

        script.gameTick();
        assertEquals(1, executor.queued.size(), "an uninitialised tick still releases the guard cleanly");
    }

    @Test
    void rejectedSubmissionDoesNotLeaveTheGuardPermanentlyStuck() {
        DeferredExecutor executor = new DeferredExecutor();
        ProbeActionScript script = new ProbeActionScript(executor);

        // Simulate a shut-down executor: the first submission is rejected.
        executor.rejectNextSubmit = true;
        assertThrows(RejectedExecutionException.class, script::gameTick,
                "a rejected submission should surface, not be swallowed");

        // Crucially the guard was released despite the failed submit, so ticking can resume.
        executor.rejectNextSubmit = false;
        script.gameTick();
        assertEquals(1, executor.queued.size(),
                "after a rejected submission the guard must be free so the next tick submits");
    }

    /** Concrete {@link ActionScript} that lets the test inject the executor and count pipeline entry. */
    private static final class ProbeActionScript extends ActionScript<FixtureState> {
        private int createStateCalls;

        ProbeActionScript(ScheduledExecutorService executor) {
            this.scheduledExecutorService = executor;
        }

        int createStateCalls() {
            return createStateCalls;
        }

        @Override
        protected Class<? extends Action<FixtureState>> actionType() {
            return null; // never reached: the tick body short-circuits before discovery/runner use
        }

        @Override
        protected FixtureState createState() {
            createStateCalls++; // only reached if logged in AND initialised — used to prove skip paths
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
        boolean rejectNextSubmit;
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            if (rejectNextSubmit) {
                // Mirror a shut-down executor rejecting work; AbstractExecutorService.submit propagates this.
                throw new RejectedExecutionException("executor rejected the task");
            }
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
