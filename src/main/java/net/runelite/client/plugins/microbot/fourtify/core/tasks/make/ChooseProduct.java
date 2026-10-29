package net.runelite.client.plugins.microbot.fourtify.core.tasks.make;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

/**
 * Chooses a product in the open make menu, by item ID, to make as many as the materials allow. The menu closes
 * one tick after a choice, so the choice worked when it closed within
 * {@value #WAIT_TICKS} ticks. A level-up stops making; the next use of the materials opens the menu again.
 */
public class ChooseProduct implements Task {
    static final int WAIT_TICKS = 2;

    private final int productId;

    public ChooseProduct(int productId) {
        this.productId = productId;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return state.makeOptions().contains(productId);
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        if (!act.make(state.makeOptions().indexOf(productId))) {
            throw new IllegalStateException("could not choose item " + productId + " in the make menu");
        }
        return new WaitFor("make item " + productId, WAIT_TICKS, (before, now) -> !now.isMakeMenuOpen());
    }
}
