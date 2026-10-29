package net.runelite.client.plugins.microbot.fourtify.core.engine;

import lombok.Value;
import net.runelite.client.plugins.microbot.fourtify.core.client.RunLog;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.List;

/**
 * One tick of a run: what the game looked like, which task path ran, which effect it waits for, what
 * happened (effects seen, failures, skips) and, on the last tick, why the run stopped. Written to the
 * {@link RunLog} as one JSON line, so a live run can be followed without a screen and replayed in tests.
 */
@Value
public class TickEntry {
    GameState state;
    String path;
    /** Such as "ChopTree: start walking to or chopping the Tree"; null when nothing is pending. */
    String waitingFor;
    List<String> events;
    /** Why the run stopped on this tick, or null. */
    String stop;
}
