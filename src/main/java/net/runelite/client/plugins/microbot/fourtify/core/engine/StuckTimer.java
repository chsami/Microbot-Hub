package net.runelite.client.plugins.microbot.fourtify.core.engine;

import lombok.Value;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtify.core.activity.Activity;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Map;

/**
 * Notices a run that has stopped making progress while every task believes it is doing its job, such as a
 * player standing still after clicks that do nothing, or stuck in an action animation that never pays out.
 * <p>
 * Two limits, each set by the plugin from its activity's longest normal pause:
 * <ul>
 *     <li>{@code idleLimit}: no change of tile, XP, inventory or bank, and no action animation. An animation
 *     counts as progress because some actions take long to pay out.</li>
 *     <li>{@code gainLimit}: no change of XP or inventory and no move of more than {@value #FAR_TILES} tiles,
 *     animation or not. 0 turns it off. A long walk is not mistaken for being stuck; standing in an animation that
 *     never pays out, or walking back and forth on the spot, is.</li>
 * </ul>
 * Neither counts during a cutscene, while the game holds the player.
 */
public class StuckTimer {
    static final int FAR_TILES = 15;

    private final int idleLimit;
    private final int gainLimit;
    private Activity lastActivity;
    private Gain lastGain;
    private WorldPoint gainPosition;
    private int activityTick;
    private int gainTick;

    public StuckTimer(int idleLimit, int gainLimit) {
        this.idleLimit = idleLimit;
        this.gainLimit = gainLimit;
    }

    /** Starts counting afresh, such as after a pause: ticks in which the script did not run are not stuck. */
    public void reset() {
        lastActivity = null;
        lastGain = null;
        gainPosition = null;
    }

    private boolean movedFar(WorldPoint now) {
        return now != null && gainPosition != null
                && (now.getPlane() != gainPosition.getPlane() || now.distanceTo2D(gainPosition) > FAR_TILES);
    }

    /** Returns why the run is stuck, or null while it makes progress. */
    public String check(GameState state) {
        Gain gain = new Gain(state.getXp(), state.getItems());
        Activity activity = new Activity(state.getPosition(), gain, state.isBankOpen());
        int tick = state.getTick();
        // The game holds the player during a cutscene, however long it runs.
        if (lastActivity == null || state.isAnimating() || state.isInCutscene() || !activity.equals(lastActivity)) {
            lastActivity = activity;
            activityTick = tick;
        }
        if (lastGain == null || state.isInCutscene() || !gain.equals(lastGain) || movedFar(state.getPosition())) {
            lastGain = gain;
            gainTick = tick;
            gainPosition = state.getPosition();
        }
        if (tick - activityTick >= idleLimit) {
            return "no progress for " + idleLimit + " ticks (no change of tile, XP, inventory or bank, and no action)";
        }
        if (gainLimit > 0 && tick - gainTick >= gainLimit) {
            return "no XP, items or move of more than " + FAR_TILES + " tiles for " + gainLimit + " ticks";
        }
        return null;
    }

    @Value
    private static class Gain {
        Map<Skill, Integer> xp;
        Map<Integer, Integer> items;
    }

    @Value
    private static class Activity {
        WorldPoint position;
        Gain gain;
        boolean bankOpen;
    }
}
