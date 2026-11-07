package net.runelite.client.plugins.microbot.geflipper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuleLedgerProfileSafetyTest {
    @Test
    void staleAndUnknownCallbacksCannotOverwriteAnotherProfilesTotalsForTheSameAccount() {
        FixedStore first = new FixedStore(101L, 100_000_000L);
        FixedStore second = new FixedStore(303L, 300_000_000L);
        MuleLedger ledger = new MuleLedger();
        ledger.startSession(first);
        ledger.recordReceivedForProfile(101L, 202L, 20_000_000L);
        assertEquals(120_000_000L, first.state.lifetimeGp);
        ledger.startSession(second);
        ledger.recordReceivedForProfile(101L, 202L, 20_000_000L);
        ledger.observeHoldingsForProfile(101L, 202L, 99_000_000L, 99_000_000L, true);
        ledger.recordReceivedForProfile(Long.MIN_VALUE, 202L, 20_000_000L);
        ledger.observeHoldingsForProfile(Long.MIN_VALUE, 202L, 99_000_000L, 99_000_000L, true);
        ledger.flush();
        assertEquals(300_000_000L, ledger.snapshot().lifetimeGp);
        assertEquals(0L, ledger.snapshot().sessionGp);
        assertEquals(15L, ledger.snapshot().heldGp);
        assertEquals(0, second.writes.get());
        ledger.recordReceivedForProfile(303L, 202L, 10_000_000L);
        assertEquals(310_000_000L, second.state.lifetimeGp);
        assertEquals(120_000_000L, first.state.lifetimeGp);
    }

    @Test
    void aProfileSwitchWaitsForAnAlreadyOwnedSaveToFinishInItsOriginalStore() throws Exception {
        MuleLedger ledger = new MuleLedger();
        FixedStore first = new FixedStore(101L, 100_000_000L);
        FixedStore second = new FixedStore(303L, 300_000_000L);
        CountDownLatch saving = new CountDownLatch(1), finishSave = new CountDownLatch(1), switching = new CountDownLatch(1);
        first.beforeSave = () -> { saving.countDown(); await(finishSave); };
        ledger.startSession(first);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> receipt = workers.submit(() -> ledger.recordReceivedForProfile(101L, 202L, 20_000_000L));
            assertTrue(saving.await(2, TimeUnit.SECONDS));
            Future<?> change = workers.submit(() -> { switching.countDown(); ledger.startSession(second); });
            assertTrue(switching.await(2, TimeUnit.SECONDS));
            assertFalse(change.isDone(), "Profile replacement must wait for the owned write");
            finishSave.countDown();
            receipt.get(2, TimeUnit.SECONDS);
            change.get(2, TimeUnit.SECONDS);
            assertEquals(120_000_000L, first.state.lifetimeGp);
            assertEquals(300_000_000L, ledger.snapshot().lifetimeGp);
            assertEquals(0, second.writes.get());
        } finally {
            finishSave.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void anOldCallbackQueuedDuringTheNewProfilesLoadIsRejectedAfterTheSwitch() throws Exception {
        MuleLedger ledger = new MuleLedger();
        FixedStore first = new FixedStore(101L, 100_000_000L);
        FixedStore second = new FixedStore(303L, 300_000_000L);
        ledger.startSession(first);
        CountDownLatch loading = new CountDownLatch(1), finishLoad = new CountDownLatch(1), callback = new CountDownLatch(1);
        second.beforeLoad = () -> { loading.countDown(); await(finishLoad); };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> change = workers.submit(() -> ledger.startSession(second));
            assertTrue(loading.await(2, TimeUnit.SECONDS));
            Future<?> stale = workers.submit(() -> {
                callback.countDown();
                ledger.recordReceivedForProfile(101L, 202L, 20_000_000L);
            });
            assertTrue(callback.await(2, TimeUnit.SECONDS));
            finishLoad.countDown();
            change.get(2, TimeUnit.SECONDS);
            stale.get(2, TimeUnit.SECONDS);
            assertEquals(300_000_000L, ledger.snapshot().lifetimeGp);
            assertEquals(0, second.writes.get());
            assertEquals(0, first.writes.get());
        } finally {
            finishLoad.countDown();
            workers.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(2, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    private static final class FixedStore implements MuleLedger.Store {
        final long profile;
        volatile MuleLedger.State state;
        final AtomicInteger writes = new AtomicInteger();
        Runnable beforeSave = () -> { }, beforeLoad = () -> { };
        FixedStore(long profile, long lifetime) {
            this.profile = profile;
            state = new MuleLedger.State(202L, 10L, 5L, lifetime);
        }
        public long profileId() { return profile; }
        public MuleLedger.State load() { beforeLoad.run(); return state; }
        public void save(MuleLedger.State value) { beforeSave.run(); state = value; writes.incrementAndGet(); }
    }
}
