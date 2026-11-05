package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.runelite.client.callback.ClientThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InteractionWorkerTest
{
    private final List<ManualExecutor> workers = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger starts = new AtomicInteger();
    private InteractionWorker worker;

    @BeforeEach
    public void setUp()
    {
        worker = new InteractionWorker(() ->
        {
            ManualExecutor executor = new ManualExecutor();
            workers.add(executor);
            return executor;
        }, BooleanSupplier::getAsBoolean);
    }

    @Test
    public void submissionDoesNotRunInlineAndCannotOverlapAnotherRequest()
    {
        assertTrue(submit());
        assertTrue(worker.isBusy());
        assertEquals(0, calls.get());
        assertFalse(submit());
        assertEquals(1, workers.size());
        workers.get(0).runAll();
        assertEquals(1, calls.get());
        assertEquals(1, starts.get());
        assertFalse(worker.isBusy());
        assertTrue(workers.get(0).isShutdown());
        assertTrue(submit());
        workers.get(1).runAll();
        assertEquals(2, calls.get());
    }

    @Test
    public void staleEligibilityNeverCountsOrCallsAHelper()
    {
        AtomicBoolean eligible = new AtomicBoolean(true);
        assertTrue(worker.submit(eligible::get, this::invoke, starts::incrementAndGet));
        eligible.set(false);
        workers.get(0).runAll();
        assertEquals(0, calls.get());
        assertEquals(0, starts.get());
        assertFalse(worker.isBusy());
        assertNull(worker.getFailure());
    }

    @Test
    public void onStartedCapturesBaselineBeforeTheHelperChangesIt()
    {
        AtomicInteger observed = new AtomicInteger(7);
        AtomicInteger baseline = new AtomicInteger();
        worker.submit(() -> true, () ->
        {
            assertEquals(7, baseline.get());
            observed.set(8);
            return true;
        }, () -> baseline.set(observed.get()));
        workers.get(0).runAll();
        assertEquals(7, baseline.get());
        assertEquals(8, observed.get());
    }

    @Test
    public void cancellationBeforeExecutorStartInvalidatesTheOldRequest()
    {
        submit();
        worker.cancel();
        assertFalse(worker.isBusy());
        assertTrue(submit());
        workers.get(0).runAll();
        assertEquals(0, calls.get());
        workers.get(1).runAll();
        assertEquals(1, calls.get());
        assertEquals(1, starts.get());
    }

    @Test
    public void aLateClientCallbackCannotRunItsOldStartHook()
    {
        AtomicReference<BooleanSupplier> delayed = new AtomicReference<>();
        InteractionWorker active = new InteractionWorker(() -> workers.get(0), check ->
        {
            delayed.set(check);
            return false;
        });
        workers.add(new ManualExecutor());
        active.submit(() -> true, this::invoke, starts::incrementAndGet);
        workers.get(0).runAll();
        assertFalse(delayed.get().getAsBoolean());
        active.cancel();
        assertFalse(delayed.get().getAsBoolean());
        assertEquals(0, calls.get());
        assertEquals(0, starts.get());
    }

    @Test
    public void cancellingFromTheStartHookPreventsTheHelper()
    {
        worker.submit(() -> true, this::invoke, worker::cancel);
        workers.get(0).runAll();
        assertEquals(0, calls.get());
        assertFalse(worker.isBusy());
        assertNull(worker.getFailure());
        // The manual executor runs on this test thread, so clear its cancellation interrupt.
        Thread.interrupted();
    }

    @Test
    public void helperExceptionIsLatchedUntilExplicitCancellation()
    {
        RuntimeException failure = new IllegalStateException("Microbot API failed");
        worker.submit(() -> true, () -> { throw failure; }, starts::incrementAndGet);
        workers.get(0).runAll();
        assertSame(failure, worker.getFailure());
        assertFalse(worker.isBusy());
        assertFalse(submit());
        worker.cancel();
        assertNull(worker.getFailure());
        assertTrue(submit());
        workers.get(1).runAll();
        assertEquals(1, calls.get());
    }

    @Test
    public void gateExceptionIsLatchedWithoutAHelperCall()
    {
        RuntimeException failure = new IllegalStateException("Client unavailable");
        InteractionWorker active = new InteractionWorker(() -> workers.get(0), check -> { throw failure; });
        workers.add(new ManualExecutor());
        active.submit(() -> true, this::invoke, starts::incrementAndGet);
        workers.get(0).runAll();
        assertSame(failure, active.getFailure());
        assertEquals(0, starts.get());
        assertEquals(0, calls.get());
        assertFalse(active.isBusy());
    }

    @Test
    @Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void productionGateDropsAQueuedClientCallbackWhenCancelled() throws Exception
    {
        CountDownLatch queued = new CountDownLatch(1);
        AtomicReference<Runnable> callback = new AtomicReference<>();
        ClientThread clientThread = new ClientThread()
        {
            @Override
            public void invokeLater(Runnable action)
            {
                callback.set(action);
                queued.countDown();
            }
        };
        InteractionWorker active = new InteractionWorker(clientThread);
        try
        {
            assertTrue(active.submit(() -> true, this::invoke, starts::incrementAndGet));
            assertTrue(queued.await(1, TimeUnit.SECONDS));
            active.cancel();
            callback.get().run();
            assertEquals(0, calls.get());
            assertEquals(0, starts.get());
            assertNull(active.getFailure());
        }
        finally
        {
            active.cancel();
        }
    }

    @Test
    public void rejectedExecutorIsReportedAndCanBeReset()
    {
        ManualExecutor executor = new ManualExecutor();
        executor.shutdown();
        InteractionWorker active = new InteractionWorker(() -> executor, BooleanSupplier::getAsBoolean);
        assertFalse(active.submit(() -> true, this::invoke, starts::incrementAndGet));
        assertTrue(active.getFailure() instanceof RejectedExecutionException);
        assertFalse(active.isBusy());
        active.cancel();
        assertNull(active.getFailure());
    }

    @Test
    @Timeout(value = 5_000, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void cancellationCannotOverlapAHelperThatIgnoresInterruption() throws Exception
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<ExecutorService> actualWorkers = new ArrayList<>();
        InteractionWorker active = new InteractionWorker(() ->
        {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            actualWorkers.add(executor);
            return executor;
        }, BooleanSupplier::getAsBoolean);
        try
        {
            assertTrue(active.submit(() -> true, () ->
            {
                started.countDown();
                awaitIgnoringInterrupt(release);
                throw new IllegalStateException("Cancelled Microbot API failure");
            }, starts::incrementAndGet));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            active.cancel();
            assertTrue(active.isBusy());
            assertFalse(active.submit(() -> true, this::invoke, starts::incrementAndGet));
            assertEquals(1, actualWorkers.size());
            release.countDown();
            assertTrue(actualWorkers.get(0).awaitTermination(1, TimeUnit.SECONDS));
            assertFalse(active.isBusy());
            assertNull(active.getFailure());
            assertTrue(active.submit(() -> true, this::invoke, starts::incrementAndGet));
            actualWorkers.get(1).shutdown();
            assertTrue(actualWorkers.get(1).awaitTermination(1, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
        finally
        {
            release.countDown();
            active.cancel();
            actualWorkers.forEach(ExecutorService::shutdownNow);
        }
    }

    private boolean submit()
    {
        return worker.submit(() -> true, this::invoke, starts::incrementAndGet);
    }

    private boolean invoke()
    {
        calls.incrementAndGet();
        return false;
    }

    private static void awaitIgnoringInterrupt(CountDownLatch release)
    {
        boolean waiting = true;
        while (waiting)
        {
            try
            {
                release.await();
                waiting = false;
            }
            catch (InterruptedException ignored)
            {
                // Model a helper that completes its own cleanup before returning.
            }
        }
    }

    private static final class ManualExecutor extends AbstractExecutorService
    {
        private final Deque<Runnable> actions = new ArrayDeque<>();
        private boolean shutdown;

        private void runAll()
        {
            while (!actions.isEmpty())
            {
                actions.removeFirst().run();
            }
        }

        @Override
        public void shutdown()
        {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow()
        {
            shutdown = true;
            return new ArrayList<>(actions);
        }

        @Override
        public boolean isShutdown()
        {
            return shutdown;
        }

        @Override
        public boolean isTerminated()
        {
            return shutdown && actions.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return isTerminated();
        }

        @Override
        public void execute(Runnable action)
        {
            if (shutdown)
            {
                throw new RejectedExecutionException("Worker stopped");
            }
            actions.addLast(action);
        }
    }
}
