package net.runelite.client.plugins.microbot.fourtify.core.tasks.inventory;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.GroundItem;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Picks up the nearest wanted ground item within {@code reach} tiles, with inventory space. The pickup worked
 * when the inventory holds one more of that item ID; the wait covers walking the reach (1 tile a tick without
 * run energy) and the pickup tick. If the item vanishes first (despawned, or another player took it), this
 * task stops being picked and nothing counts as failed.
 */
public class PickUpItems implements Task {
    private final Predicate<GroundItem> wanted;
    private final int reach;

    public PickUpItems(Predicate<GroundItem> wanted, int reach) {
        this.wanted = wanted;
        this.reach = reach;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return !state.isFull() && nearest(state) != null;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        GroundItem item = nearest(state);
        if (!act.take(item)) {
            throw new IllegalStateException("could not take " + item.getName());
        }
        int id = item.getId();
        return new WaitFor("pick up the " + item.getName(), reach + 1, (before, now) -> now.count(id) > before.count(id));
    }

    /** The nearest wanted item within reach that the player may take. */
    GroundItem nearest(GameState state) {
        return state.getGroundItems().stream()
                .filter(i -> wanted.test(i) && !i.isForOthers() && state.distanceTo(i.getLocation()) <= reach)
                .min(Comparator.comparingInt(i -> state.distanceTo(i.getLocation())))
                .orElse(null);
    }
}
