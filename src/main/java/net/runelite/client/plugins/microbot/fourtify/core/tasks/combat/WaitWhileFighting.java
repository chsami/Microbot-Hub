package net.runelite.client.plugins.microbot.fourtify.core.tasks.combat;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Npc;

/**
 * Holds the tasks below it while the player fights (see {@link #fighting}). Tasks
 * above it, such as eating, still cut in. No break or logout during a fight.
 */
public class WaitWhileFighting implements Task {
    @Override
    public boolean shouldRun(GameState state) {
        return fighting(state);
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        return null;
    }

    @Override
    public boolean canBreak() {
        return false;
    }

    /**
     * The player's target is an NPC that can be attacked and is still alive (its health unknown, or above 0), outside
     * a dialogue: talking to an NPC makes it the player's target too.
     */
    public static boolean fighting(GameState state) {
        if (state.inDialogue()) {
            return false;
        }
        for (Npc npc : state.npcs()) {
            if (npc.isPlayerTarget() && npc.hasAction(AttackNpc.ATTACK) && npc.getHealthPercent() != 0) {
                return true;
            }
        }
        return false;
    }
}
