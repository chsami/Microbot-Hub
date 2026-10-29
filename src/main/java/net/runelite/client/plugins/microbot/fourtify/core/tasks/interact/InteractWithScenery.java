package net.runelite.client.plugins.microbot.fourtify.core.tasks.interact;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.Area;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Scenery;

import java.util.Comparator;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * When idle, clicks a menu action on the nearest reachable object of a name inside the work area, such as
 * "Chop down" on a tree or "Mine" on a rock. An object without the action (a felled tree, an empty rock) is
 * skipped; when none is left it waits, and the subclass's {@link #maxTicks()} caps that wait.
 * <p>
 * A click that works makes the player walk to the object or start the action's animation within a few ticks,
 * or {@code gained} (such as a log or XP) shows that fast. If none of these shows within {@code waitTicks}
 * ticks the click did nothing, which counts as a failure.
 */
public class InteractWithScenery implements Task {
    private final Function<GameState, String> objectName;
    private final Function<GameState, String> action;
    private final Function<GameState, ? extends Area> area;
    private final int waitTicks;
    private final BiPredicate<GameState, GameState> gained;

    public InteractWithScenery(Function<GameState, String> objectName, Function<GameState, String> action,
                            Function<GameState, ? extends Area> area, int waitTicks, BiPredicate<GameState, GameState> gained) {
        this.objectName = objectName;
        this.action = action;
        this.area = area;
        this.waitTicks = waitTicks;
        this.gained = gained;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return state.isIdle() && !state.inDialogue();
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        String name = objectName.apply(state);
        String verb = action.apply(state);
        Scenery target = target(state, name, verb, area.apply(state));
        if (target == null) {
            return null; // none usable now; wait for one
        }
        if (!act.interact(target, verb)) {
            throw new IllegalStateException("could not click " + target.getName());
        }
        return new WaitFor("walk to or " + verb.toLowerCase() + " the " + name, waitTicks, (before, now) ->
                now.isMoving() || now.isAnimating() || gained.test(before, now));
    }

    /**
     * The object to click: that name with that action (any, when null), inside the area, reachable, and nearest
     * to the player within twice the area's radius.
     */
    public static Scenery target(GameState state, String name, String action, Area area) {
        if (area == null) {
            return null;
        }
        return state.getObjects().stream()
                .filter(o -> o.getName().equals(name) && (action == null || o.hasAction(action)) && o.isReachable())
                .filter(o -> o.getLocation().distanceTo(area.getCentre()) <= area.getRadius())
                .filter(o -> state.distanceTo(o.getLocation()) <= area.getRadius() * 2)
                .min(Comparator.comparingInt(o -> state.distanceTo(o.getLocation())))
                .orElse(null);
    }
}
