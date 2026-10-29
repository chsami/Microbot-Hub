package net.runelite.client.plugins.microbot.fourtify.core.tasks.inventory;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.List;

/**
 * Uses the first carried of some items with an inventory action, such as "Bury" on bones or "Drink" on a
 * potion, while the bank is closed (an open bank shows deposit options instead). It worked when one fewer of
 * that item is carried within {@code waitTicks} ticks.
 */
public class UseItem implements Task {
    private final String label;
    private final List<Integer> itemIds;
    private final String action;
    private final int waitTicks;

    public UseItem(String label, List<Integer> itemIds, String action, int waitTicks) {
        this.label = label;
        this.itemIds = itemIds;
        this.action = action;
        this.waitTicks = waitTicks;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return !state.isBankOpen() && state.hasAny(itemIds);
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        int id = itemIds.stream().filter(i -> state.count(i) > 0).findFirst().orElseThrow(IllegalStateException::new);
        if (!act.useItem(id, action)) {
            throw new IllegalStateException("could not " + action.toLowerCase() + " item " + id);
        }
        return new WaitFor(action.toLowerCase() + " the " + label, waitTicks, (before, now) -> now.count(id) < before.count(id));
    }

    @Override
    public String name() {
        return "UseItem " + action + " " + label;
    }
}
