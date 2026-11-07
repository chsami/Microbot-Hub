package net.runelite.client.plugins.microbot.geflipper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuleLedgerTest {
    private static final long HOUR = 3_600_000L;

    @Test
    void receiptsAddToSessionAndAllTimeTotalsAndAreSavedImmediately() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(new MuleLedger.State(202L, 5_000_000L, 0L, 5_000_000L));
        MuleLedger ledger = ledger(clock, store);

        ledger.recordReceived(202L, 2_000_000L);
        ledger.recordReceived(202L, 3_000_000L);
        MuleLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(5_000_000L, snapshot.sessionGp);
        assertEquals(10_000_000L, snapshot.lifetimeGp);
        assertEquals(2, store.saves.size(), "Every receipt is saved, however soon after the last");
        assertEquals(10_000_000L, store.last().lifetimeGp);
        assertEquals(202L, store.last().account);
    }

    @Test
    void aNewSessionRestartsTheSessionTotalButKeepsTheSavedAllTimeTotal() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(MuleLedger.State.EMPTY);
        MuleLedger ledger = ledger(clock, store);
        ledger.recordReceived(202L, 4_000_000L);
        clock.now += 10 * HOUR;

        ledger.startSession();
        MuleLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(0L, snapshot.sessionGp);
        assertEquals(4_000_000L, snapshot.lifetimeGp);
        assertEquals(0L, snapshot.sessionMillis);
        assertEquals(0L, snapshot.gpPerHour());
    }

    @Test
    void gpPerHourIsSessionGpOverSessionTimeWithAOneMinuteFloor() {
        Clock clock = new Clock();
        MuleLedger ledger = ledger(clock, new MemoryStore(MuleLedger.State.EMPTY));
        ledger.recordReceived(202L, 1_000_000L);

        assertEquals(60_000_000L, ledger.snapshot().gpPerHour(), "Under a minute is averaged over a full minute");
        clock.now += HOUR;
        assertEquals(1_000_000L, ledger.snapshot().gpPerHour());
        clock.now += HOUR;
        assertEquals(500_000L, ledger.snapshot().gpPerHour(), "Logged-out waiting time counts toward the hour");
    }

    @Test
    void gpPerHourIsZeroWithoutAnyReceiptAndNeverOverflows() {
        Clock clock = new Clock();
        MuleLedger ledger = ledger(clock, new MemoryStore(MuleLedger.State.EMPTY));
        assertEquals(0L, ledger.snapshot().gpPerHour());
        assertEquals(Long.MAX_VALUE, new MuleLedger.Snapshot(Long.MAX_VALUE, 0, -1, 1).gpPerHour());
        ledger.recordReceived(202L, Long.MAX_VALUE);
        ledger.recordReceived(202L, 5L);
        assertEquals(Long.MAX_VALUE, ledger.snapshot().sessionGp, "Totals saturate instead of wrapping negative");
        assertEquals(Long.MAX_VALUE, ledger.snapshot().lifetimeGp);
    }

    @Test
    void nonPositiveReceiptsAreIgnored() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(MuleLedger.State.EMPTY);
        MuleLedger ledger = ledger(clock, store);
        ledger.recordReceived(202L, 0L);
        ledger.recordReceived(202L, -5L);
        assertEquals(0L, ledger.snapshot().sessionGp);
        assertTrue(store.saves.isEmpty());
    }

    @Test
    void heldIsUnknownUntilSeenThenInventoryPlusTheLastKnownBank() {
        Clock clock = new Clock();
        MuleLedger ledger = ledger(clock, new MemoryStore(MuleLedger.State.EMPTY));
        assertEquals(-1L, ledger.snapshot().heldGp);

        ledger.observeHoldings(202L, 5_000_000L, 0L, false);
        assertEquals(5_000_000L, ledger.snapshot().heldGp);
        ledger.observeHoldings(202L, 5_000_000L, 700_000_000L, true);
        assertEquals(705_000_000L, ledger.snapshot().heldGp);
        ledger.observeHoldings(202L, 6_000_000L, 0L, false);
        assertEquals(706_000_000L, ledger.snapshot().heldGp, "An unloaded bank keeps its last known balance");
        ledger.observeHoldings(202L, 6_000_000L, 0L, true);
        assertEquals(6_000_000L, ledger.snapshot().heldGp, "A readable empty bank really is empty");
        ledger.observeHoldings(202L, -5L, -5L, true);
        assertEquals(0L, ledger.snapshot().heldGp);
    }

    @Test
    void holdingsAreSavedAtMostOncePerIntervalButTheLatestIsNeverLost() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(MuleLedger.State.EMPTY);
        MuleLedger ledger = ledger(clock, store);

        ledger.observeHoldings(202L, 1_000L, 0L, false);
        assertEquals(1, store.saves.size());
        clock.now += 100;
        ledger.observeHoldings(202L, 2_000L, 0L, false);
        clock.now += 100;
        ledger.observeHoldings(202L, 3_000L, 0L, false);
        assertEquals(1, store.saves.size(), "Rapid changes are held back");
        clock.now += MuleLedger.SAVE_INTERVAL_MS;
        ledger.observeHoldings(202L, 3_000L, 0L, false);
        assertEquals(2, store.saves.size(), "A pending change is written by the next observation after the interval");
        assertEquals(3_000L, store.last().inventoryGp);

        clock.now += 100;
        ledger.observeHoldings(202L, 4_000L, 0L, false);
        assertEquals(2, store.saves.size());
        ledger.flush();
        assertEquals(3, store.saves.size(), "Flush ignores the interval");
        assertEquals(4_000L, store.last().inventoryGp);
        ledger.flush();
        assertEquals(3, store.saves.size(), "Nothing pending, nothing written");
    }

    @Test
    void unchangedHoldingsNeverWrite() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(MuleLedger.State.EMPTY);
        MuleLedger ledger = ledger(clock, store);
        ledger.observeHoldings(202L, 1_000L, 0L, false);
        for (int i = 0; i < 20; i++) {
            clock.now += 5_000;
            ledger.observeHoldings(202L, 1_000L, 0L, false);
        }
        assertEquals(1, store.saves.size());
    }

    @Test
    void aDifferentAccountOnTheSameProfileStartsItsOwnTotals() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(new MuleLedger.State(202L, 9_000_000L, 1_000_000L, 40_000_000L));
        MuleLedger ledger = ledger(clock, store);
        assertEquals(10_000_000L, ledger.snapshot().heldGp);
        assertEquals(40_000_000L, ledger.snapshot().lifetimeGp);

        ledger.observeHoldings(303L, 2_000_000L, 0L, false);
        MuleLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(2_000_000L, snapshot.heldGp, "The old account's bank balance must not carry over");
        assertEquals(0L, snapshot.lifetimeGp);
        assertEquals(303L, store.last().account);
        assertEquals(0L, store.last().lifetimeGp);
    }

    @Test
    void theFirstAccountSeenAdoptsTotalsThatHadNoAccountYet() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(new MuleLedger.State(0L, 3_000L, 0L, 7_000_000L));
        MuleLedger ledger = ledger(clock, store);
        ledger.observeHoldings(202L, 4_000L, 0L, false);
        assertEquals(7_000_000L, ledger.snapshot().lifetimeGp);
        assertEquals(202L, store.last().account);
        ledger.observeHoldings(0L, 5_000L, 0L, false);
        assertEquals(202L, store.last().account, "An unknown account never rebinds the totals");
    }

    @Test
    void anAccountThePlatformCouldNotReportYetNeverWipesTheTotals() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(new MuleLedger.State(202L, 9_000_000L, 1_000_000L, 40_000_000L));
        MuleLedger ledger = ledger(clock, store);

        // The client answers -1 for the account hash until the login has finished; that is not an account.
        ledger.observeHoldings(-1L, 2_000_000L, 0L, false);
        ledger.recordReceived(-1L, 1_000_000L);
        MuleLedger.Snapshot during = ledger.snapshot();
        assertEquals(41_000_000L, during.lifetimeGp, "The saved all-time total survives an unreported account");
        assertEquals(1_000_000L, during.sessionGp);
        assertEquals(3_000_000L, during.heldGp, "The last known bank balance is kept");
        assertEquals(202L, store.last().account, "Nothing is written under the placeholder account");
        assertEquals(41_000_000L, store.last().lifetimeGp);

        ledger.observeHoldings(202L, 2_500_000L, 0L, false);
        MuleLedger.Snapshot after = ledger.snapshot();
        assertEquals(41_000_000L, after.lifetimeGp, "The real account picks up exactly where it was");
        assertEquals(1_000_000L, after.sessionGp);
        assertEquals(202L, store.last().account);
    }

    @Test
    void anUnreportedAccountDoesNotUseUpTheChanceToAdoptTotalsThatHadNoAccountYet() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(new MuleLedger.State(0L, 3_000L, 0L, 7_000_000L));
        MuleLedger ledger = ledger(clock, store);
        ledger.observeHoldings(-1L, 4_000L, 0L, false);
        assertEquals(0L, store.last().account, "Still no account to attach the totals to");
        assertEquals(7_000_000L, ledger.snapshot().lifetimeGp);
        ledger.observeHoldings(202L, 4_000L, 0L, false);
        assertEquals(7_000_000L, ledger.snapshot().lifetimeGp, "The first real account adopts them");
        clock.now += MuleLedger.SAVE_INTERVAL_MS;
        ledger.flush();
        assertEquals(202L, store.last().account);
        assertEquals(7_000_000L, store.last().lifetimeGp);
    }

    @Test
    void startingASessionDropsUnsavedChangesInsteadOfWritingThemElsewhere() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(MuleLedger.State.EMPTY);
        MuleLedger ledger = ledger(clock, store);
        ledger.observeHoldings(202L, 1_000L, 0L, false);
        clock.now += 10;
        ledger.observeHoldings(202L, 2_000L, 0L, false); // held back by the interval
        int writes = store.saves.size();

        // A profile switch: the store now answers for another profile.
        store.stored = new MuleLedger.State(909L, 50L, 0L, 123L);
        ledger.startSession();
        ledger.flush();
        assertEquals(writes, store.saves.size(), "Old-profile numbers are never written into the new profile");
        assertEquals(50L, ledger.snapshot().heldGp);
        assertEquals(123L, ledger.snapshot().lifetimeGp);
    }

    @Test
    void aBrokenStoreNeverBreaksAccountingAndWritesAreRetried() {
        Clock clock = new Clock();
        MemoryStore store = new MemoryStore(MuleLedger.State.EMPTY);
        store.failLoad = true;
        MuleLedger ledger = ledger(clock, store);
        assertEquals(0L, ledger.snapshot().lifetimeGp);

        store.failSave = true;
        ledger.recordReceived(202L, 1_000L);
        assertEquals(1_000L, ledger.snapshot().sessionGp, "Totals stay correct in memory");
        store.failSave = false;
        ledger.flush();
        assertEquals(1, store.saves.size(), "The failed write is retried");
        assertEquals(1_000L, store.last().lifetimeGp);
    }

    @Test
    void worksInMemoryWithoutAnyStore() {
        Clock clock = new Clock();
        MuleLedger ledger = new MuleLedger(clock);
        ledger.startSession();
        ledger.recordReceived(202L, 1_000L);
        ledger.observeHoldings(202L, 5_000L, 0L, false);
        ledger.flush();
        MuleLedger.Snapshot snapshot = ledger.snapshot();
        assertEquals(1_000L, snapshot.sessionGp);
        assertEquals(1_000L, snapshot.lifetimeGp);
        assertEquals(5_000L, snapshot.heldGp);
    }

    @Test
    void aSnapshotIsAStableCopy() {
        Clock clock = new Clock();
        MuleLedger ledger = ledger(clock, new MemoryStore(MuleLedger.State.EMPTY));
        ledger.recordReceived(202L, 1_000L);
        MuleLedger.Snapshot before = ledger.snapshot();
        ledger.recordReceived(202L, 1_000L);
        assertEquals(1_000L, before.sessionGp);
        assertEquals(2_000L, ledger.snapshot().sessionGp);
    }

    private static MuleLedger ledger(Clock clock, MemoryStore store) {
        MuleLedger ledger = new MuleLedger(clock);
        ledger.attachStore(store);
        ledger.startSession();
        return ledger;
    }

    private static final class Clock implements java.util.function.LongSupplier {
        long now = 1_000_000L;
        @Override public long getAsLong() { return now; }
    }

    private static final class MemoryStore implements MuleLedger.Store {
        MuleLedger.State stored;
        final List<MuleLedger.State> saves = new ArrayList<>();
        boolean failLoad;
        boolean failSave;

        MemoryStore(MuleLedger.State stored) { this.stored = stored; }

        MuleLedger.State last() { return saves.get(saves.size() - 1); }

        @Override public MuleLedger.State load() {
            if (failLoad) throw new IllegalStateException("unreadable");
            return stored;
        }

        @Override public void save(MuleLedger.State state) {
            if (failSave) throw new IllegalStateException("unwritable");
            stored = state;
            saves.add(state);
        }
    }
}
