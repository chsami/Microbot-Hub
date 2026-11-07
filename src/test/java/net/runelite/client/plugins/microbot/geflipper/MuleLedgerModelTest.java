package net.runelite.client.plugins.microbot.geflipper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repeatable random histories for {@link MuleLedger}, checked against a second, deliberately plain description
 * of the rules: receipts, holdings readings, unreported accounts, other accounts, rejected writes, restarts
 * with and without a clean shutdown. {@link MuleLedgerTest} holds the hand-picked cases; this holds the
 * combinations nobody thought to write down.
 */
class MuleLedgerModelTest {
    /** Most histories belong to one real account plus readings taken before the client reported it. */
    private static final long[] ONE_ACCOUNT = {202L, 202L, 202L, 202L, 202L, 202L, 202L, 0L, -1L, -1L};
    /** The rest see other accounts appear on the same profile. */
    private static final long[] SEVERAL_ACCOUNTS = {202L, 202L, 202L, 202L, 202L, 202L, 303L, 404L, 0L, -1L};
    private static final int HISTORIES = 1_500;
    private static final int OPERATIONS = 220;

    @Test
    void randomHistoriesAlwaysMatchTheRulesAndNeverLoseAnAllTimeTotal() {
        Tally tally = new Tally();
        for (long seed = 1; seed <= HISTORIES; seed++) play(seed, tally);
        assertTrue(tally.receipts >= 50_000, tally.toString());
        assertTrue(tally.observations >= 50_000, tally.toString());
        assertTrue(tally.rebinds >= 9_000, tally.toString());
        assertTrue(tally.unreportedAccounts >= 25_000, tally.toString());
        assertTrue(tally.cleanRestarts >= 5_000, tally.toString());
        assertTrue(tally.crashRestarts >= 5_000, tally.toString());
        assertTrue(tally.refusedWrites >= 30_000, tally.toString());
        assertTrue(tally.quietReadings >= 40_000, tally.toString());
        assertTrue(tally.writes >= 70_000, tally.toString());
    }

