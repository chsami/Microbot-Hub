package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinishOffersTest {
    @Test
    void coinsAndKnownEmptyOffersPermitACompletedSnapshot() {
        FinishOffers result = FinishOffers.capture(emptyOffers(), inventory(new Item(ItemID.COINS, 500)));
        assertNotNull(result);
        assertEquals(-1, result.buySlot);
        assertTrue(result.noCollectables);
        assertTrue(result.noUnlistedItems);
        assertTrue(result.noActiveSells);
        assertEquals(27, result.emptyInventorySlots);
    }

    @Test
    void selectsTheFirstActiveBuyAndNeverSelectsASellOrTerminalOffer() {
        GrandExchangeOffer[] offers = emptyOffers();
        offers[0] = offer(GrandExchangeOfferState.SELLING);
        offers[1] = offer(GrandExchangeOfferState.CANCELLED_BUY);
        offers[2] = offer(GrandExchangeOfferState.BOUGHT);
        offers[4] = offer(GrandExchangeOfferState.BUYING);
        offers[7] = offer(GrandExchangeOfferState.BUYING);
        FinishOffers result = FinishOffers.capture(offers, inventory(new Item(ItemID.COINS, 1)));
        assertNotNull(result);
        assertEquals(4, result.buySlot);
        assertFalse(result.noCollectables);
        assertFalse(result.noActiveSells);
        assertTrue(FinishOffers.isActiveBuy(offers, 4));
        assertTrue(FinishOffers.isActiveBuy(offers, 7));
        assertFalse(FinishOffers.isActiveBuy(offers, 0));
        assertFalse(FinishOffers.isActiveBuy(offers, 1));
        assertFalse(FinishOffers.isActiveBuy(offers, 2));
    }

    @Test
    void onlyBuyingAuthorizesCancellationForEveryKnownOfferState() {
        for (GrandExchangeOfferState state : GrandExchangeOfferState.values()) {
            GrandExchangeOffer[] offers = emptyOffers();
            offers[3] = offer(state);
            assertEquals(state == GrandExchangeOfferState.BUYING, FinishOffers.isActiveBuy(offers, 3));
        }
    }

    @Test
    void missingOrIncompleteOfferDataCannotBeTreatedAsEmpty() {
        assertUnavailableOffers(null);
        assertUnavailableOffers(new GrandExchangeOffer[0]);
        assertUnavailableOffers(Arrays.copyOf(emptyOffers(), 7));
        assertUnavailableOffers(Arrays.copyOf(emptyOffers(), 9));
        GrandExchangeOffer[] missingSlot = emptyOffers();
        missingSlot[7] = null;
        assertUnavailableOffers(missingSlot);
        GrandExchangeOffer[] missingState = emptyOffers();
        missingState[7] = offer(null);
        assertUnavailableOffers(missingState);
    }

    @Test
    void anUnreadableOfferCannotAuthorizeCancellationOrCompletion() {
        GrandExchangeOffer[] offers = emptyOffers();
        offers[2] = (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                throw new IllegalStateException("Offer read unavailable");
            });
        assertUnavailableOffers(offers);
    }

    @Test
    void targetMustBeValidAndTheOtherSlotsMustAlsoBeKnown() {
        GrandExchangeOffer[] offers = emptyOffers();
        offers[3] = offer(GrandExchangeOfferState.BUYING);
        assertFalse(FinishOffers.isActiveBuy(offers, -1));
        assertFalse(FinishOffers.isActiveBuy(offers, 8));
        offers[7] = offer(null);
        assertFalse(FinishOffers.isActiveBuy(offers, 3));
    }

    @Test
    void eachTerminalOfferRequiresCollectionBeforeCompletion() {
        for (GrandExchangeOfferState state : new GrandExchangeOfferState[]{
            GrandExchangeOfferState.BOUGHT, GrandExchangeOfferState.SOLD,
            GrandExchangeOfferState.CANCELLED_BUY, GrandExchangeOfferState.CANCELLED_SELL}) {
            GrandExchangeOffer[] offers = emptyOffers();
            offers[5] = offer(state);
            FinishOffers result = FinishOffers.capture(offers, inventory(new Item(ItemID.COINS, 1)));
            assertNotNull(result);
            assertFalse(result.noCollectables);
            assertEquals(-1, result.buySlot);
            assertTrue(result.noActiveSells);
        }
    }

    @Test
    void anActiveSellRemainsActiveWithoutReadingTradeAmounts() {
        GrandExchangeOffer[] offers = emptyOffers();
        // The proxy rejects quantity/price reads, including partially filled offer amounts.
        offers[5] = offer(GrandExchangeOfferState.SELLING);
        FinishOffers result = FinishOffers.capture(offers, inventory(new Item(ItemID.COINS, 1)));
        assertNotNull(result);
        assertFalse(result.noActiveSells);
        assertTrue(result.noCollectables);
        assertEquals(-1, result.buySlot);
    }

    @Test
    void missingOrUnreadableInventoryCannotReportNoUnlistedItems() {
        assertNull(FinishOffers.capture(emptyOffers(), null));
        assertNull(FinishOffers.capture(emptyOffers(), inventory((Item[]) null)));
        assertNull(FinishOffers.capture(emptyOffers(), inventory(new Item[0])));
        assertNull(FinishOffers.capture(emptyOffers(), inventory(new Item[]{null})));
        ItemContainer unreadable = (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
            new Class<?>[]{ItemContainer.class}, (proxy, method, args) -> {
                throw new IllegalStateException("Inventory read unavailable");
            });
        assertNull(FinishOffers.capture(emptyOffers(), unreadable));
    }

    @Test
    void negativeQuantitiesAndInvalidOccupiedSlotsAreUnavailable() {
        for (Item malformed : new Item[]{new Item(ItemID.COINS, -1), new Item(4151, -1),
            new Item(-1, -1), new Item(-1, 1), new Item(0, 1)}) {
            assertNull(FinishOffers.capture(emptyOffers(), inventory(malformed)));
        }
        Item[] oversized = new Item[29];
        Arrays.fill(oversized, new Item(-1, 0));
        assertNull(FinishOffers.capture(emptyOffers(), inventory(oversized)));
    }

    @Test
    void emptySlotsAndZeroQuantitiesDoNotBecomeItemsForSale() {
        FinishOffers result = FinishOffers.capture(emptyOffers(), inventory(
            new Item(-1, 0), new Item(0, 0), new Item(4151, 0), new Item(ItemID.COINS, Integer.MAX_VALUE)));
        assertNotNull(result);
        assertTrue(result.noUnlistedItems);
        assertEquals(27, result.emptyInventorySlots);
    }

    @Test
    void aNonCoinItemOrStackMustBeListedBeforeCompletion() {
        for (int quantity : new int[]{1, Integer.MAX_VALUE}) {
            FinishOffers result = FinishOffers.capture(emptyOffers(), inventory(
                new Item(ItemID.COINS, 1), new Item(4151, quantity)));
            assertNotNull(result);
            assertFalse(result.noUnlistedItems);
            assertEquals(26, result.emptyInventorySlots);
        }
        FinishOffers fakeCoins = FinishOffers.capture(emptyOffers(), inventory(new Item(617, 1)));
        assertNotNull(fakeCoins);
        assertFalse(fakeCoins.noUnlistedItems);
    }

    @Test
    void fullInventoryHasNoCollectionSpaceAndTrimmedArraysKeepTrailingEmptySlots() {
        Item[] items = new Item[28];
        Arrays.fill(items, new Item(4151, 1));
        FinishOffers full = FinishOffers.capture(emptyOffers(), inventory(items));
        assertNotNull(full);
        assertEquals(0, full.emptyInventorySlots);
        FinishOffers trimmed = FinishOffers.capture(emptyOffers(), inventory(new Item(ItemID.COINS, 1)));
        assertNotNull(trimmed);
        assertEquals(27, trimmed.emptyInventorySlots);
    }

    @Test
    void snapshotsDoNotRetainMutableOffersOrInventoryItems() {
        GrandExchangeOffer[] offers = emptyOffers();
        AtomicReference<GrandExchangeOfferState> state = new AtomicReference<>(GrandExchangeOfferState.BUYING);
        offers[2] = (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                if (method.getName().equals("getState")) return state.get();
                throw new AssertionError("Unexpected offer read: " + method.getName());
            });
        Item[] items = new Item[]{new Item(ItemID.COINS, 1)};
        FinishOffers captured = FinishOffers.capture(offers, inventory(items));
        assertNotNull(captured);
        state.set(GrandExchangeOfferState.SELLING);
        items[0] = new Item(4151, 1);
        assertEquals(2, captured.buySlot);
        assertTrue(captured.noUnlistedItems);
        assertTrue(captured.noActiveSells);
        assertFalse(FinishOffers.isActiveBuy(offers, 2));
        FinishOffers refreshed = FinishOffers.capture(offers, inventory(items));
        assertNotNull(refreshed);
        assertEquals(-1, refreshed.buySlot);
        assertFalse(refreshed.noUnlistedItems);
        assertFalse(refreshed.noActiveSells);
    }

    private static void assertUnavailableOffers(GrandExchangeOffer[] offers) {
        assertNull(FinishOffers.capture(offers, inventory(new Item(ItemID.COINS, 1))));
        assertFalse(FinishOffers.isActiveBuy(offers, 0));
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

    private static ItemContainer inventory(Item... items) {
        return (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
            new Class<?>[]{ItemContainer.class}, (proxy, method, args) -> {
                if (method.getName().equals("getItems")) return items;
                throw new AssertionError("Unexpected inventory read: " + method.getName());
            });
    }
}
