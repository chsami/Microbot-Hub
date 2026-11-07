package net.runelite.client.plugins.microbot.geflipper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

/** The script, not the overlay, owns the ledger: these tests check the receiver's notifications reach it. */
class FlipperMuleLedgerWiringTest {
    @TempDir Path temporary;

    @Test
    void receiverNotificationsFeedTheScriptsLedger() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            MuleSession.ReceiverObserver observer = observer(f.script);
            observer.holdings(202L, 5_000_000L, 0L, false);
            observer.received(202L, 2_000_000L);
            MuleLedger.Snapshot snapshot = f.script.getMuleLedgerSnapshot();
            assertEquals(2_000_000L, snapshot.sessionGp);
            assertEquals(2_000_000L, snapshot.lifetimeGp);
            assertEquals(5_000_000L, snapshot.heldGp);
        }
    }

    @Test
    void aNewRunStartsAFreshSessionButKeepsSavedAllTimeTotals() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            RecordingStore store = new RecordingStore(new MuleLedger.State(202L, 7_000_000L, 0L, 40_000_000L));
            f.script.attachMuleLedgerStore(store);
            f.script.run(new FlipperConfig() { });
            MuleLedger.Snapshot first = f.script.getMuleLedgerSnapshot();
            assertEquals(0L, first.sessionGp);
            assertEquals(40_000_000L, first.lifetimeGp);
            assertEquals(7_000_000L, first.heldGp, "The login screen can show the last seen coins straight away");

            observer(f.script).received(202L, 3_000_000L);
            assertEquals(43_000_000L, store.last().lifetimeGp, "A receipt is saved at once");
            f.script.run(new FlipperConfig() { });
            MuleLedger.Snapshot second = f.script.getMuleLedgerSnapshot();
            assertEquals(0L, second.sessionGp);
            assertEquals(43_000_000L, second.lifetimeGp);
        }
    }

    @Test
    void shutdownWritesAnyPendingCoinCountAndNothingElse() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            RecordingStore store = new RecordingStore(MuleLedger.State.EMPTY);
            f.script.attachMuleLedgerStore(store);
            f.script.run(new FlipperConfig() { });
            MuleSession.ReceiverObserver observer = observer(f.script);
            observer.holdings(202L, 1_000L, 0L, false);
            observer.holdings(202L, 2_000L, 0L, false);
            int before = store.saves.size();
            f.script.shutdown();
            assertEquals(before + 1, store.saves.size());
            assertEquals(2_000L, store.last().inventoryGp);
            f.script.shutdown();
            assertEquals(before + 1, store.saves.size(), "Nothing pending, nothing written");
        }
    }

    @Test
    void aProfileChangeStartsANewSessionFromTheNewProfilesSavedTotals() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            RecordingStore store = new RecordingStore(new MuleLedger.State(202L, 1_000L, 0L, 5_000L));
            f.script.attachMuleLedgerStore(store);
            f.script.run(new FlipperConfig() { });
            observer(f.script).received(202L, 100L);
            observer(f.script).holdings(202L, 9_999L, 0L, false);
            int saves = store.saves.size();

            store.stored = new MuleLedger.State(909L, 77L, 0L, 123L); // another profile's saved totals
            f.script.pauseMuleForProfileChange();
            MuleLedger.Snapshot snapshot = f.script.getMuleLedgerSnapshot();
            assertEquals(0L, snapshot.sessionGp);
            assertEquals(123L, snapshot.lifetimeGp);
            assertEquals(77L, snapshot.heldGp);
            f.script.shutdown();
            assertEquals(saves, store.saves.size(), "The old profile's numbers are never written into the new one");
        }
    }

    private static MuleSession.ReceiverObserver observer(FlipperScript script) throws Exception {
        MuleSession session = (MuleSession) field(FlipperScript.class, "muleSession").get(script);
        return (MuleSession.ReceiverObserver) field(MuleSession.class, "receiverObserver").get(session);
    }

    private static final class RecordingStore implements MuleLedger.Store {
        MuleLedger.State stored;
        final List<MuleLedger.State> saves = new ArrayList<>();

        RecordingStore(MuleLedger.State stored) { this.stored = stored; }

        MuleLedger.State last() { return saves.get(saves.size() - 1); }

        @Override public MuleLedger.State load() { return stored; }

        @Override public void save(MuleLedger.State state) {
            stored = state;
            saves.add(state);
        }
    }
}
