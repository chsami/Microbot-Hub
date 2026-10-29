package net.runelite.client.plugins.microbot.fourtify.core.tasks.guards;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Collection;

/**
 * Confirms a warning screen the activity expects, such as the one before a dangerous area it works in: one whose
 * text contains one of {@code expected} (any case). Any other warning is left open for the queue to stop on.
 * It worked when the screen closed within {@value #WAIT_TICKS} ticks.
 */
public class ConfirmWarning implements Task {
    static final int WAIT_TICKS = 2;

    private final Collection<String> expected;

    public ConfirmWarning(Collection<String> expected) {
        this.expected = expected;
    }

    /** Whether one of {@code expected} is in the warning's text. */
    public static boolean expects(Collection<String> expected, String warning) {
        return warning != null && expected.stream().anyMatch(part -> warning.toLowerCase().contains(part.toLowerCase()));
    }

    @Override
    public boolean shouldRun(GameState state) {
        return expects(expected, state.getWarning());
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        if (!act.confirmWarning()) {
            throw new IllegalStateException("could not confirm the warning");
        }
        return new WaitFor("confirm the warning", WAIT_TICKS, (before, now) -> now.getWarning() == null);
    }
}
