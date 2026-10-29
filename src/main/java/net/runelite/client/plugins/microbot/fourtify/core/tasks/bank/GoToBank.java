package net.runelite.client.plugins.microbot.fourtify.core.tasks.bank;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.function.Predicate;

/** Walks to the nearest bank and opens it, when the bank is closed and {@code when} says a visit is due. */
public class GoToBank implements Task {
    /**
     * Microbot's bank helper stops walking once a booth is ready to click (Rs2InteractionApproach.isReady: on
     * screen, with the tile in front of it at most 16 tiles away by path), clicks it and returns, so the player
     * may still walk up to 16 tiles at 1 tile a tick without run energy, and the bank opens a tick after
     * arriving. A live run returned 3 tiles out and opened 4 ticks later.
     */
    static final int WAIT_TICKS = 16 + 2;

    private final Predicate<GameState> when;

    public GoToBank(Predicate<GameState> when) {
        this.when = when;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return !state.isBankOpen() && when.test(state);
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        act.openBank();
        return new WaitFor("open the bank", WAIT_TICKS, (before, now) -> now.isBankOpen());
    }
}
