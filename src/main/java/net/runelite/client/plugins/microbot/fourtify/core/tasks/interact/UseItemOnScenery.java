package net.runelite.client.plugins.microbot.fourtify.core.tasks.interact;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.Area;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Scenery;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.inventory.UseItemOnItem;

import java.util.function.Function;

/**
 * Uses a carried item on the nearest reachable object of a name inside the area, such as ore on a furnace or a
 * pot on a flour bin, once settled as {@link UseItemOnItem#ready} says. With no such object in sight it waits.
 */
public class UseItemOnScenery implements Task {
    private final int itemId;
    private final String objectName;
    private final Function<GameState, ? extends Area> area;
    private final int settleTicks;
    private final int waitTicks;

    public UseItemOnScenery(int itemId, String objectName, Function<GameState, ? extends Area> area, int settleTicks, int waitTicks) {
        this.itemId = itemId;
        this.objectName = objectName;
        this.area = area;
        this.settleTicks = settleTicks;
        this.waitTicks = waitTicks;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return UseItemOnItem.ready(state, settleTicks) && state.count(itemId) > 0;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        Scenery target = InteractWithScenery.target(state, objectName, null, area.apply(state));
        if (target == null) {
            return null;
        }
        if (!act.useItemOn(itemId, target)) {
            throw new IllegalStateException("could not use item " + itemId + " on " + objectName);
        }
        return UseItemOnItem.reaction("use item " + itemId + " on the " + objectName, itemId, itemId, waitTicks);
    }
}
