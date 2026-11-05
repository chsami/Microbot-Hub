package net.runelite.client.plugins.microbot.fourtifysagility.util.navigation;

import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

/**
 * Limits movement to the course tiles where a lap waits or walks back to an obstacle. A waiting tile
 * gets a minimap click; a walk-back tile gets a scene click, or a minimap step toward it, at the
 * player's current run setting. A course start beyond the object range gets one walker step a tick.
 */
public final class CourseMovement
{
    /** The two Agility Pyramid tiles where the lap waits for a moving block. */
    public static final Set<WorldPoint> WAITING_TILES = Set.of(
        new WorldPoint(3375, 2849, 1),
        new WorldPoint(3365, 2848, 3));

    /** The Agility Pyramid start, and the Wilderness tiles a lap walks back to after a fall or to wait. */
    public static final Set<WorldPoint> WALK_BACK_TILES = Set.of(
        new WorldPoint(3364, 2830, 0),
        new WorldPoint(3001, 3943, 0),
        new WorldPoint(3005, 3953, 0),
        new WorldPoint(3002, 3960, 0),
        new WorldPoint(3002, 3946, 0));

    /**
     * The tile beside the first obstacle of each course whose lap ends beyond the 20-tile object range
     * of that obstacle, so the lap walks back before it starts again.
     */
    public static final Map<AgilityCourse, WorldPoint> COURSE_STARTS = Map.of(
        AgilityCourse.SEERS_VILLAGE, new WorldPoint(2729, 3489, 0),
        AgilityCourse.POLLNIVNEACH, new WorldPoint(3351, 2961, 0),
        AgilityCourse.RELLEKKA, new WorldPoint(2625, 3677, 0),
        AgilityCourse.AL_KHARID, new WorldPoint(3273, 3195, 0));

    /** Ticks before a click to the same tile is sent again while the player has not moved, for example when frozen. */
    static final int RETRY_TICKS = 5;

    private final Client client;
    private final AgilityActions actions;
    private WorldPoint clickedTarget;
    private WorldPoint clickedFrom;
    private int clickedTick;
    private final AtomicInteger stepGeneration = new AtomicInteger();
    private volatile WorldPoint stepTarget;
    private volatile WorldPoint failedTarget;
    private volatile WorldPoint failedFrom;

    public CourseMovement(Client client, AgilityActions actions)
    {
        this.client = client;
        this.actions = actions;
    }

    /** Sends a click toward an allowed tile, or holds a recent one; true while the walk is under way. */
    public boolean walk(WorldPoint target)
    {
        if (!valid(target, true))
        {
            return false;
        }
        WorldPoint position = client.getLocalPlayer().getWorldLocation();
        int tick = client.getTickCount();
        if (target.equals(clickedTarget) && position.equals(clickedFrom) && tick - clickedTick < RETRY_TICKS)
        {
            return true;
        }
        clickedTarget = target;
        clickedFrom = position;
        clickedTick = tick;
        if (WAITING_TILES.contains(target))
        {
            actions.enqueueBlocking(() -> valid(target, true), () -> Rs2Walker.walkMiniMap(target));
        }
        else
        {
            actions.enqueueBlocking(() -> valid(target, true),
                () -> Rs2Walker.walkFastCanvas(target, Rs2Player.isRunEnabled()));
        }
        return true;
    }

    /**
     * Queues one walker step toward a course start; false when the start is on another plane or the
     * walker found no path from where the player still stands.
     */
    public boolean walkToStart(WorldPoint target)
    {
        if (target == null || !COURSE_STARTS.containsValue(target) || !onPlaneOf(target))
        {
            return false;
        }
        WorldPoint position = client.getLocalPlayer().getWorldLocation();
        if (target.equals(failedTarget) && position.equals(failedFrom))
        {
            return false;
        }
        failedTarget = null;
        failedFrom = null;
        stepTarget = target;
        int version = stepGeneration.get();
        actions.enqueueBlocking(() -> version == stepGeneration.get() && onPlaneOf(target),
            () -> stepFinished(target, position, Rs2Walker.walkStep(target, 1)));
        return true;
    }

    /** Records a walker step; a route the walker cannot find is not requested again from the same tile. */
    void stepFinished(WorldPoint target, WorldPoint from, WalkerState state)
    {
        if (state == WalkerState.UNREACHABLE)
        {
            failedTarget = target;
            failedFrom = from;
        }
    }

    /** Drops queued steps and clears the walker goal this helper set. */
    public void cancelStep()
    {
        stepGeneration.incrementAndGet();
        WorldPoint target = stepTarget;
        stepTarget = null;
        if (target != null && target.equals(Rs2Walker.getCurrentTarget()))
        {
            Rs2Walker.setTarget(null, "Fourtifys Agility stopped walking to the course start");
        }
    }

    /** The player is moving and still short of an allowed tile in range, so no second click is sent. */
    public boolean isWalkingTo(WorldPoint target)
    {
        return valid(target, false);
    }

    private boolean onPlaneOf(WorldPoint target)
    {
        Player player = client.getLocalPlayer();
        return client.getGameState() == GameState.LOGGED_IN && player != null && target != null
            && client.getTopLevelWorldView() != null && player.getWorldView() == client.getTopLevelWorldView()
            && player.getWorldLocation() != null && target.getPlane() == player.getWorldLocation().getPlane();
    }

    private boolean valid(WorldPoint target, boolean idle)
    {
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN || player == null || target == null
            || client.getTopLevelWorldView() == null || player.getWorldView() != client.getTopLevelWorldView())
        {
            return false;
        }
        WorldPoint position = player.getWorldLocation();
        return (WAITING_TILES.contains(target) || WALK_BACK_TILES.contains(target))
            && position != null && !position.equals(target)
            && target.getPlane() == position.getPlane()
            && target.distanceTo(position) <= TargetRange.GAME_OBJECT_SEARCH_RADIUS
            && (player.getPoseAnimation() == player.getIdlePoseAnimation()) == idle;
    }
}
