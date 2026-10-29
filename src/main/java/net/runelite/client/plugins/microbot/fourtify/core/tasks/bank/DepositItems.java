package net.runelite.client.plugins.microbot.fourtify.core.tasks.bank;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Set;
import java.util.function.Function;

/** With the bank open, deposits these items by item ID, each kind carried; everything else stays. */
public class DepositItems implements Task {
    static final int WAIT_TICKS = 3;

    private final Function<GameState, Set<Integer>> itemIds;
    private final String label;

    public DepositItems(Set<Integer> itemIds, String label) {
        this(state -> itemIds, label);
    }

    /** The items to deposit can change with the state, such as whatever the activity does not need. */
    public DepositItems(Function<GameState, Set<Integer>> itemIds, String label) {
        this.itemIds = itemIds;
        this.label = label;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return state.isBankOpen() && state.hasAny(itemIds.apply(state));
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        Set<Integer> ids = itemIds.apply(state);
        for (int id : ids) {
            if (state.count(id) > 0) {
                act.deposit(id);
            }
        }
        return new WaitFor("deposit the " + label, WAIT_TICKS, (before, now) -> !now.hasAny(ids));
    }
}
