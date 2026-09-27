package net.runelite.client.plugins.microbot.drozulrah;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahSafeRouteTest
{
    @Test public void openCrossingUsesTwoStraightTiles()
    {
        assertArrayEquals(new int[]{52, 50}, ZulrahSafeRoute.next(50, 50, 56, 50,
                (x, y, nx, ny) -> true));
    }

    @Test public void cannotCutBlockedDiagonalCorners()
    {
        assertNull(ZulrahSafeRoute.next(50, 50, 51, 51, (x, y, nx, ny) -> nx == ny));
    }

    @Test public void unreachableRouteStaysPut()
    {
        assertNull(ZulrahSafeRoute.next(50, 50, 56, 50, (x, y, nx, ny) -> false));
    }

    @Test public void crossingDetoursWithoutTraversingAnyCloudTile()
    {
        int x = 45, y = 50;
        for (int i = 0; i < 30 && (x != 60 || y != 50); i++)
        {
            int[] step = ZulrahSafeRoute.next(x, y, 60, 50,
                    (a, b, nx, ny) -> !clouded(nx, ny));
            assertNotNull(step);
            int dx = Integer.signum(step[0] - x), dy = Integer.signum(step[1] - y);
            while (x != step[0] || y != step[1])
            {
                x += dx;
                y += dy;
                assertFalse(clouded(x, y));
            }
        }
        assertEquals(60, x);
        assertEquals(50, y);
    }

    @Test public void changedHazardInvalidatesTheOldCrossing()
    {
        assertArrayEquals(new int[]{52, 50}, ZulrahSafeRoute.next(50, 50, 56, 50,
                (x, y, nx, ny) -> true));
        int[] step = ZulrahSafeRoute.next(50, 50, 56, 50,
                (x, y, nx, ny) -> nx != 51 || ny != 50);
        assertNotNull(step);
        assertFalse(step[0] == 52 && step[1] == 50);
    }

    @Test public void sceneBoundsAreRejected()
    {
        assertNull(ZulrahSafeRoute.next(-1, 50, 56, 50, (x, y, nx, ny) -> true));
        assertNull(ZulrahSafeRoute.next(50, 50, 104, 50, (x, y, nx, ny) -> true));
    }

    private static boolean clouded(int x, int y)
    { return x >= 50 && x <= 54 && y >= 48 && y <= 52; }
}
