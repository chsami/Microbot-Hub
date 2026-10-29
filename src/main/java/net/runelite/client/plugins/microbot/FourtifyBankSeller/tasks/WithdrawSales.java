package net.runelite.client.plugins.microbot.FourtifyBankSeller.tasks;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.SaleItem;

final class WithdrawSales implements Task {
    public boolean shouldRun(GameState state) {
        return state.isBankOpen() && state.getExchange() != null;
    }

    public WaitFor run(GameState state, Actions act) {
        if (!state.getExchange().isBankLoaded()) return null;
        if (state.getExchange().pendingSales() || state.getExchange().collectableOffers()) return leave(state, act);
        if (state.getFreeSlots() == 0 && !SaleCandidates.inventory(state).isEmpty()) return leave(state, act);
        for (SaleItem item : SaleCandidates.ranked(state.getExchange().getBankSales())) {
            int held = state.count(item.getInventoryId());
            int amount = Math.min(item.getQuantity(), Integer.MAX_VALUE - held);
            if (amount <= 0 || state.getFreeSlots() <= 0 && held == 0) continue;
            int beforeBank = SaleCandidates.bankCount(state, item.getItemId());
            act.withdrawForSale(item.getItemId(), amount);
            return WaitFor.once("sale stack withdrawn", BankSeller.ACK_WAIT, (before, now) ->
                    now.isBankOpen() && SaleCandidates.bankCount(now, item.getItemId()) >= 0
                    && SaleCandidates.bankCount(now, item.getItemId()) <= beforeBank - amount
                    && now.count(item.getInventoryId()) >= (long) held + amount);
        }
        if (!SaleCandidates.inventory(state).isEmpty()) return leave(state, act);
        if (!SaleCandidates.ranked(state.getExchange().getBankSales()).isEmpty()) {
            throw new IllegalStateException("No inventory space for a sale stack; free a slot before restarting");
        }
        return null;
    }

    private WaitFor leave(GameState state, Actions act) {
        act.closeBank();
        return new WaitFor("bank closed for sale batch", BankSeller.CLICK_WAIT, (before, now) -> !now.isBankOpen());
    }
}
