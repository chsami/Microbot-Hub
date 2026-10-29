package net.runelite.client.plugins.microbot.fourtify.core.tasks.interact;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.Dialogue;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Chooses a dialogue option by its text: the first of {@code wanted} the open dialogue offers, matching an
 * option's whole text first (ignoring case), then a part of it. A dialogue offering none of them is left alone.
 * Put it above the task that starts the conversation. It worked when the dialogue closed or moved on within
 * {@value #WAIT_TICKS} ticks.
 */
public class ChooseOption implements Task {
    static final int WAIT_TICKS = 2;

    private final List<String> wanted;

    public ChooseOption(String... wanted) {
        this.wanted = Arrays.asList(wanted);
    }

    @Override
    public boolean shouldRun(GameState state) {
        return option(state) != null;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        String option = option(state);
        if (!act.chooseOption(option)) {
            throw new IllegalStateException("could not choose \"" + option + "\"");
        }
        Dialogue before = state.getDialogue();
        return new WaitFor("choose " + option, WAIT_TICKS, (was, now) -> !Objects.equals(now.getDialogue(), before));
    }

    /** The option to choose, as the dialogue shows it, or null. */
    String option(GameState state) {
        Dialogue dialogue = state.getDialogue();
        if (dialogue == null || dialogue.getOptions().isEmpty()) {
            return null;
        }
        for (String text : wanted) {
            for (String option : dialogue.getOptions()) {
                if (option.equalsIgnoreCase(text)) {
                    return option;
                }
            }
            for (String option : dialogue.getOptions()) {
                if (option.toLowerCase().contains(text.toLowerCase())) {
                    return option;
                }
            }
        }
        return null;
    }

    @Override
    public String name() {
        return "ChooseOption " + wanted;
    }
}
