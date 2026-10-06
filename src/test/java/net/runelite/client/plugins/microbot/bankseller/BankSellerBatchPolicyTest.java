package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Dependency-free regression tests. Run main(); this never starts or controls a client.
 * Compile with BankSellerBatchPolicy.java and the Microbot client JAR on the classpath.
 */
public final class BankSellerBatchPolicyTest {
    private static final BiPredicate<Integer, GrandExchangeOffer> ALL_OWNED = (slot, offer) -> true;
    private static int assertions;

    private BankSellerBatchPolicyTest() {
    }

    public static void main(String[] args) {
        completedBatchContainsAllThreeOwnedOffers();
        completedSubsetAllowsOwnedSalesToContinue();
        emptyExchangeHasNoBatch();
        foreignOffersPreventGlobalCollection();
        incompleteOwnedOffersPreventGlobalCollection();
        terminalBatchIncludesCanceledOwnedSales();
        foreignOccupancyIsDistinctFromUnknownState();
        unknownSnapshotsFailClosed();
        fillGraceRequiresOnlyOwnedSales();
        fillGraceCompletesOnlyAfterEveryOwnedSaleFinishes();
        batchSnapshotsDoNotRetainMutableOffers();
        collectionNeedsReliableEmptySlots();
        System.out.println("BankSellerBatchPolicyTest passed (" + assertions + " assertions)");
    }

