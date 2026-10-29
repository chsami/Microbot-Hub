package net.runelite.client.plugins.microbot.FourtifyBankSeller.tasks;

import net.runelite.client.plugins.microbot.fourtify.core.activity.Activity;
import net.runelite.client.plugins.microbot.fourtify.core.activity.ActivityQueue;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StopCondition;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeOffer;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeState;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

public final class BankSeller {
    // Microbot's exchange-open helper waits up to 5000 ms; a game tick lasts 600 ms.
    static final int CLICK_WAIT = 9;
    // Transaction acknowledgment guard chosen for this workflow, not a promise about sale fill time.
    static final int ACK_WAIT = 60;
    static final int IDLE_LIMIT = 3 * ACK_WAIT;
    // Longest wait for one-coin sales with no offer changing: 500 ticks, 5 minutes. Our choice, not a game value;
    // tune it from a live run.
    static final int SALE_WAIT = 500;

    private BankSeller() { }

    public static ActivityQueue queue() {
        return ActivityQueue.of(Activity.named("Bank seller").keepsInventory().managesBanking().withoutGuards()
                .stuckTimer(IDLE_LIMIT, 0)
                .stopWhen(new StopCondition("a one-coin sale was cancelled; inspect the returned stock", state ->
                        state.getExchange() != null && state.getExchange().getOffers().stream()
                                .anyMatch(ExchangeOffer::cancelledOneCoinSale)),
                        new StopCondition("no free Grand Exchange slot: free one and restart", BankSeller::noFreeSlot),
                        new StopCondition("sale warning does not match an exact one-coin sale", state ->
                                state.getExchange() != null && state.getExchange().isPriceWarning() && !SellInventory.exactSetup(state)),
                        new StopCondition("no inventory space for a sale stack; free a slot before restarting", BankSeller::noSpace))
                .work(new WithdrawSales(), new SellInventory(), new VisitExchange())
                .doneWhen(BankSeller::finished));
    }

    static boolean finished(GameState state) {
        return state.getExchange() != null && state.isBankOpen() && state.getExchange().isBankLoaded()
                && SaleCandidates.ranked(state.getExchange().getBankSales()).isEmpty() && !SaleCandidates.hasBatch(state)
                && state.getExchange().getOffers().stream().noneMatch(ExchangeOffer::cancelledOneCoinSale)
                && !state.getExchange().isPriceWarning();
    }

    /**
     * Stock is left to sell, but every slot holds an offer this run cannot clear: nothing to collect and no
     * one-coin sale that will finish. The offers are always listed when the exchange is read; an empty list means
     * they were not read.
     */
    static boolean noFreeSlot(GameState state) {
        ExchangeState exchange = state.getExchange();
        return exchange != null && !exchange.getOffers().isEmpty() && !SaleCandidates.inventory(state).isEmpty()
                && !exchange.freeOfferSlot() && !exchange.collectableOffers() && !exchange.pendingSales();
    }

    private static boolean noSpace(GameState state) {
        return state.isBankOpen() && state.getExchange() != null && state.getExchange().isBankLoaded()
                && !SaleCandidates.hasBatch(state) && !SaleCandidates.ranked(state.getExchange().getBankSales()).isEmpty()
                && SaleCandidates.ranked(state.getExchange().getBankSales()).stream().noneMatch(i ->
                        state.count(i.getInventoryId()) < Integer.MAX_VALUE
                                && (state.getFreeSlots() > 0 || state.count(i.getInventoryId()) > 0));
    }

    public static BankSellerTimer stuckTimer() { return new BankSellerTimer(); }
}
