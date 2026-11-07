package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompletedOfferRecoveryTest {
    @Test
    void firstHealthyWaitScansImmediatelyAndTheFullMinuteMustElapse() {
        CompletedOfferRecovery recovery = new CompletedOfferRecovery();
        Object manager = new Object();
        assertFalse(recovery.due(manager, false, 100));
        assertTrue(recovery.due(manager, true, 1_000));
        assertFalse(recovery.due(manager, true, 1_000));
        assertFalse(recovery.due(manager, true, 60_999));
        assertTrue(recovery.due(manager, true, 61_000));
        assertFalse(recovery.due(manager, true, 120_999));
        assertTrue(recovery.due(manager, true, 121_000));
    }

    @Test
    void unsuccessfulScansAndRefreshFlickerCannotCreateRapidRetries() {
        CompletedOfferRecovery recovery = new CompletedOfferRecovery();
        Object manager = new Object();
        assertTrue(recovery.due(manager, true, 0));
        // There is intentionally no success callback: an empty board or refused click is still a scan.
        assertFalse(recovery.due(manager, false, 10_000));
        assertFalse(recovery.due(manager, true, 10_001));
        assertFalse(recovery.due(manager, false, 59_999));
        assertTrue(recovery.due(manager, true, 60_000));
    }

    @Test
    void anOverdueCheckStillNeedsAHealthyWait() {
        CompletedOfferRecovery recovery = new CompletedOfferRecovery();
        Object manager = new Object();
        assertTrue(recovery.due(manager, true, 0));
        assertFalse(recovery.due(manager, false, 120_000));
        assertTrue(recovery.due(manager, true, 120_001));
        assertFalse(recovery.due(manager, true, 120_002));
    }

    @Test
    void missingManagerNeverScansOrDiscardsTheCurrentDeadline() {
        CompletedOfferRecovery recovery = new CompletedOfferRecovery();
        Object manager = new Object();
        assertFalse(recovery.due(null, true, 0));
        assertTrue(recovery.due(manager, true, 100));
        assertFalse(recovery.due(null, true, 101));
        assertFalse(recovery.due(manager, true, 102));
    }

    @Test
    void managerIdentityAndLifecycleResetAllowAFreshFirstScan() {
        CompletedOfferRecovery recovery = new CompletedOfferRecovery();
        Object first = new EqualManager();
        Object second = new EqualManager();
        assertTrue(recovery.due(first, true, 0));
        assertFalse(recovery.due(second, false, 1));
        assertTrue(recovery.due(second, true, 2), "Owner comparison must use identity, not equals");
        recovery.reset();
        assertTrue(recovery.due(second, true, 3));
    }

    @Test
    void aRegressedClockRebaselinesWithoutAnImmediateRetry() {
        CompletedOfferRecovery recovery = new CompletedOfferRecovery();
        Object manager = new Object();
        assertTrue(recovery.due(manager, true, 90_000));
        assertFalse(recovery.due(manager, true, 10_000));
        assertFalse(recovery.due(manager, true, 69_999));
        assertTrue(recovery.due(manager, true, 70_000));
    }

    @Test
    void clockRegressionDuringRefreshAlsoRequiresANewFullInterval() {
        CompletedOfferRecovery recovery = new CompletedOfferRecovery();
        Object manager = new Object();
        assertTrue(recovery.due(manager, true, 90_000));
        assertFalse(recovery.due(manager, false, 10_000));
        assertFalse(recovery.due(manager, true, 10_001));
        assertTrue(recovery.due(manager, true, 70_000));
    }

    @Test
    void onlyTerminalStatesAuthorizeCollection() {
        for (GrandExchangeOfferState state : GrandExchangeOfferState.values()) {
            GrandExchangeOffer[] offers = emptyOffers();
            offers[3] = offer(state);
            boolean terminal = state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD
                || state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL;
            assertEquals(terminal, CompletedOfferRecovery.hasCompletedOffers(offers), state.name());
        }
    }

    @Test
    void activePartialFillsDoNotBecomeCompletedOffers() {
        GrandExchangeOffer[] offers = emptyOffers();
        offers[0] = offer(GrandExchangeOfferState.BUYING);
        offers[1] = offer(GrandExchangeOfferState.SELLING);
        // Proxies reject any amount read; completion is determined by state, never quantity sold.
        assertFalse(CompletedOfferRecovery.hasCompletedOffers(offers));
        offers[7] = offer(GrandExchangeOfferState.SOLD);
        assertTrue(CompletedOfferRecovery.hasCompletedOffers(offers));
    }

    @Test
    void incompleteOrUnreadableBoardsFailClosedEvenAfterACompletedSlot() {
        assertFalse(CompletedOfferRecovery.hasCompletedOffers(null));
        assertFalse(CompletedOfferRecovery.hasCompletedOffers(new GrandExchangeOffer[0]));
        assertFalse(CompletedOfferRecovery.hasCompletedOffers(Arrays.copyOf(emptyOffers(), 7)));
        assertFalse(CompletedOfferRecovery.hasCompletedOffers(Arrays.copyOf(emptyOffers(), 9)));
        GrandExchangeOffer[] offers = emptyOffers();
        offers[0] = offer(GrandExchangeOfferState.SOLD);
        offers[7] = null;
        assertFalse(CompletedOfferRecovery.hasCompletedOffers(offers));
        offers[7] = offer(null);
        assertFalse(CompletedOfferRecovery.hasCompletedOffers(offers));
        offers[7] = (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                throw new IllegalStateException("Offer read unavailable");
            });
        assertFalse(CompletedOfferRecovery.hasCompletedOffers(offers));
    }

    private static GrandExchangeOffer[] emptyOffers() {
        GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
        Arrays.fill(offers, offer(GrandExchangeOfferState.EMPTY));
        return offers;
    }

    private static GrandExchangeOffer offer(GrandExchangeOfferState state) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                if (method.getName().equals("getState")) return state;
                throw new AssertionError("Unexpected offer read: " + method.getName());
            });
    }

    private static final class EqualManager {
        @Override public boolean equals(Object other) { return other instanceof EqualManager; }
        @Override public int hashCode() { return 1; }
    }
}