    private static void play(long seed, Tally tally) {
        Random random = new Random(seed);
        Clock clock = new Clock();
        SimStore store = new SimStore(clock);
        MuleLedger ledger = new MuleLedger(clock);
        ledger.attachStore(store);
        ledger.startSession();
        Model model = new Model();
        store.rebindProbe = () -> model.rebinds;
        model.reload(store.stored, clock.now);
        store.newEpoch();
        String who = "seed " + seed;
        long[] accounts = random.nextInt(10) < 7 ? ONE_ACCOUNT : SEVERAL_ACCOUNTS;

        for (int step = 0; step < OPERATIONS; step++) {
            String op;
            store.attemptsThisOp = 0;
            store.refusedThisOp = 0;
            int roll = random.nextInt(100);
            if (roll < 30) {
                long account = accounts[random.nextInt(accounts.length)];
                long gp = random.nextInt(6) == 0 ? random.nextInt(11) - 5 : 1 + random.nextInt(5_000_000);
                op = "receive(" + account + ", " + gp + ")";
                int rebindsBefore = model.rebinds;
                if (gp > 0) model.receive(account, gp, clock.now);
                ledger.recordReceived(account, gp);
                tally.receipts++;
                tally.rebinds += model.rebinds - rebindsBefore;
                if (account == 0L || account == -1L) tally.unreportedAccounts++;
                if (gp > 0) {
                    assertEquals(1, store.attemptsThisOp, who + " " + op + ": a receipt is always written");
                } else {
                    assertEquals(0, store.attemptsThisOp, who + " " + op + ": nothing was received");
                }
            } else if (roll < 62) {
                long account = accounts[random.nextInt(accounts.length)];
                long inventory = random.nextInt(8) == 0 ? -random.nextInt(10) : random.nextInt(10_000_000);
                long bank = random.nextInt(8) == 0 ? -random.nextInt(10) : random.nextInt(1_000_000_000);
                boolean known = random.nextBoolean();
                op = "observe(" + account + ", " + inventory + ", " + bank + ", " + known + ")";
                int rebindsBefore = model.rebinds;
                model.observe(account, inventory, bank, known, clock.now);
                ledger.observeHoldings(account, inventory, bank, known);
                tally.observations++;
                tally.rebinds += model.rebinds - rebindsBefore;
                if (account == 0L || account == -1L) tally.unreportedAccounts++;
                assertTrue(store.attemptsThisOp <= 1, who + " " + op + ": one reading, one write at most");
                if (store.attemptsThisOp == 1 && store.epochAttempts > 1) {
                    assertTrue(clock.now - store.previousAttemptAt >= MuleLedger.SAVE_INTERVAL_MS,
                        who + " " + op + ": a reading wrote only " + (clock.now - store.previousAttemptAt)
                            + " ms after the previous write");
                }
            } else if (roll < 78) {
                long advance = random.nextInt(4) == 0 ? 0 : random.nextInt(4) == 0 ? random.nextInt(4 * 3_600_000)
                    : random.nextInt(90_000);
                op = "advance(" + advance + ")";
                clock.now += advance;
            } else if (roll < 86) {
                op = "flush()";
                ledger.flush();
                assertTrue(store.attemptsThisOp <= 1, who + " " + op);
                if (!store.failSave) {
                    assertEquals(model.persistent(), SimStore.describe(store.stored),
                        who + " " + op + ": after a flush the store holds exactly what the ledger knows");
                }
            } else if (roll < 91) {
                op = "toggleStoreFailure(" + !store.failSave + ")";
                store.failSave = !store.failSave;
            } else if (roll < 94) {
                op = "newSession()";
                ledger.startSession();
                model.reload(store.stored, clock.now);
                store.newEpoch();
            } else if (roll < 97) {
                op = "cleanRestart()";
                boolean writable = !store.failSave;
                String before = model.persistent();
                ledger.flush();
                ledger = new MuleLedger(clock);
                ledger.attachStore(store);
                ledger.startSession();
                model.reload(store.stored, clock.now);
                store.newEpoch();
                tally.cleanRestarts++;
                if (writable) {
                    assertEquals(before, model.persistent(),
                        who + " " + op + ": a restart after a clean shutdown must lose nothing");
                }
            } else {
                op = "crashRestart()";
                ledger = new MuleLedger(clock);
                ledger.attachStore(store);
                ledger.startSession();
                model.reload(store.stored, clock.now);
                store.newEpoch();
                tally.crashRestarts++;
            }

            check(who, op, ledger, model, clock);
            tally.writes += store.attemptsThisOp;
            tally.refusedWrites += store.refusedThisOp;
            if (store.attemptsThisOp == 0 && op.startsWith("observe")) tally.quietReadings++;
        }
    }

    private static void check(String who, String op, MuleLedger ledger, Model model, Clock clock) {
        MuleLedger.Snapshot snapshot = ledger.snapshot();
        String where = who + " after " + op + ": ";
        assertEquals(model.session, snapshot.sessionGp, where + "session total");
        assertEquals(model.lifetime, snapshot.lifetimeGp, where + "all-time total");
        long held = model.inventory < 0 ? -1L : model.inventory + model.bank;
        assertEquals(held, snapshot.heldGp, where + "coins held");
        assertEquals(clock.now - model.sessionStart, snapshot.sessionMillis, where + "session time");
        long elapsed = Math.max(clock.now - model.sessionStart, MuleLedger.MIN_RATE_WINDOW_MS);
        long rate = model.session <= 0 ? 0L : (long) (model.session * 3_600_000.0 / elapsed);
        assertEquals(rate, snapshot.gpPerHour(), where + "GP per hour");
        assertTrue(snapshot.gpPerHour() >= 0L, where + "the rate is never negative");
        assertTrue(snapshot.heldGp >= -1L && snapshot.sessionGp >= 0L && snapshot.lifetimeGp >= 0L, where + "ranges");
    }

