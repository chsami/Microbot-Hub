package net.runelite.client.plugins.microbot.fourtify.core.tasks.guards;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.Dialogue;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Objects;

/**
 * Clicks "Click here to continue" on a dialogue with no options to choose, such as a level-up message, which
 * otherwise stays open. A dialogue with options is left to the activity's own tasks. It worked when the
 * dialogue closed or moved on within {@value #WAIT_TICKS} ticks.
 */
public class ContinueDialogue implements Task {
    static final int WAIT_TICKS = 2;

    @Override
    public boolean shouldRun(GameState state) {
        Dialogue dialogue = state.getDialogue();
        return dialogue != null && dialogue.isCanContinue() && dialogue.getOptions().isEmpty();
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        if (!act.continueDialogue()) {
            throw new IllegalStateException("could not continue the dialogue");
        }
        String text = state.getDialogue().getText();
        return new WaitFor("continue the dialogue", WAIT_TICKS,
                (before, now) -> now.getDialogue() == null || !Objects.equals(now.getDialogue().getText(), text));
    }
}
