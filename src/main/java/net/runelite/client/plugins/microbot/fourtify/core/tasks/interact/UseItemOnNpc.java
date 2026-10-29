package net.runelite.client.plugins.microbot.fourtify.core.tasks.interact;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.Area;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Npc;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.inventory.UseItemOnItem;

import java.util.List;
import java.util.function.Function;

/**
 * Uses a carried item on the nearest NPC of a name inside the area, such as a bucket on a dairy cow, once
 * settled as {@link UseItemOnItem#ready} says. With no such NPC in sight it waits.
 */
public class UseItemOnNpc implements Task {
    private final int itemId;
    private final String npcName;
    private final Function<GameState, ? extends Area> area;
    private final int settleTicks;
    private final int waitTicks;

    public UseItemOnNpc(int itemId, String npcName, Function<GameState, ? extends Area> area, int settleTicks, int waitTicks) {
        this.itemId = itemId;
        this.npcName = npcName;
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
        Npc target = InteractWithNpc.target(state, npcName, (List<String>) null, area.apply(state));
        if (target == null) {
            return null;
        }
        if (!act.useItemOn(itemId, target)) {
            throw new IllegalStateException("could not use item " + itemId + " on " + npcName);
        }
        return UseItemOnItem.reaction("use item " + itemId + " on the " + npcName, itemId, itemId, waitTicks);
    }
}