    /** Several threads feed and read one ledger; the all-time total must be exactly the sum fed to it. */
    @Test
    void concurrentFeedingAndReadingNeitherDeadlocksNorLosesAReceipt() throws Exception {
        MuleLedger ledger = new MuleLedger();
        SlowStore store = new SlowStore();
        ledger.attachStore(store);
        ledger.startSession();
        int threads = 6;
        int perThread = 4_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        CountDownLatch go = new CountDownLatch(1);
        AtomicLong fed = new AtomicLong();
        List<Future<?>> work = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            work.add(pool.submit(() -> {
                Random random = new Random(id);
                go.await();
                for (int i = 0; i < perThread; i++) {
                    int pick = random.nextInt(10);
                    if (pick < 5) {
                        long gp = 1 + random.nextInt(1_000);
                        ledger.recordReceived(202L, gp);
                        fed.addAndGet(gp);
                    } else if (pick < 8) {
                        ledger.observeHoldings(202L, random.nextInt(1_000_000), random.nextInt(1_000_000),
                            random.nextBoolean());
                    } else if (pick < 9) {
                        MuleLedger.Snapshot snapshot = ledger.snapshot();
                        assertTrue(snapshot.lifetimeGp >= snapshot.sessionGp);
                    } else {
                        ledger.flush();
                    }
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> job : work) job.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        ledger.flush();
        assertEquals(fed.get(), ledger.snapshot().sessionGp);
        assertEquals(fed.get(), ledger.snapshot().lifetimeGp);
        assertEquals(fed.get(), store.stored.get().lifetimeGp, "The last write holds the final total");
        assertEquals(0, store.overlaps.get(), "Writes never overlap, so an older total cannot overwrite a newer one");
    }

    /** The ledger's own lock is what makes a reading and an update atomic: no reader sees half an update. */
    @Test
    void readersAndUpdatersTakeTheSameLockSoNoReaderSeesAHalfAppliedUpdate() throws Exception {
        MuleLedger ledger = new MuleLedger();
        ledger.startSession();
        ExecutorService pool = Executors.newCachedThreadPool();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> {
            synchronized (ledger) {
                held.countDown();
                release.await(30, TimeUnit.SECONDS);
            }
            return null;
        });
        try {
            assertTrue(held.await(10, TimeUnit.SECONDS));
            List<Future<?>> blocked = new ArrayList<>();
            blocked.add(pool.submit(() -> ledger.snapshot()));
            blocked.add(pool.submit(() -> ledger.recordReceived(202L, 5L)));
            blocked.add(pool.submit(() -> ledger.observeHoldings(202L, 1L, 1L, true)));
            Thread.sleep(300);
            for (Future<?> call : blocked) {
                assertFalse(call.isDone(), "Every read and update waits while another is in progress");
            }
            assertEquals(0L, field(MuleLedger.class, "sessionGp").getLong(ledger),
                "A receipt changes nothing until it holds the lock");
            assertEquals(-1L, field(MuleLedger.class, "inventoryGp").getLong(ledger),
                "A holdings reading changes nothing until it holds the lock");
            release.countDown();
            for (Future<?> call : blocked) call.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertEquals(5L, ledger.snapshot().lifetimeGp);
    }

    /** The class promises that a slow config write cannot stall rendering. */
    @Test
    void aSlowStoreWriteNeverStallsReadingTheTotals() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch finishWrite = new CountDownLatch(1);
        MuleLedger.Store slow = new MuleLedger.Store() {
            @Override public MuleLedger.State load() { return MuleLedger.State.EMPTY; }

            @Override public void save(MuleLedger.State state) {
                writing.countDown();
                try {
                    finishWrite.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        MuleLedger ledger = new MuleLedger();
        ledger.attachStore(slow);
        ledger.startSession();
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<?> receipt = pool.submit(() -> ledger.recordReceived(202L, 7L));
            assertTrue(writing.await(10, TimeUnit.SECONDS), "The write is under way and stuck");
            Future<MuleLedger.Snapshot> reading = pool.submit(ledger::snapshot);
            MuleLedger.Snapshot during = reading.get(5, TimeUnit.SECONDS);
            assertEquals(7L, during.sessionGp, "The receipt is already visible while its write is still pending");
            assertFalse(receipt.isDone());
            finishWrite.countDown();
            receipt.get(10, TimeUnit.SECONDS);
        } finally {
            finishWrite.countDown();
            pool.shutdownNow();
        }
    }

    private static final class Tally {
        long receipts;
        long observations;
        long rebinds;
        long unreportedAccounts;
        long cleanRestarts;
        long crashRestarts;
        long refusedWrites;
        long quietReadings;
        long writes;

        @Override public String toString() {
            return "receipts=" + receipts + " observations=" + observations + " rebinds=" + rebinds
                + " unreported=" + unreportedAccounts + " cleanRestarts=" + cleanRestarts
                + " crashRestarts=" + crashRestarts + " refusedWrites=" + refusedWrites
                + " quietReadings=" + quietReadings + " writes=" + writes;
        }
    }

    /** The rules, restated without any of the ledger's bookkeeping. */
    private static final class Model {
        long account;
        long inventory = -1L;
        long bank;
        long lifetime;
        long session;
        long sessionStart;
        int rebinds;

        void reload(MuleLedger.State saved, long now) {
            account = saved.account;
            inventory = saved.inventoryGp;
            bank = saved.bankGp;
            lifetime = saved.lifetimeGp;
            session = 0L;
            sessionStart = now;
        }

        /** 0 and -1 mean "the client has not said"; the first real account adopts what had none; another restarts. */
        void bind(long observed, long now) {
            if (observed == 0L || observed == -1L || observed == account) return;
            if (account != 0L) {
                inventory = -1L;
                bank = 0L;
                lifetime = 0L;
                session = 0L;
                sessionStart = now;
                rebinds++;
            }
            account = observed;
        }

        void receive(long observed, long gp, long now) {
            bind(observed, now);
            session += gp;
            lifetime += gp;
        }

        void observe(long observed, long inventoryCoins, long bankCoins, boolean bankKnown, long now) {
            bind(observed, now);
            inventory = Math.max(0L, inventoryCoins);
            if (bankKnown) bank = Math.max(0L, bankCoins);
        }

        String persistent() {
            return account + "/" + inventory + "/" + bank + "/" + lifetime;
        }
    }

    private static final class Clock implements LongSupplier {
        long now = 5_000_000L;
        @Override public long getAsLong() { return now; }
    }

    private static final class SimStore implements MuleLedger.Store {
        final Clock clock;
        MuleLedger.State stored = MuleLedger.State.EMPTY;
        boolean failSave;
        int attemptsThisOp;
        int refusedThisOp;
        int epochAttempts;
        long previousAttemptAt;
        long lastAttemptAt;
        MuleLedger.State lastSaved;
        java.util.function.IntSupplier rebindProbe = () -> 0;
        int rebindsAtLastSave;

        SimStore(Clock clock) { this.clock = clock; }

        static String describe(MuleLedger.State state) {
            return state.account + "/" + state.inventoryGp + "/" + state.bankGp + "/" + state.lifetimeGp;
        }

        /** A new session counts its write interval from scratch. */
        void newEpoch() {
            epochAttempts = 0;
            lastSaved = stored;
            rebindsAtLastSave = rebindProbe.getAsInt();
        }

        @Override public MuleLedger.State load() { return stored; }

        @Override public void save(MuleLedger.State state) {
            attemptsThisOp++;
            epochAttempts++;
            previousAttemptAt = lastAttemptAt;
            lastAttemptAt = clock.now;
            if (failSave) {
                refusedThisOp++;
                throw new IllegalStateException("config refused the write");
            }
            int rebinds = rebindProbe.getAsInt();
            // Unless another account really appeared in between, the all-time total only ever grows.
            if (lastSaved != null && rebinds == rebindsAtLastSave) {
                if (lastSaved.account == state.account) {
                    assertTrue(state.lifetimeGp >= lastSaved.lifetimeGp,
                        "the all-time total of one account went down: " + lastSaved.lifetimeGp + " -> " + state.lifetimeGp);
                }
                if (lastSaved.account == 0L && state.account != 0L) {
                    assertTrue(state.lifetimeGp >= lastSaved.lifetimeGp, "totals with no account must be adopted whole");
                }
            }
            rebindsAtLastSave = rebinds;
            lastSaved = state;
            stored = state;
        }
    }

    private static final class SlowStore implements MuleLedger.Store {
        final java.util.concurrent.atomic.AtomicReference<MuleLedger.State> stored =
            new java.util.concurrent.atomic.AtomicReference<>(MuleLedger.State.EMPTY);
        final java.util.concurrent.atomic.AtomicInteger inside = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger overlaps = new java.util.concurrent.atomic.AtomicInteger();

        @Override public MuleLedger.State load() { return stored.get(); }

        @Override public void save(MuleLedger.State state) {
            if (inside.incrementAndGet() > 1) overlaps.incrementAndGet();
            try {
                Thread.yield();
                stored.set(state);
            } finally {
                inside.decrementAndGet();
            }
        }
    }
}
