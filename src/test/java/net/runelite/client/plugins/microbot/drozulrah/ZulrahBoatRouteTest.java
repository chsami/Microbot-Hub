package net.runelite.client.plugins.microbot.drozulrah;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahBoatRouteTest
{
    @Test public void visibleBoatCanBeClickedFromTeleportSpot()
    {
        assertFalse(ZulrahBoatRoute.shouldApproach(15, true, false));
    }

    @Test public void obscuredBoatOrFailedBoardingUsesApproach()
    {
        assertTrue(ZulrahBoatRoute.shouldApproach(15, false, false));
        assertTrue(ZulrahBoatRoute.shouldApproach(15, true, true));
        assertFalse(ZulrahBoatRoute.shouldApproach(4, true, true));
    }

    @Test public void findsApproachesWhenEveryStraightLineIsBlocked()
    {
        // A wall lies between the teleport tile and every boat approach, with a gap to the north.
        ZulrahSafeRoute.Edge edge = (x, y, nx, ny) -> nx >= 40 && nx <= 65 && ny >= 40 && ny <= 60
                && (nx != 50 || ny == 42);
        List<int[]> choices = ZulrahBoatRoute.approaches(45, 50, 58, 50, edge);
        assertFalse(choices.isEmpty());
        int[] chosen = choices.get(0);
        int x = 45, y = 50;
        for (int i = 0; i < 100 && (x != chosen[0] || y != chosen[1]); i++)
        {
            int[] next = ZulrahSafeRoute.next(x, y, chosen[0], chosen[1], edge);
            assertNotNull(next);
            int dx = Integer.signum(next[0] - x), dy = Integer.signum(next[1] - y);
            while (x != next[0] || y != next[1])
            {
                assertTrue(edge.allowed(x, y, x + dx, y + dy));
                x += dx; y += dy;
            }
        }
        assertEquals(chosen[0], x);
        assertEquals(chosen[1], y);
    }

    @Test public void excludesDisconnectedLandAcrossWater()
    {
        List<int[]> choices = ZulrahBoatRoute.approaches(45, 50, 52, 50,
                (x, y, nx, ny) -> nx != 51);
        assertFalse(choices.isEmpty());
        for (int[] tile : choices) assertTrue(tile[0] < 51);
    }

    @Test public void approachesStayWithinBoardingDistance()
    {
        List<int[]> choices = ZulrahBoatRoute.approaches(45, 50, 58, 50, (x, y, nx, ny) -> true);
        assertTrue(choices.size() > 1);
        for (int[] tile : choices)
        {
            int distance = Math.max(Math.abs(tile[0] - 58), Math.abs(tile[1] - 50));
            assertTrue(distance >= 2 && distance <= 3);
        }
    }

    @Test public void unavailableRouteReturnsNoRandomCandidate()
    {
        assertTrue(ZulrahBoatRoute.approaches(45, 50, 58, 50, (x, y, nx, ny) -> false).isEmpty());
        assertTrue(ZulrahBoatRoute.approaches(-1, 50, 58, 50, (x, y, nx, ny) -> true).isEmpty());
    }
}
