package net.runelite.client.plugins.microbot.fourtify.core.tasks.interact;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.function.Predicate;

/**
 * Holds the tasks below it while {@code condition} holds, without clicking, such as waiting in a minigame lobby
 * for the round to start. The stuck timer still counts the wait, so the activity's limits must cover it.
 */
public class WaitWhile implements Task {
    private final String what;
    private final Predicate<GameState> condition;

    public WaitWhile(String what, Predicate<GameState> condition) {
        this.what = what;
        this.condition = condition;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return condition.test(state);
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        return null;
    }

    @Override
    public String name() {
        return "WaitWhile " + what;
    }
}
