package net.runelite.client.plugins.microbot.fourtify.core.tasks.guards;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

/**
 * Holds every task after it while a cutscene plays: the game moves the player and the camera, and clicks do
 * nothing until it ends. Put it after {@link ContinueDialogue}, which continues the cutscene's dialogues.
 */
public class WaitForCutscene implements Task {
    @Override
    public boolean shouldRun(GameState state) {
        return state.isInCutscene();
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        return null;
    }
}
