package net.runelite.client.plugins.microbot.fourtify.core.tasks.guards;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Npc;

/**
 * Dismisses a random event. An event NPC picks one player, and that player can
 * right-click it and dismiss it; one ignored leaves after a minute. So an NPC after the player with a
 * "Dismiss" action is the player's random event. {@code ClientReader} always reads the NPCs after the player,
 * whatever names the activity asks for. Not with the bank open, which a click on an NPC would close. It worked
 * when the NPC is gone or no longer after the player within {@value #WAIT_TICKS} ticks.
 */
public class DismissRandomEvent implements Task {
    static final String DISMISS = "Dismiss";
    /** A guess: the click and the event leaving; the run log measures it. */
    static final int WAIT_TICKS = 3;

    @Override
    public boolean shouldRun(GameState state) {
        return !state.isBankOpen() && event(state) != null;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        Npc event = event(state);
        if (!act.interact(event, DISMISS)) {
            throw new IllegalStateException("could not dismiss " + event.getName());
        }
        return new WaitFor("dismiss " + event.getName(), WAIT_TICKS,
                (before, now) -> now.npcs().stream().noneMatch(npc -> npc.getIndex() == event.getIndex() && npc.isInteractingWithPlayer()));
    }

    private static Npc event(GameState state) {
        for (Npc npc : state.npcs()) {
            if (npc.isInteractingWithPlayer() && npc.hasAction(DISMISS)) {
                return npc;
            }
        }
        return null;
    }
}
