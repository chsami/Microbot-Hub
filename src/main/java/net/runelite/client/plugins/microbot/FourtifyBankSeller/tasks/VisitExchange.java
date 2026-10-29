package net.runelite.client.plugins.microbot.FourtifyBankSeller.tasks;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

final class VisitExchange implements Task {
    public boolean shouldRun(GameState state) { return !state.isBankOpen(); }

    public WaitFor run(GameState state, Actions act) {
        if (state.getExchange() == null) return null;
        if (SaleCandidates.hasBatch(state)) {
            if (state.getExchange().isOpen()) return null;
            act.openExchange();
            return new WaitFor("exchange opened", BankSeller.CLICK_WAIT,
                    (before, now) -> now.getExchange() != null && now.getExchange().isOpen());
        }
        if (state.getExchange().isOpen()) {
            act.closeExchange();
            return new WaitFor("exchange closed", BankSeller.CLICK_WAIT,
                    (before, now) -> now.getExchange() != null && !now.getExchange().isOpen());
        }
        act.openBank();
        return new WaitFor("live bank opened", BankSeller.CLICK_WAIT,
                (before, now) -> now.isBankOpen() && now.getExchange() != null && now.getExchange().isBankLoaded());
    }
}
