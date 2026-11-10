package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.Set;
import java.util.function.BiPredicate;

/** Never seize execution from the door/obstacle walker across a blocked approach. */
final class TransportApproach
{
    private TransportApproach() { }

    static boolean reachable(WorldArea object, Set<WorldPoint> reachable, WorldView world)
    {
        return reachable(object, reachable, (inside, outside) ->
            new WorldArea(inside, 1, 1).canTravelInDirection(world,
                outside.getX() - inside.getX(), outside.getY() - inside.getY()));
    }

    static boolean reachable(WorldArea object, Set<WorldPoint> reachable,
                             BiPredicate<WorldPoint, WorldPoint> openBoundary)
    {
        if (object == null || reachable.isEmpty()) return false;
        for (WorldPoint inside : object.toWorldPointList())
        {
            if (reachable.contains(inside)) return true;
            for (int[] delta : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            {
                WorldPoint outside = inside.dx(delta[0]).dy(delta[1]);
                if (!object.contains(outside) && reachable.contains(outside)
                    && openBoundary.test(inside, outside)) return true;
            }
        }
        return false;
    }
}
