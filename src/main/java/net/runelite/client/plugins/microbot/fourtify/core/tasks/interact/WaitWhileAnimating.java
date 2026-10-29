package net.runelite.client.plugins.microbot.fourtify.core.tasks.interact;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;


/** Waits while the player walks or does an action, so the runner does not count it as idle. Put it last. */
public class WaitWhileAnimating implements Task {
    @Override
    public boolean shouldRun(GameState state) {
        return !state.isIdle();
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        return null;
    }
}
