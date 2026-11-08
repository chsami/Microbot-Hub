package net.runelite.client.plugins.microbot.fourtifysagility.course;

import java.util.List;
import java.util.Optional;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrimhavenRouteTest
{
    private static WorldPoint centre(int x, int y)
    {
        return new WorldPoint(2761 + 11 * x, 9546 + 11 * y, 3);
    }

    @Test
    public void recognisesArenaAndRejectsOtherPlanesAndOutsideTiles()
    {
        assertEquals(centre(4, 4), BrimhavenRoute.platformAt(new WorldPoint(2804, 9589, 3)));
        assertEquals(centre(0, 0), BrimhavenRoute.platformAt(new WorldPoint(2759, 9544, 3)));
        assertNull(BrimhavenRoute.platformAt(new WorldPoint(2758, 9544, 3)));
        assertNull(BrimhavenRoute.platformAt(new WorldPoint(2805, 9590, 0)));
        assertNull(BrimhavenRoute.platformAt(null));
    }

    @Test
    public void eachPairOfPlatformsHasAConnectedRouteAtLevelNinetyNine()
    {
        for (int from = 0; from < 25; from++)
        {
            for (int to = 0; to < 25; to++)
            {
                WorldPoint start = centre(from % 5, from / 5);
                WorldPoint target = centre(to % 5, to / 5);
                Optional<List<BrimhavenRoute.Step>> route = BrimhavenRoute.find(start, target, 99);
                assertTrue(route.isPresent());
                WorldPoint at = start;
                for (BrimhavenRoute.Step step : route.get())
                {
                    assertEquals(at, step.from());
                    int distance = Math.abs(step.to().getX() - at.getX()) + Math.abs(step.to().getY() - at.getY());
                    assertEquals(11, distance);
                    assertNotNull(step.obstacle());
                    at = step.to();
                }
                assertEquals(target, at);
            }
        }
    }

    @Test
    public void routesRespectCurrentAgilityAndAvoidUnavailableObstacles()
    {
        for (int level : new int[] {1, 20, 40})
        {
            for (int from = 0; from < 25; from++)
            {
                for (int to = 0; to < 25; to++)
                {
                    Optional<List<BrimhavenRoute.Step>> route = BrimhavenRoute.find(
                        centre(from % 5, from / 5), centre(to % 5, to / 5), level);
                    if (route.isPresent())
                    {
                        for (BrimhavenRoute.Step step : route.get())
                        {
                            assertTrue(step.obstacle().minimumLevel() <= level);
                        }
                    }
                }
            }
        }
        assertFalse(BrimhavenRoute.find(centre(0, 0), centre(4, 4), 0).isPresent());
        assertFalse(BrimhavenRoute.find(centre(0, 0), new WorldPoint(1, 1, 3), 99).isPresent());
        assertTrue(BrimhavenRoute.find(centre(2, 3), centre(2, 3), 1).get().isEmpty());
        List<BrimhavenRoute.Step> aroundBlades = BrimhavenRoute.find(centre(1, 1), centre(2, 1), 20).get();
        assertTrue(aroundBlades.size() > 1);
        for (BrimhavenRoute.Step step : aroundBlades)
        {
            assertTrue(step.obstacle() != BrimhavenRoute.Obstacle.SPINNING_BLADES);
        }
    }

    @Test
    public void adjacentCachePlacementsMapToExpectedObstaclesInBothDirections()
    {
        assertEquals(BrimhavenRoute.Obstacle.LEDGE,
            BrimhavenRoute.find(centre(0, 0), centre(1, 0), 99).get().get(0).obstacle());
        assertEquals(BrimhavenRoute.Obstacle.PILLAR,
            BrimhavenRoute.find(centre(0, 1), centre(0, 0), 99).get().get(0).obstacle());
        assertEquals(BrimhavenRoute.Obstacle.ROPE_SWING,
            BrimhavenRoute.find(centre(4, 3), centre(4, 4), 99).get().get(0).obstacle());
    }
}
