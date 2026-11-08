package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Tiles the player can walk to within a few steps, in the same coordinates as the player's own position, inside
 * an instance as well as outside one. Steps follow the collision flags: a straight step needs its edge open, and
 * a diagonal step also needs both tiles it cuts past open.
 */
public final class ReachableTiles
{
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    private ReachableTiles()
    {
    }

    public static Collection<WorldPoint> around(WorldView view, WorldPoint start, int reach)
    {
        CollisionData[] maps = view == null || start == null ? null : view.getCollisionMaps();
        if (maps == null || start.getPlane() < 0 || start.getPlane() >= maps.length || maps[start.getPlane()] == null)
        {
            return List.of();
        }
        return search(maps[start.getPlane()].getFlags(), view.getBaseX(), view.getBaseY(), start, reach);
    }

    static Collection<WorldPoint> search(int[][] flags, int baseX, int baseY, WorldPoint start, int reach)
    {
        Map<WorldPoint, Integer> steps = new HashMap<>();
        if (!inScene(flags, start.getX() - baseX, start.getY() - baseY))
        {
            return List.of();
        }
        ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
        steps.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty())
        {
            WorldPoint point = queue.poll();
            int taken = steps.get(point);
            if (taken >= reach)
            {
                continue;
            }
            int x = point.getX() - baseX;
            int y = point.getY() - baseY;
            for (int[] step : STEPS)
            {
                WorldPoint next = point.dx(step[0]).dy(step[1]);
                if (!steps.containsKey(next) && canStep(flags, x, y, step[0], step[1]))
                {
                    steps.put(next, taken + 1);
                    queue.add(next);
                }
            }
        }
        return steps.keySet();
    }

    static boolean canStep(int[][] flags, int x, int y, int dx, int dy)
    {
        int toX = x + dx;
        int toY = y + dy;
        if (!inScene(flags, toX, toY) || (flags[toX][toY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
        {
            return false;
        }
        if (dx == 0 || dy == 0)
        {
            return (flags[x][y] & edge(dx, dy)) == 0;
        }
        return (flags[x][y] & (edge(dx, 0) | edge(0, dy))) == 0
            && (flags[toX][y] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | edge(0, dy))) == 0
            && (flags[x][toY] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | edge(dx, 0))) == 0;
    }

    private static int edge(int dx, int dy)
    {
        if (dx > 0)
        {
            return CollisionDataFlag.BLOCK_MOVEMENT_EAST;
        }
        if (dx < 0)
        {
            return CollisionDataFlag.BLOCK_MOVEMENT_WEST;
        }
        return dy > 0 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH : CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
    }

    private static boolean inScene(int[][] flags, int x, int y)
    {
        return x >= 0 && y >= 0 && x < flags.length && y < flags[x].length;
    }
}
