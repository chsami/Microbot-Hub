package net.runelite.client.plugins.microbot.fourtifysagility.util.navigation;

import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CourseMovementTest
{
    private static final WorldPoint BLOCK_TILE = new WorldPoint(3375, 2849, 1);
    private static final WorldPoint LADDER_EXIT = new WorldPoint(3005, 3964, 0);
    private static final WorldPoint LOG_START = new WorldPoint(3002, 3946, 0);
    private static final WorldPoint SEERS_LAP_END = new WorldPoint(2704, 3464, 0);
    private static final WorldPoint SEERS_START = CourseMovement.COURSE_STARTS.get(AgilityCourse.SEERS_VILLAGE);

    @Test
    public void courseStartsAreInRangeOfTheFirstObstacleWhileTheLapEndIsNot()
    {
        // Each course: the first obstacle's tile and the tile the last obstacle lands on.
        Object[][] courses = {
            {AgilityCourse.SEERS_VILLAGE, new WorldPoint(2729, 3489, 0), SEERS_LAP_END},
            {AgilityCourse.POLLNIVNEACH, new WorldPoint(3351, 2962, 0), new WorldPoint(3363, 2998, 0)},
            {AgilityCourse.RELLEKKA, new WorldPoint(2625, 3677, 0), new WorldPoint(2653, 3676, 0)},
            {AgilityCourse.AL_KHARID, new WorldPoint(3273, 3195, 0), new WorldPoint(3299, 3194, 0)},
        };
        assertEquals(courses.length, CourseMovement.COURSE_STARTS.size());
        for (Object[] course : courses)
        {
            WorldPoint start = CourseMovement.COURSE_STARTS.get(course[0]);
            WorldPoint obstacle = (WorldPoint) course[1];
            WorldPoint lapEnd = (WorldPoint) course[2];
            assertTrue(start.distanceTo(obstacle) <= 1, course[0].toString());
            assertTrue(lapEnd.distanceTo(obstacle) > TargetRange.GAME_OBJECT_SEARCH_RADIUS, course[0].toString());
        }
    }

    @Test
    public void walksFromTheLapEndToTheCourseStartBeyondTheObjectRange() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = SEERS_LAP_END;
        assertTrue(fixture.movement.walkToStart(SEERS_START));
        assertEquals(1, fixture.queued());
    }

    @Test
    public void rejectsUnlistedStartsOtherPlanesAndNoTarget() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = SEERS_LAP_END;
        assertFalse(fixture.movement.walkToStart(new WorldPoint(2729, 3488, 0)));
        assertFalse(fixture.movement.walkToStart(null));
        fixture.position = new WorldPoint(2704, 3464, 1);
        assertFalse(fixture.movement.walkToStart(SEERS_START));
        assertEquals(0, fixture.queued());
    }

    @Test
    public void stopsAfterTheWalkerFindsNoPathUntilThePlayerMoves() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = SEERS_LAP_END;
        assertTrue(fixture.movement.walkToStart(SEERS_START));
        fixture.movement.stepFinished(SEERS_START, SEERS_LAP_END, WalkerState.MOVING);
        assertTrue(fixture.movement.walkToStart(SEERS_START));
        fixture.movement.stepFinished(SEERS_START, SEERS_LAP_END, WalkerState.UNREACHABLE);
        assertFalse(fixture.movement.walkToStart(SEERS_START));
        fixture.position = new WorldPoint(2705, 3465, 0);
        assertTrue(fixture.movement.walkToStart(SEERS_START));
    }

    @Test
    public void cancelDropsAQueuedStep() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = SEERS_LAP_END;
        assertTrue(fixture.movement.walkToStart(SEERS_START));
        fixture.movement.cancelStep();
        fixture.actions.dispatch();
        assertNull(fixture.pending());
    }

    @Test
    public void walksToAWaitingTileWithinRangeOnThePlayersPlane() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.movement.walk(BLOCK_TILE));
        fixture.position = new WorldPoint(3355, 2849, 1);
        assertTrue(fixture.movement.walk(BLOCK_TILE));
    }

    @Test
    public void rejectsOtherTilesPlanesDistantOrReachedTiles() throws Exception
    {
        Fixture fixture = new Fixture();
        assertFalse(fixture.movement.walk(new WorldPoint(3374, 2849, 1)));
        assertFalse(fixture.movement.walk(new WorldPoint(3365, 2848, 3)));
        assertFalse(fixture.movement.walk(null));
        fixture.position = new WorldPoint(3354, 2849, 1);
        assertFalse(fixture.movement.walk(BLOCK_TILE));
        fixture.position = BLOCK_TILE;
        assertFalse(fixture.movement.walk(BLOCK_TILE));
    }

    @Test
    public void waitsWhileThePlayerIsMoving() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.pose = 824;
        assertFalse(fixture.movement.walk(BLOCK_TILE));
    }

    @Test
    public void walksBackToTheWildernessLogAfterAFall() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = LADDER_EXIT;
        assertTrue(fixture.movement.walk(LOG_START));
        assertTrue(fixture.movement.walk(new WorldPoint(3005, 3953, 0)));
        assertTrue(fixture.movement.walk(new WorldPoint(3002, 3960, 0)));
    }

    @Test
    public void rejectsUnlistedWalkBackTilesAndTilesOutOfRange() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = LADDER_EXIT;
        assertFalse(fixture.movement.walk(new WorldPoint(3002, 3947, 0)));
        assertFalse(fixture.movement.walk(new WorldPoint(3002, 3946, 1)));
        fixture.position = new WorldPoint(3005, 3967, 0);
        assertFalse(fixture.movement.walk(LOG_START));
        fixture.position = LOG_START;
        assertFalse(fixture.movement.walk(LOG_START));
    }

    @Test
    public void reportsTheWalkWithoutASecondClickWhileMoving() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = LADDER_EXIT;
        assertFalse(fixture.movement.isWalkingTo(LOG_START));
        fixture.pose = 824;
        assertFalse(fixture.movement.walk(LOG_START));
        assertTrue(fixture.movement.isWalkingTo(LOG_START));
        fixture.position = LOG_START;
        assertFalse(fixture.movement.isWalkingTo(LOG_START));
    }

    @Test
    public void holdsTheClickWhileTheWalkIsStoppedThenRetries() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.position = LADDER_EXIT;
        assertTrue(fixture.movement.walk(LOG_START));
        assertEquals(1, fixture.queued());
        for (int tick = 1; tick < CourseMovement.RETRY_TICKS; tick++)
        {
            fixture.tick = tick;
            assertTrue(fixture.movement.walk(LOG_START));
        }
        assertEquals(1, fixture.queued());
        fixture.tick = CourseMovement.RETRY_TICKS;
        assertTrue(fixture.movement.walk(LOG_START));
        assertEquals(2, fixture.queued());
        fixture.position = new WorldPoint(3004, 3961, 0);
        assertTrue(fixture.movement.walk(LOG_START));
        assertEquals(3, fixture.queued());
    }

    @Test
    public void logoutOrStopRejectsAQueuedWalk() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.movement.walk(BLOCK_TILE));
        fixture.state = GameState.LOGIN_SCREEN;
        fixture.actions.dispatch();
        fixture.state = GameState.LOGGED_IN;
        assertTrue(fixture.movement.walk(BLOCK_TILE));
        fixture.actions.reset();
        fixture.actions.dispatch();
        fixture.position = LADDER_EXIT;
        assertTrue(fixture.movement.walk(LOG_START));
        fixture.state = GameState.LOGIN_SCREEN;
        fixture.actions.dispatch();
    }

    private static final class Fixture
    {
        private GameState state = GameState.LOGGED_IN;
        private WorldPoint position = new WorldPoint(3368, 2851, 1);
        private int pose = 808;
        private int tick;
        private final AgilityActions actions = new AgilityActions();
        private final CourseMovement movement;

        private Fixture() throws Exception
        {
            net.runelite.api.WorldView view = (net.runelite.api.WorldView) Proxy.newProxyInstance(
                net.runelite.api.WorldView.class.getClassLoader(), new Class<?>[] {net.runelite.api.WorldView.class},
                (object, method, args) -> null);
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (object, method, args) -> {
                    switch (method.getName())
                    {
                        case "getWorldLocation":
                            return position;
                        case "getWorldView":
                            return view;
                        case "getPoseAnimation":
                            return pose;
                        case "getIdlePoseAnimation":
                            return 808;
                        default:
                            return null;
                    }
                });
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                new Class<?>[] {Client.class}, (object, method, args) -> {
                    switch (method.getName())
                    {
                        case "getGameState":
                            return state;
                        case "getLocalPlayer":
                            return player;
                        case "getTopLevelWorldView":
                            return view;
                        case "getTickCount":
                            return tick;
                        default:
                            throw new AssertionError("Stale course walk reached " + method.getName());
                    }
                });
            Field field = AgilityActions.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(actions, client);
            movement = new CourseMovement(client, actions);
        }

        private Object pending() throws Exception
        {
            Field field = AgilityActions.class.getDeclaredField("pending");
            field.setAccessible(true);
            return field.get(actions);
        }

        private int queued() throws Exception
        {
            Field field = AgilityActions.class.getDeclaredField("queue");
            field.setAccessible(true);
            Object queue = field.get(actions);
            Field requests = queue.getClass().getDeclaredField("queue");
            requests.setAccessible(true);
            return ((java.util.Collection<?>) requests.get(queue)).size();
        }
    }
}
