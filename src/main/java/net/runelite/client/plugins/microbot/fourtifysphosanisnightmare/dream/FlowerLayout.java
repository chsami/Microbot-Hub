package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Her corpse flowers form a cross of four arms. The safe quarter is the one between the two arms of
 * safe (white) flowers; the other three hurt the player and heal her.
 */
public final class FlowerLayout
{
    private final int centreX;
    private final int centreY;
    private final int east;
    private final int north;

    private FlowerLayout(int centreX, int centreY, int east, int north)
    {
        this.centreX = centreX;
        this.centreY = centreY;
        this.east = east;
        this.north = north;
    }

    /** Reads the cross from its flowers, or none until both safe arms are seen. */
    public static FlowerLayout read(Collection<WorldPoint> safe, Collection<WorldPoint> unsafe)
    {
        Map<Integer, Integer> xs = new HashMap<>();
        Map<Integer, Integer> ys = new HashMap<>();
        for (Collection<WorldPoint> points : java.util.List.of(safe, unsafe))
        {
            for (WorldPoint point : points)
            {
                xs.merge(point.getX(), 1, Integer::sum);
                ys.merge(point.getY(), 1, Integer::sum);
            }
        }
        if (xs.isEmpty())
        {
            return null;
        }
        int centreX = mostCommon(xs);
        int centreY = mostCommon(ys);
        int east = 0;
        int north = 0;
        for (WorldPoint point : safe)
        {
            if (point.getY() == centreY && point.getX() != centreX)
            {
                east = Integer.signum(point.getX() - centreX);
            }
            else if (point.getX() == centreX && point.getY() != centreY)
            {
                north = Integer.signum(point.getY() - centreY);
            }
        }
        if (east == 0 || north == 0)
        {
            return null;
        }
        return new FlowerLayout(centreX, centreY, east, north);
    }

    private static int mostCommon(Map<Integer, Integer> counts)
    {
        int best = 0;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet())
        {
            if (entry.getValue() > bestCount)
            {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /** Inside the safe quarter, off the flower lines themselves. */
    public boolean isSafe(WorldPoint point)
    {
        return point != null && Integer.signum(point.getX() - centreX) == east
            && Integer.signum(point.getY() - centreY) == north;
    }
}
