package net.runelite.client.plugins.microbot.geflipper;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinishSessionTest {
    @Test
    void failedRestorationSurvivesSuspensionAndLaterCleanup() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.preferences.failDisableBeforeWrite = true;
        session.suspend();
        assertTrue(fixture.preferences.sellOnly);
        assertFalse(session.begin(null, fixture.controller, fixture.manager));
        assertTrue(session.status().contains("could not be restored"));
        session.close();
        assertTrue(session.status().contains("could not be restored"));
        session.close();
        assertTrue(session.status().contains("could not be restored"), "Cleared references cannot hide a restoration failure");
    }
    @Test
    void profileSwitchReleasesModeAndBlocksAutomaticContinuation() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        session.pauseForProfileChange();
        assertFalse(fixture.preferences.sellOnly);
        assertTrue(session.isRequested(), "New buys remain blocked until restart");
        assertFalse(session.isActive());
        assertFalse(session.begin(null, fixture.controller, fixture.manager));
        assertFalse(session.readyForCancellation());
        assertTrue(session.status().contains("profile change"));
        session.close();
        assertTrue(session.request(false));
        assertTrue(session.begin(null, fixture.controller, fixture.manager));
        session.close();
    }
    @Test
    void cancellationNeedsHealthyOwnedModeButNotAReadySuggestion() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        assertTrue(session.readyForCancellation(), "Existing buy cancellation precedes suggestion refresh");
        fixture.controller.paused.paused = true;
        assertFalse(session.readyForCancellation());
        fixture.controller.paused.paused = false;
        fixture.preferences.sellOnly = false;
        assertFalse(session.readyForCancellation());
        session.close();
        assertEquals(1, fixture.preferences.writes, "A user who turned it off owns that choice");
    }
    @Test
    void explicitRequestStartsOnceAndRestoresOnlyOwnedRuntimeMode() {
        Fixture fixture = new Fixture();
        FinishSession session = new FinishSession();
        assertFalse(session.begin(null, fixture.controller, fixture.manager));
        assertEquals(0, fixture.preferences.writes);
        session.request(false);
        long generation = session.generation();
        session.request(true);
        assertEquals(generation, session.generation());
        assertTrue(session.begin(null, fixture.controller, fixture.manager));
        assertTrue(session.isRequested());
        assertTrue(session.isActive());
        assertTrue(fixture.preferences.sellOnly);
        assertEquals(1, fixture.preferences.writes);
        assertEquals(1, fixture.manager.refreshes);
        assertTrue(session.begin(null, fixture.controller, fixture.manager));
        assertEquals(1, fixture.preferences.writes);
        assertEquals(1, fixture.manager.refreshes);
        session.close();
        assertFalse(fixture.preferences.sellOnly);
        assertEquals(2, fixture.preferences.writes);
        assertEquals(2, fixture.manager.refreshes);
        assertFalse(session.isRequested());
        assertFalse(session.isActive());
        session.close();
        assertEquals(2, fixture.preferences.writes);
    }

    @Test
    void existingSellOnlyModeIsNeverWrittenOrReset() {
        Fixture fixture = new Fixture();
        fixture.preferences.sellOnly = true;
        FinishSession session = start(fixture, false);
        assertEquals(0, fixture.preferences.writes);
        session.close();
        assertTrue(fixture.preferences.sellOnly);
        assertEquals(0, fixture.preferences.writes);
        assertEquals(1, fixture.manager.refreshes);
    }

    @Test
    void userDisablingSellOnlyIsRespectedAndBlocksFurtherTrading() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.manager.completeRequest();
        fixture.preferences.sellOnly = false;
        assertFalse(session.canTrade(new Suggestion("sell")));
        assertTrue(session.status().contains("was disabled"));
        session.close();
        assertFalse(fixture.preferences.sellOnly);
        assertEquals(1, fixture.preferences.writes);
    }

    @Test
    void observedUserModeChangesEndOwnershipEvenIfUserLaterEnablesIt() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.manager.completeRequest();
        fixture.preferences.sellOnly = false;
        assertFalse(session.canTrade(new Suggestion("sell")));
        fixture.preferences.sellOnly = true;
        assertTrue(session.canTrade(new Suggestion("sell")));
        session.close();
        assertTrue(fixture.preferences.sellOnly);
        assertEquals(1, fixture.preferences.writes);
    }

    @Test
    void missingApiFailsBeforeAnyModeMutation() {
        Fixture fixture = new Fixture();
        FinishSession session = new FinishSession();
        session.request(false);
        assertFalse(session.begin(null, fixture.controller, new Object()));
        assertFalse(session.isActive());
        assertTrue(session.isRequested());
        assertFalse(fixture.preferences.sellOnly);
        assertEquals(0, fixture.preferences.writes);
        assertTrue(session.status().contains("unavailable"));
        assertFalse(session.canTrade(new Suggestion("sell")));
    }

    @Test
    void pluginFieldFallbackUsesTheSameRuntimeManager() {
        Fixture fixture = new Fixture();
        FinishSession session = new FinishSession();
        session.request(false);
        assertTrue(session.begin(new Copilot(fixture.controller.account),
            new ControllerWithoutAccountAccessor(fixture.controller.paused), fixture.manager));
        assertTrue(fixture.preferences.sellOnly);
        session.close();
        assertFalse(fixture.preferences.sellOnly);
    }

    @Test
    void failedRefreshRollsBackAndLeavesTheRequestBlockingBuys() {
        Fixture fixture = new Fixture();
        fixture.manager.failRefresh = true;
        FinishSession session = new FinishSession();
        session.request(false);
        assertFalse(session.begin(null, fixture.controller, fixture.manager));
        assertFalse(session.isActive());
        assertTrue(session.isRequested());
        assertFalse(fixture.preferences.sellOnly);
        assertEquals(2, fixture.preferences.writes);
        assertFalse(session.canTrade(new Suggestion("buy")));
    }

    @Test
    void setterFailureAfterChangingModeIsRolledBack() {
        Fixture fixture = new Fixture();
        fixture.preferences.failEnableAfterWrite = true;
        FinishSession session = new FinishSession();
        session.request(false);
        assertFalse(session.begin(null, fixture.controller, fixture.manager));
        assertFalse(fixture.preferences.sellOnly);
        assertEquals(2, fixture.preferences.writes);
        assertTrue(session.isRequested());
    }

    @Test
    void pausedCopilotIsNeverResumedAutomatically() {
        Fixture fixture = new Fixture();
        fixture.controller.paused.paused = true;
        FinishSession session = new FinishSession();
        session.request(false);
        assertFalse(session.begin(null, fixture.controller, fixture.manager));
        assertEquals(0, fixture.preferences.writes);
        assertEquals(0, fixture.manager.refreshes);
        assertTrue(fixture.controller.paused.paused);
        assertTrue(session.status().contains("resume Copilot"));
        fixture.controller.paused.paused = false;
        assertTrue(session.begin(null, fixture.controller, fixture.manager));
        fixture.manager.completeRequest();
        fixture.controller.paused.paused = true;
        assertFalse(session.canTrade(new Suggestion("sell")));
        assertFalse(session.healthyFreshWait(new Suggestion("wait")));
    }

    @Test
    void oldWaitCannotCompleteUntilAFreshSellOnlyResponseArrives() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        assertFalse(session.canTrade(new Suggestion("sell")));
        assertFalse(session.healthyFreshWait(new Suggestion("wait")));
        fixture.manager.needed = false;
        assertFalse(session.healthyFreshWait(new Suggestion("wait")));
        fixture.manager.completeRequest();
        assertTrue(session.healthyFreshWait(new Suggestion("wait")));
    }

    @Test
    void requestAlreadyInFlightIsDiscardedAndRefreshedOnce() {
        Fixture fixture = new Fixture();
        fixture.manager.inProgress = true;
        FinishSession session = start(fixture, false);
        assertFalse(session.healthyFreshWait(new Suggestion("wait")));
        assertEquals(1, fixture.manager.refreshes);
        fixture.manager.completeRequest();
        assertFalse(session.healthyFreshWait(new Suggestion("wait")));
        assertEquals(2, fixture.manager.refreshes);
        fixture.manager.needed = false;
        assertFalse(session.healthyFreshWait(new Suggestion("wait")));
        assertEquals(2, fixture.manager.refreshes);
        fixture.manager.completeRequest();
        assertTrue(session.healthyFreshWait(new Suggestion("wait")));
    }

    @Test
    void onlyKnownNonBuyingSuggestionsAreAllowed() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.manager.completeRequest();
        for (String allowed : new String[]{"sell", "modify_sell", "abort", "collect", "wait"}) {
            assertTrue(session.canTrade(new Suggestion(allowed)), allowed);
        }
        for (String forbidden : new String[]{"buy", "modify_buy", "modify", "unexpected", ""}) {
            assertFalse(session.canTrade(new Suggestion(forbidden)), forbidden);
        }
        assertFalse(session.canTrade(null));
        assertFalse(session.canTrade(new Object()));
        assertFalse(session.healthyFreshWait(new Suggestion("sell")));
    }

    @Test
    void requestErrorsAndPendingResponsesBlockTradingAndCompletion() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.manager.completeRequest();
        fixture.manager.error = new Object();
        assertFalse(session.canTrade(new Suggestion("sell")));
        assertFalse(session.healthyFreshWait(new Suggestion("wait")));
        fixture.manager.error = null;
        fixture.manager.inProgress = true;
        assertFalse(session.canTrade(new Suggestion("sell")));
        fixture.manager.inProgress = false;
        fixture.manager.refreshPending = true;
        assertFalse(session.canTrade(new Suggestion("sell")));
        fixture.manager.refreshPending = false;
        assertTrue(session.canTrade(new Suggestion("sell")));
    }

    @Test
    void listingCompletionRequiresFiveStableSecondsAndNoUnlistedItems() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.manager.completeRequest();
        FinishSession.CompletionSnapshot readyWithSales = snapshot(true, true, true, true, false, true);
        assertFalse(session.completionReady(readyWithSales, 100));
        assertFalse(session.completionReady(readyWithSales, 5099));
        assertTrue(session.completionReady(readyWithSales, 5100));
        assertFalse(session.completionReady(snapshot(true, true, true, false, false, true), 5200));
        assertFalse(session.completionReady(readyWithSales, 5300));
        assertFalse(session.completionReady(readyWithSales, 10299));
        assertTrue(session.completionReady(readyWithSales, 10300));
    }

    @Test
    void monotonicTimeCanStartNegativeOrMoveBackwardsWithoutFalseCompletion() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.manager.completeRequest();
        FinishSession.CompletionSnapshot ready = snapshot(true, true, true, true, true, true);
        assertFalse(session.completionReady(ready, -20000));
        assertFalse(session.completionReady(ready, -15001));
        assertTrue(session.completionReady(ready, -15000));
        assertFalse(session.completionReady(ready, -25000));
        assertFalse(session.completionReady(ready, -20001));
        assertTrue(session.completionReady(ready, -20000));
    }

    @Test
    void waitingForAllSalesAlsoRequiresNoActiveSellOffer() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, true);
        fixture.manager.completeRequest();
        assertFalse(session.completionReady(snapshot(true, true, true, true, false, true), 100));
        assertFalse(session.completionReady(snapshot(true, true, true, true, false, true), 10000));
        FinishSession.CompletionSnapshot ready = snapshot(true, true, true, true, true, true);
        assertFalse(session.completionReady(ready, 10100));
        assertTrue(session.completionReady(ready, 15100));
    }

    @Test
    void everySafetyConditionAndCurrentCopilotStateCanResetCompletion() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.manager.completeRequest();
        FinishSession.CompletionSnapshot ready = snapshot(true, true, true, true, true, true);
        FinishSession.CompletionSnapshot[] blocked = {
            snapshot(false, true, true, true, true, true),
            snapshot(true, false, true, true, true, true),
            snapshot(true, true, false, true, true, true),
            snapshot(true, true, true, false, true, true),
            snapshot(true, true, true, true, true, false), null
        };
        long now = 100;
        for (FinishSession.CompletionSnapshot value : blocked) {
            assertFalse(session.completionReady(ready, now));
            assertFalse(session.completionReady(value, now + 4000));
            assertFalse(session.completionReady(ready, now + 5000));
            now += 6000;
        }
        fixture.manager.error = new Object();
        assertFalse(session.completionReady(ready, now + 20000));
        fixture.manager.error = null;
        assertFalse(session.completionReady(ready, now + 21000));
        assertTrue(session.completionReady(ready, now + 26000));
    }

    @Test
    void suspensionRestoresModeReleasesControlsAndRetainsExplicitRequest() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, true);
        fixture.manager.completeRequest();
        long generation = session.generation();
        session.suspend();
        assertFalse(fixture.preferences.sellOnly);
        assertTrue(session.isRequested());
        assertFalse(session.isActive());
        assertTrue(session.generation() > generation);
        assertFalse(session.canTrade(new Suggestion("sell")));
        assertTrue(session.begin(null, fixture.controller, fixture.manager));
        fixture.manager.completeRequest();
        assertFalse(session.completionReady(snapshot(true, true, true, true, false, true), 10000));
        session.close();
        assertFalse(fixture.preferences.sellOnly);
    }

    @Test
    void stoppedSessionCannotCompleteOrTradeFromAnOldSnapshot() {
        Fixture fixture = new Fixture();
        FinishSession session = start(fixture, false);
        fixture.manager.completeRequest();
        FinishSession.CompletionSnapshot ready = snapshot(true, true, true, true, true, true);
        assertFalse(session.completionReady(ready, 100));
        session.close();
        assertFalse(session.completionReady(ready, 10000));
        assertFalse(session.canTrade(new Suggestion("sell")));
    }

    private static FinishSession start(Fixture fixture, boolean waitForAllSales) {
        FinishSession session = new FinishSession();
        session.request(waitForAllSales);
        assertTrue(session.begin(null, fixture.controller, fixture.manager));
        return session;
    }

    private static FinishSession.CompletionSnapshot snapshot(boolean overview, boolean buys,
            boolean collected, boolean listed, boolean sales, boolean wait) {
        return new FinishSession.CompletionSnapshot(overview, buys, collected, listed, sales, wait);
    }

    static final class Fixture {
        final Preferences preferences = new Preferences();
        final Manager manager = new Manager();
        final Controller controller = new Controller(new AccountManager(preferences));
    }

    public static final class Preferences {
        boolean sellOnly;
        boolean failEnableAfterWrite;
        boolean failDisableBeforeWrite;
        int writes;
        public boolean isSellOnlyMode() { return sellOnly; }
        public void setSellOnlyMode(boolean value) {
            if (!value && failDisableBeforeWrite) throw new IllegalStateException("Synthetic restoration failure");
            writes++;
            sellOnly = value;
            if (value && failEnableAfterWrite) throw new IllegalStateException("Synthetic setter failure");
        }
    }

    public static final class AccountManager {
        private final Preferences suggestionPreferencesManager;
        AccountManager(Preferences preferences) { suggestionPreferencesManager = preferences; }
    }

    public static final class Copilot {
        private final AccountManager accountStatusManager;
        Copilot(AccountManager manager) { accountStatusManager = manager; }
    }

    public static final class PausedManager {
        boolean paused;
        public boolean isPaused() { return paused; }
    }

    public static final class Controller {
        final AccountManager account;
        final PausedManager paused = new PausedManager();
        Controller(AccountManager account) { this.account = account; }
        public AccountManager getAccountStatusManager() { return account; }
        public PausedManager getPausedManager() { return paused; }
    }

    public static final class ControllerWithoutAccountAccessor {
        final PausedManager paused;
        ControllerWithoutAccountAccessor(PausedManager paused) { this.paused = paused; }
        public PausedManager getPausedManager() { return paused; }
    }

    public static final class Suggestion {
        private final String type;
        Suggestion(String type) { this.type = type; }
        public String getType() { return type; }
    }

    public static final class Manager {
        Object suggestion = new Suggestion("wait");
        boolean needed;
        boolean inProgress;
        boolean refreshPending;
        boolean failRefresh;
        int refreshes;
        Object error;
        Instant received = Instant.EPOCH;
        public boolean isSuggestionNeeded() { return needed; }
        public boolean isSuggestionRequestInProgress() { return inProgress; }
        public boolean isSuggestionRefreshPending() { return refreshPending; }
        public Object getSuggestionError() { return error; }
        public Instant getSuggestionReceivedAt() { return received; }
        public void setSuggestionNeeded(boolean value) {
            refreshes++;
            if (failRefresh) throw new IllegalStateException("Synthetic refresh failure");
            needed = value;
        }
        void completeRequest() {
            needed = inProgress = refreshPending = false;
            received = received.plusSeconds(1);
        }
    }
}
