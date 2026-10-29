package net.runelite.client.plugins.microbot.FourtifyWoodcutting.tasks;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Requirement;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.interact.InteractWithScenery;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.movement.SpotPicker;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.Axe;
import net.runelite.client.plugins.microbot.fourtify.core.game.Spot;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.TreeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Clicks the nearest reachable tree of the chosen type inside the spot, with that tree's own menu action
 * ("Chop down", or "Cut" on redwoods and "Cut down" on arctic pines). When all are felled it
 * waits; waiting longer than the tree's respawn time plus the click and walk ({@link #maxTicks()})
 * means something is wrong, and the failure log says where the player was.
 * <p>
 * A click that works makes the player walk to the tree or start the woodcutting animation within a few
 * ticks; a log or XP can also arrive that fast. If none of these shows within {@value #WAIT_TICKS} ticks the
 * click did nothing, which counts as a failure.
 * <p>
 * With a {@code drop} for spots, a click that did nothing is judged per spot instead (see {@link #MISSES_BEFORE_DROP}):
 * a spot where chopping does nothing, such as trees that need quest progress, is dropped for the run so the
 * next spot is used, and the task is not skipped for it.
 */
public class ChopTree extends InteractWithScenery {
    static final int WAIT_TICKS = 4;
    /**
     * Clicks in a row at one spot that do nothing before the spot is dropped. A single miss is usually a one-off,
     * such as another player felling the tree first, so it only counts; any click that lands (the player walks to the
     * tree or starts chopping) sets the count back to zero. Three in a row is the number of failures a task list
     * tolerates before it skips a task, and at one spot it points to the spot (a tree that gives nothing without
     * quest progress, or one the player cannot use) rather than to bad luck.
     */
    static final int MISSES_BEFORE_DROP = 3;

    private final TreePicker tree;
    private final Function<GameState, Spot> spot;
    private final Consumer<Spot> drop;
    /** Clicks in a row at each spot whose effect did not show. */
    private final Map<Spot, Integer> misses = new HashMap<>();

    public ChopTree(TreeType tree, Function<GameState, Spot> spot) {
        this(TreePicker.fixed(tree), spot);
    }

    public ChopTree(TreePicker tree, Function<GameState, Spot> spot) {
        this(tree, spot, null);
    }

    /** {@code drop} takes a spot out of the run; null leaves every miss a failure of the task. */
    public ChopTree(TreePicker tree, Function<GameState, Spot> spot, Consumer<Spot> drop) {
        super(state -> tree.apply(state).getObjectName(), state -> tree.apply(state).getAction(), spot, WAIT_TICKS,
                (before, now) -> now.xp(Skill.WOODCUTTING) > before.xp(Skill.WOODCUTTING)
                        || now.count(tree.apply(before).getLogId()) > before.count(tree.apply(before).getLogId()));
        this.tree = tree;
        this.spot = spot;
        this.drop = drop;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        WaitFor click = super.run(state, act);
        Spot clicked = spot.apply(state);
        if (click == null || drop == null || clicked == null) {
            return click;
        }
        return new WaitFor(click.getWhat(), click.getWaitTicks(), (before, now) -> {
            boolean landed = click.landed(before, now);
            if (landed) {
                misses.remove(clicked);
            }
            return landed;
        }).whenMissed(() -> miss(clicked));
    }

    private String miss(Spot clicked) {
        int count = misses.merge(clicked, 1, Integer::sum);
        if (count < MISSES_BEFORE_DROP) {
            return "miss " + count + " of " + MISSES_BEFORE_DROP + " at " + clicked;
        }
        misses.remove(clicked);
        drop.accept(clicked);
        return "dropped " + clicked + " after " + MISSES_BEFORE_DROP + " misses in a row";
    }

    /** Waiting for a felled spot to grow back: the longest respawn of the trees the picker may choose, plus the click and walk. */
    @Override
    public int maxTicks() {
        return Woodcutting.idleLimit(tree);
    }

    @Override
    public List<Requirement> requirements() {
        List<Requirement> requirements = new ArrayList<>();
        String base = (tree.isFixed() ? "a " + tree : "a tree") + " spot that is safe at your combat level and where chopping works";
        // A spot picker also says which gates of the spots are why none is found.
        requirements.add(spot instanceof SpotPicker ? ((SpotPicker) spot).requirement(base)
                : new Requirement(base, state -> spot.apply(state) != null));
        requirements.add(new Requirement("an axe for your Woodcutting level", Axe::any));
        requirements.add(new Requirement("free inventory space", state -> !state.isFull()));
        return requirements;
    }
}
