package net.runelite.client.plugins.microbot.fourtify.core.tasks.inventory;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

/**
 * Uses one carried item on another, such as a knife on logs, to open the make menu or make a product. Runs once
 * the player has been idle for {@code settleTicks}, so a gap between two products is not taken for the end of
 * making, and never with the bank or the make menu open.
 */
public class UseItemOnItem implements Task {
    private final int itemId;
    private final int otherItemId;
    private final int settleTicks;
    private final int waitTicks;

    public UseItemOnItem(int itemId, int otherItemId, int settleTicks, int waitTicks) {
        this.itemId = itemId;
        this.otherItemId = otherItemId;
        this.settleTicks = settleTicks;
        this.waitTicks = waitTicks;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return ready(state, settleTicks) && state.count(itemId) > 0 && state.count(otherItemId) > 0;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        if (!act.useItemOnItem(itemId, otherItemId)) {
            throw new IllegalStateException("could not use item " + itemId + " on item " + otherItemId);
        }
        return reaction("use item " + itemId + " on item " + otherItemId, itemId, otherItemId, waitTicks);
    }

    /** Idle for {@code settleTicks}, with the bank, the make menu and any dialogue closed. */
    public static boolean ready(GameState state, int settleTicks) {
        return state.getIdleTicks() >= settleTicks && !state.isBankOpen() && !state.isMakeMenuOpen() && !state.inDialogue();
    }

    /**
     * Using an item worked when the player walks, acts, gets the make menu or a dialogue, or has fewer of the
     * items, within {@code waitTicks} ticks.
     */
    public static WaitFor reaction(String what, int itemId, int otherItemId, int waitTicks) {
        return new WaitFor(what, waitTicks, (before, now) -> now.isMoving() || now.isAnimating() || now.isMakeMenuOpen()
                || now.inDialogue() || now.count(itemId) < before.count(itemId) || now.count(otherItemId) < before.count(otherItemId));
    }
}
