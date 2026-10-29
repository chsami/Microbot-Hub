package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import java.util.List;

/** Bank sale candidates and the GE interface, requested only by exchange activities. */
@Value
@Builder(toBuilder = true)
public class ExchangeState {
    boolean open;
    boolean setupOpen;
    boolean detailsOpen;
    boolean bankLoaded;
    /** The account is a member, whatever the world: it unlocks offer slots 4 to 8. Reads false in older run logs (3 slots). */
    boolean membersAccount;
    int setupItemId;
    int setupQuantity;
    int setupType;
    long setupPrice;
    /** True only for the numeric price input opened by the GE price button. */
    boolean priceInput;
    boolean priceWarning;
    @Singular("bankSale") List<SaleItem> bankSales;
    @Singular("inventorySale") List<SaleItem> inventorySales;
    @Singular List<ExchangeOffer> offers;

    public boolean pendingSales() { return offers.stream().anyMatch(ExchangeOffer::pendingSale); }
    public boolean collectableOffers() { return offers.stream().anyMatch(ExchangeOffer::collectable); }
    public boolean freeOfferSlot() { return offers.stream().anyMatch(ExchangeOffer::empty); }
}
