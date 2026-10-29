package net.runelite.client.plugins.microbot.fourtify.core.tasks.movement;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.Area;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.function.Function;

/**
 * Walks to the work area when idle and outside it: at the start, and after banking. Walking up to an object
 * can briefly leave the area, so it never cuts in while moving. Uses the full walker, which handles stairs and
 * doors (bank floors), and blocks until it arrives. Only a walk the walker finds no path for reports the area
 * unreachable, so the caller can choose another. A walk cut short (the script stopping, a break, the player
 * taking over) counts as a failed step but keeps the area.
 */
public class WalkToArea implements Task {
    static final int ARRIVAL_DISTANCE = 3;
    static final int ARRIVAL_WAIT_TICKS = 2;
    /** Caps retries while the walker answers "moving" without walking (pathfinder still busy, player not loaded). */
    static final int MAX_TICKS = 10;

    private final Function<GameState, ? extends Area> area;
    private final Runnable unreachable;
    /** The area last looked at, only for the task's name in logs. */
    private Area shown;

    public WalkToArea(Function<GameState, ? extends Area> area, Runnable unreachable) {
        this.area = area;
        this.unreachable = unreachable;
    }

    @Override
    public boolean shouldRun(GameState state) {
        Area target = area.apply(state);
        shown = target;
        return target != null && state.getPosition() != null && !state.isBankOpen()
                && state.distanceTo(target.getCentre()) > target.getRadius() + ARRIVAL_DISTANCE
                && state.isIdle();
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        Area target = area.apply(state);
        Actions.Walk result = act.walkTo(target.getCentre(), ARRIVAL_DISTANCE);
        if (result == Actions.Walk.UNREACHABLE) {
            // Not a failure of this task: the caller drops the area and the next tick walks to another one.
            unreachable.run();
            return null;
        }
        if (result == Actions.Walk.CUT_SHORT) {
            throw new IllegalStateException("walk to " + target + " was cut short");
        }
        if (result == Actions.Walk.ARRIVED) {
            // The walker returns once it has arrived, so the next snapshot must already be there.
            return new WaitFor("arrive at " + target, ARRIVAL_WAIT_TICKS,
                    (before, now) -> now.distanceTo(target.getCentre()) <= target.getRadius() + ARRIVAL_DISTANCE);
        }
        return null;
    }

    @Override
    public int maxTicks() {
        return MAX_TICKS;
    }

    @Override
    public String name() {
        return "WalkToArea" + (shown == null ? "" : " (" + shown + ")");
    }
}
