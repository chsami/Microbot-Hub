package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.Value;
import net.runelite.api.GrandExchangeOfferState;

/** A copy of a live offer, including its zero-based slot. */
@Value
public class ExchangeOffer {
    int slot;
    int itemId;
    int quantity;
    int sold;
    long price;
    GrandExchangeOfferState state;

    public boolean empty() { return state == GrandExchangeOfferState.EMPTY; }
    public boolean sold() { return state == GrandExchangeOfferState.SOLD; }
    /** The four states where an offer is over and its slot still holds items or coins to collect. */
    public static boolean finished(GrandExchangeOfferState state) {
        return state == GrandExchangeOfferState.SOLD || state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL;
    }
    /** A cancelled one-coin sale returns the stock, which the user must inspect; the run stops instead of collecting. */
    public boolean cancelledOneCoinSale() { return state == GrandExchangeOfferState.CANCELLED_SELL && price == 1L; }
    /** Collecting returns the account's own items or coins, so any finished offer qualifies, except bonds and the case above. */
    public boolean collectable() {
        return finished(state) && !Bonds.IDS.contains(itemId) && !cancelledOneCoinSale();
    }
    public boolean pendingSale() { return state == GrandExchangeOfferState.SELLING && price == 1L; }
    public boolean matches(int id, int amount) {
        return itemId == id && quantity == amount && price == 1L
                && (state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.SOLD);
    }
}
