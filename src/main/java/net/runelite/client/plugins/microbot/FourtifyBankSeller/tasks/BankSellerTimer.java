package net.runelite.client.plugins.microbot.FourtifyBankSeller.tasks;

import java.util.List;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StuckTimer;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeOffer;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeState;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

/**
 * An offer has no guaranteed fill time; interface actions still have bounded effect waits. While the only thing
 * going on is waiting for one-coin sales, the generic limit is held off and {@link BankSeller#SALE_WAIT} counts
 * from the last change to any offer: a fill, a collection or a new offer.
 */
public final class BankSellerTimer extends StuckTimer {
    private List<ExchangeOffer> lastOffers;
    private int progressTick;

    public BankSellerTimer() { super(BankSeller.IDLE_LIMIT, 0); }

    /** A pause (the script skipped ticks) starts the sale wait afresh too. */
    @Override
    public void reset() {
        super.reset();
        lastOffers = null;
    }

    private static boolean waitingForSales(GameState state) {
        ExchangeState exchange = state.getExchange();
        return exchange != null && !state.isBankOpen() && exchange.isOpen()
                && !exchange.isSetupOpen() && !exchange.isDetailsOpen()
                && !exchange.isPriceWarning() && !exchange.collectableOffers()
                && exchange.pendingSales();
    }

    @Override
    public String check(GameState state) {
        if (waitingForSales(state)) {
            // ExchangeOffer is a value type, so an unchanged list means no fill, collection or new offer.
            List<ExchangeOffer> offers = state.getExchange().getOffers();
            if (!offers.equals(lastOffers)) {
                lastOffers = offers;
                progressTick = state.getTick();
            } else if (state.getTick() - progressTick >= BankSeller.SALE_WAIT) {
                return "no one-coin sale filled, was collected or started for " + BankSeller.SALE_WAIT
                        + " ticks; check the exchange offers";
            }
            super.reset();
        } else {
            lastOffers = null;
        }
        return super.check(state);
    }
}
