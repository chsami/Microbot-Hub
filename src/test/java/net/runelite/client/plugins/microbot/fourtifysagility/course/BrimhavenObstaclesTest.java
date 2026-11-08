package net.runelite.client.plugins.microbot.fourtifysagility.course;

import java.lang.reflect.Proxy;
import java.util.List;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrimhavenObstaclesTest
{
    private static WorldPoint point(int x, int y)
    {
        return new WorldPoint(x, y, 3);
    }

    private static TileObject object(int id, int x, int y)
    {
        return (TileObject) Proxy.newProxyInstance(TileObject.class.getClassLoader(), new Class<?>[] {TileObject.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getId"))
                {
                    return id;
                }
                if (method.getName().equals("getWorldLocation"))
                {
                    return point(x, y);
                }
                return null;
            });
    }

    private static BrimhavenRoute.Step step(WorldPoint from, WorldPoint to)
    {
        return BrimhavenRoute.find(from, to, 99).get().get(0);
    }

    @Test
    public void selectsEndpointOnCorrectSideOfCrossing()
    {
        BrimhavenRoute.Step east = step(point(2761, 9546), point(2772, 9546));
        TileObject west = object(3559, 2764, 9546);
        TileObject farEnd = object(3561, 2769, 9546);
        TileObject unrelated = object(3559, 2764, 9590);
        assertSame(west, BrimhavenObstacles.select(east, point(2761, 9546), List.of(unrelated, farEnd, west)).get());
        BrimhavenRoute.Step westbound = step(point(2772, 9546), point(2761, 9546));
        assertSame(farEnd, BrimhavenObstacles.select(westbound, point(2772, 9546), List.of(west, farEnd)).get());
        assertFalse(BrimhavenObstacles.select(east, point(2761, 9546), List.of(farEnd, unrelated)).isPresent());
    }

    @Test
    public void onlySelectsAPlankWhenBothOtherLanesShowBrokenSections()
    {
        BrimhavenRoute.Step east = step(point(2761, 9557), point(2772, 9557));
        TileObject safe = object(3572, 2764, 9557);
        TileObject unsafe = object(3572, 2764, 9556);
        TileObject brokenNorth = object(3576, 2766, 9558);
        TileObject brokenSouth = object(3576, 2766, 9556);
        assertSame(safe, BrimhavenObstacles.select(east, point(2761, 9557),
            List.of(unsafe, safe, brokenNorth, brokenSouth)).get());
        assertFalse(BrimhavenObstacles.select(east, point(2761, 9557), List.of(safe, brokenNorth)).isPresent());
        assertFalse(BrimhavenObstacles.select(east, point(2761, 9557),
            List.of(unsafe, brokenNorth, brokenSouth)).isPresent());
    }

    @Test
    public void walkTargetsEndAtTheNearSideOfNextPlatform()
    {
        BrimhavenRoute.Step spikes = step(point(2772, 9546), point(2772, 9557));
        assertEquals(BrimhavenRoute.Obstacle.FLOOR_SPIKES, spikes.obstacle());
        assertEquals(point(2772, 9555), BrimhavenObstacles.walkTarget(spikes).get());
        BrimhavenRoute.Step rope = step(point(2805, 9579), point(2805, 9590));
        assertFalse(BrimhavenObstacles.walkTarget(rope).isPresent());
        assertTrue(BrimhavenObstacles.select(rope, point(2805, 9579), List.of()).isEmpty());
    }
}
