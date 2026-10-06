package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/** Pure collection guards; callers must read live offers on the client thread. */
final class BankSellerBatchPolicy {
    private static final int GE_SLOT_COUNT = 8;

    private BankSellerBatchPolicy() {
    }

    /** Allow a short fill grace only for this run's sales, never foreign offers. */
    static boolean hasOnlyOwnedSales(GrandExchangeOffer[] offers,
                                     BiPredicate<Integer, GrandExchangeOffer> isOwned) {
        if (offers == null || offers.length != GE_SLOT_COUNT || isOwned == null) {
            return false;
        }
        boolean foundSale = false;
        for (int slot = 0; slot < offers.length; slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer == null) {
                return false;
            }
            if (offer.getState() == GrandExchangeOfferState.EMPTY) {
                continue;
            }
            if (offer.getItemId() <= 0 || !isOwned.test(slot, offer)
                    || (offer.getState() != GrandExchangeOfferState.SELLING
                        && offer.getState() != GrandExchangeOfferState.SOLD)) {
                return false;
            }
            foundSale = true;
        }
        return foundSale;
    }

    static boolean allOwnedSalesCompleted(GrandExchangeOffer[] offers,
                                          BiPredicate<Integer, GrandExchangeOffer> isOwned) {
        if (!hasOnlyOwnedSales(offers, isOwned)) {
            return false;
        }
        for (GrandExchangeOffer offer : offers) {
            if (offer.getState() == GrandExchangeOfferState.SELLING) {
                return false;
            }
        }
        return true;
    }

    /** An unrelated occupied slot forbids overview collection, even before it fills. */
    static boolean hasForeignOccupiedOffer(GrandExchangeOffer[] offers,
                                           BiPredicate<Integer, GrandExchangeOffer> isOwned) {
        if (offers == null || offers.length != GE_SLOT_COUNT || isOwned == null) {
            return false;
        }
        for (int slot = 0; slot < offers.length; slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY
                    && !isOwned.test(slot, offer)) {
                return true;
            }
        }
        return false;
    }

    /** Completed sales can be collected while this run's other sales are still pending. */
    static Map<Integer, Integer> completedOwnedBatch(GrandExchangeOffer[] offers,
                                                    BiPredicate<Integer, GrandExchangeOffer> isOwned) {
        return terminalOwnedBatch(offers, isOwned, false);
    }

    /** Recovery uses the same overview button, allowing this run's cancelled sales. */
    static Map<Integer, Integer> terminalOwnedBatch(GrandExchangeOffer[] offers,
                                                   BiPredicate<Integer, GrandExchangeOffer> isOwned) {
        return terminalOwnedBatch(offers, isOwned, true);
    }

    private static Map<Integer, Integer> terminalOwnedBatch(GrandExchangeOffer[] offers,
                                                           BiPredicate<Integer, GrandExchangeOffer> isOwned,
                                                           boolean allowCancelled) {
        if (offers == null || offers.length != GE_SLOT_COUNT || isOwned == null) {
            return null;
        }
        Map<Integer, Integer> batch = new LinkedHashMap<>();
        for (int slot = 0; slot < offers.length; slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer == null) {
                return null;
            }
            if (offer.getState() == GrandExchangeOfferState.EMPTY) {
                continue;
            }
            // Check locked member slots as well: a foreign offer could become
            // collectible between this guard and the overview action.
            GrandExchangeOfferState state = offer.getState();
            if (offer.getItemId() <= 0 || !isOwned.test(slot, offer)
                    || (state != GrandExchangeOfferState.SELLING
                        && state != GrandExchangeOfferState.SOLD
                        && !(allowCancelled && state == GrandExchangeOfferState.CANCELLED_SELL))) {
                return null;
            }
            if (state != GrandExchangeOfferState.SELLING) {
                batch.put(slot, offer.getItemId());
            }
        }
        return batch.isEmpty() ? null : batch;
    }

    /** A click is not success until every expected slot is reliably empty. */
    static boolean batchIsEmpty(GrandExchangeOffer[] offers, Map<Integer, Integer> expectedItems) {
        if (offers == null || offers.length != GE_SLOT_COUNT || expectedItems == null || expectedItems.isEmpty()) {
            return false;
        }
        for (int slot : expectedItems.keySet()) {
            if (slot < 0 || slot >= offers.length || offers[slot] == null
                    || offers[slot].getState() != GrandExchangeOfferState.EMPTY) {
                return false;
            }
        }
        return true;
    }
}
