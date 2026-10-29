package net.runelite.client.plugins.microbot.fourtify.core.tasks.inventory;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Set;

/**
 * With a full inventory, drops these items by item ID only, so tools, bonds and other items are never touched.
 * Dropping a full inventory takes many ticks and the drop call blocks until it is done; it worked when none is
 * left within {@value #WAIT_TICKS} ticks after it returns. Some dropped and some not (player input cut it
 * short) leaves the inventory not full, so this task is no longer picked and the rest go with the next full
 * inventory.
 */
public class DropItems implements Task {
    static final int WAIT_TICKS = 3;

    private final Set<Integer> itemIds;
    private final String label;

    public DropItems(Set<Integer> itemIds, String label) {
        this.itemIds = itemIds;
        this.label = label;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return state.isFull() && state.hasAny(itemIds);
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        for (int id : itemIds) {
            if (state.count(id) > 0) {
                act.drop(id);
            }
        }
        return new WaitFor("drop the " + label, WAIT_TICKS, (before, now) -> !now.hasAny(itemIds));
    }
}