    private static void completedSubsetAllowsOwnedSalesToContinue() {
        for (int quantitySold : new int[]{0, 4, 10}) {
            GrandExchangeOffer[] offers = soldBatch();
            offers[1] = offer(201, GrandExchangeOfferState.SELLING, quantitySold);
            Map<Integer, Integer> expected = expectedBatch();
            expected.remove(1);
            check(expected.equals(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED)),
                    "Owned SELLING with progress " + quantitySold
                            + " must not prevent collecting the other completed offers from overview");
            check(!BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED).containsKey(1),
                    "An unfinished sale must not be expected to become EMPTY after collecting proceeds");
            offers[0] = offer(200, GrandExchangeOfferState.EMPTY, 0);
            offers[2] = offer(202, GrandExchangeOfferState.EMPTY, 0);
            check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED) == null,
                    "SELLING alone must not dispatch collection, even when quantitySold matches total");
        }
        GrandExchangeOffer[] offers = soldBatch();
        offers[7] = offer(207, GrandExchangeOfferState.SELLING, 4);
        check(expectedBatch().equals(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED)),
                "An unfinished owned locked-member slot must not hide completed owned offers");
        offers[7] = offer(0, GrandExchangeOfferState.SELLING, 4);
        check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED) == null,
                "SELLING with an invalid identity must fail closed");
    }

    private static void completedBatchContainsAllThreeOwnedOffers() {
        GrandExchangeOffer[] offers = soldBatch();
        Map<Integer, Integer> expected = expectedBatch();
        Map<Integer, Integer> actual = BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED);
        check(expected.equals(actual), "All three owned SOLD slots must be collected as one batch");
        check(Arrays.asList(0, 1, 2).equals(Arrays.asList(actual.keySet().toArray())),
                "The batch must retain slot order");

        offers[7] = offer(207, GrandExchangeOfferState.SOLD, 10);
        check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED).get(7) == 207,
                "Owned member slots must be included, not silently ignored");
    }

    private static void emptyExchangeHasNoBatch() {
        check(BankSellerBatchPolicy.completedOwnedBatch(emptyOffers(), ALL_OWNED) == null,
                "An empty exchange must not dispatch a collection");
    }

    private static void foreignOffersPreventGlobalCollection() {
        GrandExchangeOfferState[] foreignStates = {
                GrandExchangeOfferState.SOLD,
                GrandExchangeOfferState.BOUGHT,
                GrandExchangeOfferState.SELLING,
                GrandExchangeOfferState.BUYING,
                GrandExchangeOfferState.CANCELLED_SELL,
                GrandExchangeOfferState.CANCELLED_BUY
        };
        for (GrandExchangeOfferState state : foreignStates) {
            for (int quantitySold : new int[]{0, 4, 10}) {
                for (int foreignSlot : new int[]{2, 7}) {
                    GrandExchangeOffer[] offers = soldBatch();
                    offers[foreignSlot] = offer(202, state, quantitySold);
                    BiPredicate<Integer, GrandExchangeOffer> ownedExceptForeign =
                            (slot, offer) -> slot != foreignSlot;
                    check(BankSellerBatchPolicy.completedOwnedBatch(offers, ownedExceptForeign) == null,
                            "Foreign " + state + " at slot " + foreignSlot + " with progress "
                                    + quantitySold + " must prevent global collection");
                    check(BankSellerBatchPolicy.terminalOwnedBatch(offers, ownedExceptForeign) == null,
                            "Foreign " + state + " must also prevent canceled-item overview collection");
                    check(BankSellerBatchPolicy.hasForeignOccupiedOffer(offers, ownedExceptForeign),
                            "Foreign " + state + " must be reported even before it has any proceeds");
                }
            }
        }
    }

    private static void incompleteOwnedOffersPreventGlobalCollection() {
        for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{
                GrandExchangeOfferState.BUYING,
                GrandExchangeOfferState.BOUGHT,
                GrandExchangeOfferState.CANCELLED_SELL,
                GrandExchangeOfferState.CANCELLED_BUY}) {
            GrandExchangeOffer[] offers = soldBatch();
            offers[1] = offer(201, state, 4);
            check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED) == null,
                    "Owned " + state + " must not be included in a completed sell batch");
        }
        GrandExchangeOffer[] offers = soldBatch();
        offers[0] = offer(0, GrandExchangeOfferState.SOLD, 10);
        check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED) == null,
                "SOLD with an invalid item identity must fail closed");
    }

    private static void terminalBatchIncludesCanceledOwnedSales() {
        GrandExchangeOffer[] offers = soldBatch();
        offers[1] = offer(201, GrandExchangeOfferState.CANCELLED_SELL, 4);
        offers[2] = offer(202, GrandExchangeOfferState.SELLING, 4);
        Map<Integer, Integer> expected = expectedBatch();
        expected.remove(2);
        check(expected.equals(BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED)),
                "One overview action must include owned SOLD and CANCELLED_SELL while other sales continue");
        check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED) == null,
                "Normal proceeds collection must not send canceled items to bank inadvertently");
        offers[7] = offer(207, GrandExchangeOfferState.CANCELLED_SELL, 0);
        expected.put(7, 207);
        check(expected.equals(BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED)),
                "Owned canceled member slots must be included in the terminal batch");
        check(Arrays.asList(0, 1, 7).equals(Arrays.asList(
                        BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED).keySet().toArray())),
                "Terminal snapshots retain predictable slot order");

        offers = emptyOffers();
        offers[0] = offer(200, GrandExchangeOfferState.CANCELLED_SELL, 0);
        check(Collections.singletonMap(0, 200).equals(BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED)),
                "An unsold canceled stack alone must be recoverable through overview Collect");
        offers[0] = offer(200, GrandExchangeOfferState.SELLING, 4);
        check(BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED) == null,
                "Partial SELLING alone has no terminal slot to confirm");
        check(BankSellerBatchPolicy.terminalOwnedBatch(emptyOffers(), ALL_OWNED) == null,
                "Empty exchange must not dispatch terminal collection");

        for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{
                GrandExchangeOfferState.BUYING, GrandExchangeOfferState.BOUGHT,
                GrandExchangeOfferState.CANCELLED_BUY, null}) {
            offers = soldBatch();
            offers[7] = offer(207, state, 4);
            check(BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED) == null,
                    "Owned " + state + " must deny terminal collection");
        }
        offers = soldBatch();
        offers[7] = offer(0, GrandExchangeOfferState.CANCELLED_SELL, 0);
        check(BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED) == null,
                "Canceled offer with invalid identity must fail closed");
    }

    private static void foreignOccupancyIsDistinctFromUnknownState() {
        check(!BankSellerBatchPolicy.hasForeignOccupiedOffer(emptyOffers(), (slot, offer) -> false),
                "EMPTY foreign-marked slots have nothing to block");
        check(!BankSellerBatchPolicy.hasForeignOccupiedOffer(soldBatch(), ALL_OWNED),
                "Owned occupied slots are not foreign");
        GrandExchangeOffer[] offers = emptyOffers();
        offers[7] = offer(207, GrandExchangeOfferState.SELLING, 0);
        check(BankSellerBatchPolicy.hasForeignOccupiedOffer(offers, (slot, offer) -> false),
                "An unfilled locked foreign offer must block before its first sale");
        check(!BankSellerBatchPolicy.hasForeignOccupiedOffer(null, ALL_OWNED),
                "Missing metadata must not be mislabeled as a known foreign offer");
        check(!BankSellerBatchPolicy.hasForeignOccupiedOffer(new GrandExchangeOffer[0], ALL_OWNED),
                "Missing offer array is unknown, not a foreign ownership verdict");
        check(!BankSellerBatchPolicy.hasForeignOccupiedOffer(Arrays.copyOf(offers, 3), ALL_OWNED),
                "A truncated array is unknown, not a foreign ownership verdict");
        check(!BankSellerBatchPolicy.hasForeignOccupiedOffer(Arrays.copyOf(offers, 9), ALL_OWNED),
                "An unexpected array shape is unknown, not a foreign ownership verdict");
        check(!BankSellerBatchPolicy.hasForeignOccupiedOffer(soldBatch(), null),
                "Unavailable ownership must not be mistaken for an explicit foreign offer");
    }

    private static void unknownSnapshotsFailClosed() {
        check(BankSellerBatchPolicy.completedOwnedBatch(null, ALL_OWNED) == null,
                "A missing offer array must fail closed");
        check(BankSellerBatchPolicy.completedOwnedBatch(new GrandExchangeOffer[0], ALL_OWNED) == null,
                "An unavailable zero-length array must fail closed");
        check(BankSellerBatchPolicy.completedOwnedBatch(Arrays.copyOf(soldBatch(), 3), ALL_OWNED) == null,
                "An F2P-only snapshot must not hide the five locked slots");
        check(BankSellerBatchPolicy.completedOwnedBatch(Arrays.copyOf(soldBatch(), 9), ALL_OWNED) == null,
                "An unexpected offer-array shape must fail closed");
        check(BankSellerBatchPolicy.completedOwnedBatch(soldBatch(), null) == null,
                "Unknown ownership must fail closed");
        check(BankSellerBatchPolicy.terminalOwnedBatch(null, ALL_OWNED) == null,
                "Missing offers must deny canceled-item collection");
        check(BankSellerBatchPolicy.terminalOwnedBatch(new GrandExchangeOffer[0], ALL_OWNED) == null,
                "Empty unavailable array must deny canceled-item collection");
        check(BankSellerBatchPolicy.terminalOwnedBatch(Arrays.copyOf(soldBatch(), 3), ALL_OWNED) == null,
                "Truncated array must deny canceled-item collection");
        check(BankSellerBatchPolicy.terminalOwnedBatch(Arrays.copyOf(soldBatch(), 9), ALL_OWNED) == null,
                "Unexpected array shape must deny canceled-item collection");
        check(BankSellerBatchPolicy.terminalOwnedBatch(soldBatch(), null) == null,
                "Unknown ownership must deny canceled-item collection");
        for (int slot : new int[]{0, 7}) {
            GrandExchangeOffer[] offers = soldBatch();
            offers[slot] = null;
            check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED) == null,
                    "A missing slot " + slot + " must not be treated as empty");
            check(BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED) == null,
                    "Missing slot " + slot + " must deny canceled-item collection");
        }
        GrandExchangeOffer[] offers = soldBatch();
        offers[7] = offer(207, null, 0);
        check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED) == null,
                "An unknown slot state must fail closed");
        check(BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED) == null,
                "Unknown state must deny canceled-item collection");
    }

    private static void fillGraceRequiresOnlyOwnedSales() {
        check(BankSellerBatchPolicy.hasOnlyOwnedSales(soldBatch(), ALL_OWNED),
                "A completed owned batch is eligible for fill readiness");
        GrandExchangeOffer[] offers = soldBatch();
        offers[2] = offer(202, GrandExchangeOfferState.SELLING, 4);
        check(BankSellerBatchPolicy.hasOnlyOwnedSales(offers, ALL_OWNED),
                "A final partially filled owned sale may receive the short grace");
        check(BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED).size() == 2,
                "After grace, only the two completed offers need to empty during overview collection");
        offers[0] = offer(200, GrandExchangeOfferState.SELLING, 0);
        offers[1] = offer(201, GrandExchangeOfferState.SELLING, 0);
        check(BankSellerBatchPolicy.hasOnlyOwnedSales(offers, ALL_OWNED),
                "Owned SELLING offers are eligible even before their first fill");

        for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{
                GrandExchangeOfferState.SOLD, GrandExchangeOfferState.SELLING}) {
            for (int foreignSlot : new int[]{1, 7}) {
                offers = soldBatch();
                offers[foreignSlot] = offer(207, state, 4);
                check(!BankSellerBatchPolicy.hasOnlyOwnedSales(offers, (slot, offer) -> slot != foreignSlot),
                        "A foreign " + state + " at slot " + foreignSlot + " must deny fill grace");
            }
        }
        for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{
                GrandExchangeOfferState.BUYING,
                GrandExchangeOfferState.BOUGHT,
                GrandExchangeOfferState.CANCELLED_BUY,
                GrandExchangeOfferState.CANCELLED_SELL,
                null}) {
            offers = soldBatch();
            offers[7] = offer(207, state, 4);
            check(!BankSellerBatchPolicy.hasOnlyOwnedSales(offers, ALL_OWNED),
                    "An owned " + state + " slot must deny fill grace");
        }
        check(!BankSellerBatchPolicy.hasOnlyOwnedSales(emptyOffers(), ALL_OWNED),
                "An empty exchange has no sale to wait for");
        check(!BankSellerBatchPolicy.hasOnlyOwnedSales(null, ALL_OWNED),
                "Unknown offers must deny fill grace");
        check(!BankSellerBatchPolicy.hasOnlyOwnedSales(Arrays.copyOf(soldBatch(), 3), ALL_OWNED),
                "A truncated offer array must deny fill grace");
        check(!BankSellerBatchPolicy.hasOnlyOwnedSales(Arrays.copyOf(soldBatch(), 9), ALL_OWNED),
                "An unexpected array shape must deny fill grace");
        check(!BankSellerBatchPolicy.hasOnlyOwnedSales(soldBatch(), null),
                "Unknown ownership must deny fill grace");
        offers = soldBatch();
        offers[7] = null;
        check(!BankSellerBatchPolicy.hasOnlyOwnedSales(offers, ALL_OWNED),
                "A missing locked slot must deny fill grace");
        offers[7] = offer(0, GrandExchangeOfferState.SELLING, 0);
        check(!BankSellerBatchPolicy.hasOnlyOwnedSales(offers, ALL_OWNED),
                "An invalid item identity must deny fill grace");
    }

    private static void fillGraceCompletesOnlyAfterEveryOwnedSaleFinishes() {
        check(BankSellerBatchPolicy.allOwnedSalesCompleted(soldBatch(), ALL_OWNED),
                "Every owned sale SOLD ends the short fill grace");
        GrandExchangeOffer[] offers = soldBatch();
        offers[2] = offer(202, GrandExchangeOfferState.SELLING, 4);
        check(!BankSellerBatchPolicy.allOwnedSalesCompleted(offers, ALL_OWNED),
                "A single still-selling offer should receive fill grace before subset collection");
        offers[2] = offer(202, GrandExchangeOfferState.SELLING, 10);
        check(!BankSellerBatchPolicy.allOwnedSalesCompleted(offers, ALL_OWNED),
                "Completion is proved by state, not merely quantitySold");
        offers[2] = offer(202, GrandExchangeOfferState.EMPTY, 0);
        check(BankSellerBatchPolicy.allOwnedSalesCompleted(offers, ALL_OWNED),
                "Empty slots do not prevent all remaining owned sales being complete");
        check(!BankSellerBatchPolicy.allOwnedSalesCompleted(emptyOffers(), ALL_OWNED),
                "Empty exchange has no completed sales for fill grace");
        check(!BankSellerBatchPolicy.allOwnedSalesCompleted(null, ALL_OWNED),
                "Unknown snapshot cannot finish fill grace");
        check(!BankSellerBatchPolicy.allOwnedSalesCompleted(soldBatch(), null),
                "Unknown ownership cannot finish fill grace");
        check(!BankSellerBatchPolicy.allOwnedSalesCompleted(soldBatch(), (slot, offer) -> slot != 1),
                "A completed foreign sale cannot finish fill grace");
        offers[7] = offer(207, GrandExchangeOfferState.CANCELLED_SELL, 0);
        check(!BankSellerBatchPolicy.allOwnedSalesCompleted(offers, ALL_OWNED),
                "Cancellation must not be confused with a finished sale");
    }

    private static void batchSnapshotsDoNotRetainMutableOffers() {
        GrandExchangeOffer[] offers = soldBatch();
        FakeOffer original = (FakeOffer) offers[0];
        Map<Integer, Integer> snapshot = BankSellerBatchPolicy.completedOwnedBatch(offers, ALL_OWNED);
        original.itemId = 999;
        original.state = GrandExchangeOfferState.SELLING;
        offers[1] = offer(888, GrandExchangeOfferState.BUYING, 0);
        check(expectedBatch().equals(snapshot),
                "A batch must retain primitive slot/item identities after live offer objects mutate");
        offers = soldBatch();
        original = (FakeOffer) offers[1];
        original.state = GrandExchangeOfferState.CANCELLED_SELL;
        snapshot = BankSellerBatchPolicy.terminalOwnedBatch(offers, ALL_OWNED);
        original.itemId = 999;
        original.state = GrandExchangeOfferState.SELLING;
        check(expectedBatch().equals(snapshot),
                "A terminal batch must retain primitive identities after live canceled offers mutate");
    }

    private static void collectionNeedsReliableEmptySlots() {
        Map<Integer, Integer> expected = expectedBatch();
        check(!BankSellerBatchPolicy.batchIsEmpty(null, expected), "Unknown offers are not a resolved batch");
        check(!BankSellerBatchPolicy.batchIsEmpty(new GrandExchangeOffer[0], expected),
                "A missing array cannot prove collection");
        check(!BankSellerBatchPolicy.batchIsEmpty(Arrays.copyOf(emptyOffers(), 3), expected),
                "A truncated array cannot prove collection");
        check(!BankSellerBatchPolicy.batchIsEmpty(Arrays.copyOf(emptyOffers(), 9), expected),
                "An unexpected array shape cannot prove collection");
        check(!BankSellerBatchPolicy.batchIsEmpty(emptyOffers(), null), "Missing expected identities are unresolved");
        check(!BankSellerBatchPolicy.batchIsEmpty(emptyOffers(), Collections.emptyMap()),
                "No attempted batch cannot count as collection success");
        check(!BankSellerBatchPolicy.batchIsEmpty(soldBatch(), expected), "Still-SOLD slots are not collected");

        GrandExchangeOffer[] offers = emptyOffers();
        offers[1] = null;
        check(!BankSellerBatchPolicy.batchIsEmpty(offers, expected), "A missing expected slot is unresolved");
        offers[1] = offer(201, null, 0);
        check(!BankSellerBatchPolicy.batchIsEmpty(offers, expected), "Unknown expected-slot state is unresolved");
        offers[1] = offer(201, GrandExchangeOfferState.SOLD, 10);
        check(!BankSellerBatchPolicy.batchIsEmpty(offers, expected), "One remaining SOLD slot keeps the batch pending");
        offers[1] = offer(999, GrandExchangeOfferState.BUYING, 0);
        check(!BankSellerBatchPolicy.batchIsEmpty(offers, expected),
                "A replacement nonempty offer must not be mistaken for a collected slot");
        offers[1] = offer(0, GrandExchangeOfferState.EMPTY, 0);
        check(BankSellerBatchPolicy.batchIsEmpty(offers, expected), "All expected slots EMPTY proves resolution");
        offers[7] = offer(999, GrandExchangeOfferState.BUYING, 0);
        check(BankSellerBatchPolicy.batchIsEmpty(offers, expected),
                "Unrelated slots do not change whether the expected batch has resolved");
        check(!BankSellerBatchPolicy.batchIsEmpty(emptyOffers(), Collections.singletonMap(-1, 200)),
                "Negative expected slot indices must fail closed");
        check(!BankSellerBatchPolicy.batchIsEmpty(emptyOffers(), Collections.singletonMap(8, 200)),
                "Out-of-range expected slot indices must fail closed");
        offers = emptyOffers();
        offers[2] = offer(202, GrandExchangeOfferState.SELLING, 4);
        Map<Integer, Integer> completedSubset = expectedBatch();
        completedSubset.remove(2);
        check(BankSellerBatchPolicy.batchIsEmpty(offers, completedSubset),
                "Still-selling owned slots do not invalidate completed subset collection confirmation");
        offers[1] = offer(201, GrandExchangeOfferState.CANCELLED_SELL, 4);
        check(!BankSellerBatchPolicy.batchIsEmpty(offers, completedSubset),
                "A canceled expected slot remains unresolved until its items are collected");
    }

    private static GrandExchangeOffer[] emptyOffers() {
        GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
        for (int slot = 0; slot < offers.length; slot++) {
            offers[slot] = offer(0, GrandExchangeOfferState.EMPTY, 0);
        }
        return offers;
    }

    private static GrandExchangeOffer[] soldBatch() {
        GrandExchangeOffer[] offers = emptyOffers();
        for (int slot = 0; slot < 3; slot++) {
            offers[slot] = offer(200 + slot, GrandExchangeOfferState.SOLD, 10);
        }
        return offers;
    }

    private static Map<Integer, Integer> expectedBatch() {
        Map<Integer, Integer> expected = new LinkedHashMap<>();
        expected.put(0, 200);
        expected.put(1, 201);
        expected.put(2, 202);
        return expected;
    }

    private static FakeOffer offer(int itemId, GrandExchangeOfferState state, int quantitySold) {
        return new FakeOffer(itemId, state, quantitySold);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeOffer implements GrandExchangeOffer {
        private int itemId;
        private GrandExchangeOfferState state;
        private final int quantitySold;

        private FakeOffer(int itemId, GrandExchangeOfferState state, int quantitySold) {
            this.itemId = itemId;
            this.state = state;
            this.quantitySold = quantitySold;
        }

        @Override
        public int getQuantitySold() { return quantitySold; }

        @Override
        public int getItemId() { return itemId; }

        @Override
        public int getTotalQuantity() { return 10; }

        @Override
        public long getPrice() { return 50; }

        @Override
        public long getSpent() { return quantitySold * getPrice(); }

        @Override
        public GrandExchangeOfferState getState() { return state; }
    }
}
