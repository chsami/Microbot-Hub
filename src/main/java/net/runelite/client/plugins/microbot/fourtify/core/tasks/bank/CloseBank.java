package net.runelite.client.plugins.microbot.fourtify.core.tasks.bank;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;


/** Closes the bank. Put it after the tasks that use the open bank, so it runs once they are done. */
public class CloseBank implements Task {
    static final int WAIT_TICKS = 2;

    @Override
    public boolean shouldRun(GameState state) {
        return state.isBankOpen();
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        act.closeBank();
        return new WaitFor("close the bank", WAIT_TICKS, (before, now) -> !now.isBankOpen());
    }
}
