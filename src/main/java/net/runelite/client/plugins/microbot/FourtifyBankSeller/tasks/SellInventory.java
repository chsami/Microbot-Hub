package net.runelite.client.plugins.microbot.FourtifyBankSeller.tasks;

import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeOffer;
import net.runelite.client.plugins.microbot.fourtify.core.game.ExchangeState;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.SaleItem;

final class SellInventory implements Task {
    public boolean shouldRun(GameState state) {
        return !state.isBankOpen() && state.getExchange() != null && state.getExchange().isOpen()
                && (SaleCandidates.hasBatch(state) || state.getExchange().isSetupOpen()
                || state.getExchange().isDetailsOpen() || state.getExchange().isPriceWarning());
    }

    static SaleItem setupItem(GameState state) {
        ExchangeState exchange = state.getExchange();
        if (exchange == null || !exchange.isSetupOpen() || exchange.getSetupType() != 1) return null;
        return SaleCandidates.inventory(state).stream().filter(i -> i.getItemId() == exchange.getSetupItemId())
                .findFirst().orElse(null);
    }

    static boolean exactSetup(GameState state) {
        SaleItem item = setupItem(state);
        return item != null && state.getExchange().getSetupQuantity() == item.getQuantity()
                && state.getExchange().getSetupPrice() == 1L;
    }

    public WaitFor run(GameState state, Actions act) {
        ExchangeState exchange = state.getExchange();
        if (exchange.isPriceWarning()) {
            if (!exactSetup(state)) throw new IllegalStateException("Sale warning does not match an exact one-coin sale; inspect the offer");
            SaleItem item = setupItem(state);
            act.confirmSaleWarning(item.getItemId(), item.getQuantity());
            return confirmed(state, item, false);
        }
        if (exchange.isDetailsOpen()) return overview(act);
        if (exchange.isSetupOpen()) {
            SaleItem item = setupItem(state);
            if (item == null) return overview(act);
            if (exchange.isPriceInput()) {
                act.typeOfferPriceOne();
                return new WaitFor("exact one-coin price entered", BankSeller.CLICK_WAIT,
                        (before, now) -> exactSetup(now) && !now.getExchange().isPriceInput());
            }
            if (exchange.getSetupQuantity() != item.getQuantity()) {
                act.offerAll();
                return new WaitFor("all sale stock selected", BankSeller.CLICK_WAIT, (before, now) ->
                        setupItem(now) != null && now.getExchange().getSetupItemId() == item.getItemId()
                                && now.getExchange().getSetupQuantity() == setupItem(now).getQuantity());
            }
            if (exchange.getSetupPrice() != 1L) {
                act.enterOfferPrice();
                return new WaitFor("sale price input opened", BankSeller.CLICK_WAIT,
                        (before, now) -> setupItem(now) != null && now.getExchange().isPriceInput());
            }
            act.confirmSale(item.getItemId(), item.getQuantity());
            return confirmed(state, item, true);
        }
        ExchangeOffer finished = exchange.getOffers().stream().filter(ExchangeOffer::collectable).findFirst().orElse(null);
        if (finished != null) {
            act.collectSale(finished.getSlot());
            return WaitFor.once("finished offer collected", BankSeller.ACK_WAIT, (before, now) ->
                    now.getExchange() != null && now.getExchange().getOffers().stream()
                            .anyMatch(o -> o.getSlot() == finished.getSlot() && o.empty()));
        }
        if (!exchange.freeOfferSlot() || SaleCandidates.inventory(state).isEmpty()) return null;
        SaleItem item = SaleCandidates.inventory(state).get(0);
        act.offerItem(item.getInventoryId());
        return new WaitFor("sale setup opened for " + item.getName(), BankSeller.CLICK_WAIT,
                (before, now) -> setupItem(now) != null && now.getExchange().getSetupItemId() == item.getItemId());
    }

    private WaitFor overview(Actions act) {
        act.exchangeOverview();
        return new WaitFor("exchange overview opened", BankSeller.CLICK_WAIT, (before, now) ->
                now.getExchange() != null && !now.getExchange().isSetupOpen() && !now.getExchange().isDetailsOpen());
    }

    private WaitFor confirmed(GameState state, SaleItem item, boolean allowWarning) {
        Set<Integer> empty = state.getExchange().getOffers().stream().filter(ExchangeOffer::empty)
                .map(ExchangeOffer::getSlot).collect(Collectors.toSet());
        long carried = SaleCandidates.held(state, item.getItemId());
        return WaitFor.once("exact sale acknowledged", BankSeller.ACK_WAIT, (before, now) -> {
            if (now.getExchange() == null) return false;
            if (allowWarning && now.getExchange().isPriceWarning() && exactSetup(now)) return true;
            return SaleCandidates.held(now, item.getItemId()) == carried - item.getQuantity()
                    && now.getExchange().getOffers().stream().anyMatch(o -> empty.contains(o.getSlot())
                    && o.matches(item.getItemId(), item.getQuantity()));
        });
    }
}
