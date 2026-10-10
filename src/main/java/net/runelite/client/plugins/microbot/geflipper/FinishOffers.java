package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;

/** Keeps only the offer and inventory flags needed to finish the current session. */
final class FinishOffers {
    private static final int OFFER_SLOTS = 8;
    private static final int INVENTORY_SLOTS = 28;

    final int buySlot;
    final boolean noCollectables;
    final boolean noUnlistedItems;
    final boolean noActiveSells;
    final int emptyInventorySlots;

    private FinishOffers(int buySlot, boolean noCollectables, boolean noUnlistedItems,
                         boolean noActiveSells, int emptyInventorySlots) {
        this.buySlot = buySlot;
        this.noCollectables = noCollectables;
        this.noUnlistedItems = noUnlistedItems;
        this.noActiveSells = noActiveSells;
        this.emptyInventorySlots = emptyInventorySlots;
    }

    /** Must be called with client-thread reads; unavailable data cannot report completion. */
    static FinishOffers capture(GrandExchangeOffer[] offers, ItemContainer inventory) {
        if (offers == null || offers.length != OFFER_SLOTS || inventory == null) return null;
        try {
            int buySlot = -1;
            boolean noCollectables = true;
            boolean noActiveSells = true;
            for (int slot = 0; slot < offers.length; slot++) {
                if (offers[slot] == null) return null;
                GrandExchangeOfferState state = offers[slot].getState();
                if (state == null) return null;
                switch (state) {
                    case BUYING:
                        if (buySlot == -1) buySlot = slot;
                        break;
                    case SELLING:
                        noActiveSells = false;
                        break;
                    case BOUGHT:
                    case SOLD:
                    case CANCELLED_BUY:
                    case CANCELLED_SELL:
                        noCollectables = false;
                        break;
                    case EMPTY:
                        break;
                    default:
                        return null;
                }
            }

            Item[] items = inventory.getItems();
            if (items == null || items.length == 0 || items.length > INVENTORY_SLOTS) return null;
            boolean noUnlistedItems = true;
            int occupiedSlots = 0;
            for (Item item : items) {
                if (item == null || item.getQuantity() < 0) return null;
                int quantity = item.getQuantity();
                if (quantity == 0) continue;
                if (item.getId() <= 0) return null;
                occupiedSlots++;
                if (item.getId() != ItemID.COINS) noUnlistedItems = false;
            }
            return new FinishOffers(buySlot, noCollectables, noUnlistedItems,
                noActiveSells, INVENTORY_SLOTS - occupiedSlots);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /** Rechecks the entire offer snapshot before authorizing a buy cancellation. */
    static boolean isActiveBuy(GrandExchangeOffer[] offers, int slot) {
        if (offers == null || offers.length != OFFER_SLOTS || slot < 0 || slot >= OFFER_SLOTS) {
            return false;
        }
        try {
            boolean activeBuy = false;
            for (int index = 0; index < offers.length; index++) {
                if (offers[index] == null) return false;
                GrandExchangeOfferState state = offers[index].getState();
                if (state == null) return false;
                if (index == slot) activeBuy = state == GrandExchangeOfferState.BUYING;
            }
            return activeBuy;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
