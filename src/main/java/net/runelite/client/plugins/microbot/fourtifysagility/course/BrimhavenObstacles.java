package net.runelite.client.plugins.microbot.fourtifysagility.course;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

/** Chooses an obstacle belonging to one directed arena crossing. */
public final class BrimhavenObstacles
{
    private static final int BROKEN_PLANK = 3576;

    private BrimhavenObstacles()
    {
    }

    /** A non-clickable crossing is walked to the near edge of the next platform. */
    public static Optional<WorldPoint> walkTarget(BrimhavenRoute.Step step)
    {
        switch (step.obstacle())
        {
            case BLADE:
            case DARTS:
            case FLOOR_SPIKES:
            case PRESSURE_PAD:
            case SPINNING_BLADES:
                int dx = Integer.signum(step.to().getX() - step.from().getX());
                int dy = Integer.signum(step.to().getY() - step.from().getY());
                return Optional.of(new WorldPoint(step.to().getX() - 2 * dx, step.to().getY() - 2 * dy, BrimhavenRoute.PLANE));
            default:
                return Optional.empty();
        }
    }

    /** Selects a clickable live-scene object on the starting half of this crossing. */
    public static Optional<TileObject> select(BrimhavenRoute.Step step, WorldPoint player, List<TileObject> objects)
    {
        if (step == null || player == null || objects == null || player.getPlane() != BrimhavenRoute.PLANE)
        {
            return Optional.empty();
        }
        TileObject best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (TileObject object : objects)
        {
            if (object == null || !clickable(step.obstacle(), object.getId()))
            {
                continue;
            }
            WorldPoint point = object.getWorldLocation();
            if (point == null || point.getPlane() != BrimhavenRoute.PLANE)
            {
                continue;
            }
            int along = along(step, point);
            if (along < 2 || along > 6 || across(step, point) > 2)
            {
                continue;
            }
            if (step.obstacle() == BrimhavenRoute.Obstacle.PLANK && !isSafePlank(step, point, objects))
            {
                continue;
            }
            int distance = player.distanceTo(point);
            if (distance < bestDistance)
            {
                best = object;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int along(BrimhavenRoute.Step step, WorldPoint point)
    {
        int dx = Integer.signum(step.to().getX() - step.from().getX());
        int dy = Integer.signum(step.to().getY() - step.from().getY());
        return (point.getX() - step.from().getX()) * dx + (point.getY() - step.from().getY()) * dy;
    }

    private static int across(BrimhavenRoute.Step step, WorldPoint point)
    {
        return step.to().getX() == step.from().getX()
            ? Math.abs(point.getX() - step.from().getX())
            : Math.abs(point.getY() - step.from().getY());
    }

    private static boolean isSafePlank(BrimhavenRoute.Step step, WorldPoint plank, List<TileObject> objects)
    {
        boolean horizontal = step.to().getX() != step.from().getX();
        int lane = horizontal ? plank.getY() : plank.getX();
        int centre = horizontal ? step.from().getY() : step.from().getX();
        Set<Integer> brokenLanes = new HashSet<>();
        for (TileObject object : objects)
        {
            if (object == null || object.getId() != BROKEN_PLANK)
            {
                continue;
            }
            WorldPoint broken = object.getWorldLocation();
            if (broken != null && broken.getPlane() == BrimhavenRoute.PLANE
                && along(step, broken) >= 2 && along(step, broken) <= 9)
            {
                int brokenLane = horizontal ? broken.getY() : broken.getX();
                if (Math.abs(brokenLane - centre) <= 1)
                {
                    brokenLanes.add(brokenLane);
                }
            }
        }
        return Math.abs(lane - centre) <= 1 && brokenLanes.size() == 2 && !brokenLanes.contains(lane);
    }

    private static boolean clickable(BrimhavenRoute.Obstacle obstacle, int id)
    {
        switch (obstacle)
        {
            case BALANCING_ROPE:
                return id == 3551;
            case LOG_BALANCE:
                return id == 3553 || id == 3555 || id == 3557;
            case LEDGE:
                return id == 3559 || id == 3561;
            case MONKEY_BARS:
                return id == 3564;
            case LOW_WALL:
                return id == 3565;
            case ROPE_SWING:
                return id == 3566;
            case PLANK:
                return id >= 3570 && id <= 3572;
            case PILLAR:
                return id == 3578;
            case HAND_HOLDS:
                return id == 3583;
            default:
                return false;
        }
    }
}
