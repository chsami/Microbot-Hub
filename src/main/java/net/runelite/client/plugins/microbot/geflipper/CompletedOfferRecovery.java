package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/** A bounded collection watchdog; the caller supplies fresh client-thread state. */
final class CompletedOfferRecovery {
    private static final long CHECK_INTERVAL_MS = 60_000;
    private static final int OFFER_SLOTS = 8;

    private Object manager;
    private boolean checked;
    private long lastCheck;

    /** A due check consumes the interval even when there is nothing to collect or the click fails. */
    synchronized boolean due(Object currentManager, boolean waiting, long nowMillis) {
        if (currentManager == null) return false;
        if (manager != currentManager) {
            manager = currentManager;
            checked = false;
        }
        if (checked && nowMillis < lastCheck) {
            // An unavailable or regressed clock must not authorize an immediate second attempt.
            lastCheck = nowMillis;
            return false;
        }
        if (!waiting || checked && nowMillis - lastCheck < CHECK_INTERVAL_MS) return false;
        checked = true;
        lastCheck = nowMillis;
        return true;
    }

    synchronized void reset() {
        manager = null;
        checked = false;
        lastCheck = 0;
    }

    /** ClientThread only. A terminal state in an incomplete board cannot authorize collection. */
    static boolean hasCompletedOffers(GrandExchangeOffer[] offers) {
        if (offers == null || offers.length != OFFER_SLOTS) return false;
        boolean completed = false;
        try {
            for (GrandExchangeOffer offer : offers) {
                if (offer == null) return false;
                GrandExchangeOfferState state = offer.getState();
                if (state == null) return false;
                switch (state) {
                    case BOUGHT:
                    case SOLD:
                    case CANCELLED_BUY:
                    case CANCELLED_SELL:
                        completed = true;
                        break;
                    case EMPTY:
                    case BUYING:
                    case SELLING:
                        break;
                    default:
                        return false;
                }
            }
            return completed;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
