package net.runelite.client.plugins.microbot.drozulrah;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Reachable approach tiles, including paths that turn around shore obstacles. */
final class ZulrahBoatRoute
{
    static boolean shouldApproach(int distance, boolean clickable, boolean retry)
    {
        return distance > 4 && (!clickable || retry);
    }

    static List<int[]> approaches(int sx, int sy, int bx, int by, ZulrahSafeRoute.Edge edge)
    {
        List<int[]> result = new ArrayList<>();
        if (!inside(sx, sy) || !inside(bx, by)) return result;
        boolean[][] seen = new boolean[104][104];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seen[sx][sy] = true;
        queue.add(new int[]{sx, sy});
        while (!queue.isEmpty())
        {
            int[] tile = queue.remove();
            int x = tile[0], y = tile[1];
            int distance = Math.max(Math.abs(x - bx), Math.abs(y - by));
            if (distance >= 2 && distance <= 3) result.add(tile);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                {
                    int nx = x + dx, ny = y + dy;
                    if (!inside(nx, ny) || seen[nx][ny]
                            || Math.max(Math.abs(nx - sx), Math.abs(ny - sy)) > 32) continue;
                    if (!edge.allowed(x, y, nx, ny)) continue;
                    if (dx != 0 && dy != 0 && (!edge.allowed(x, y, nx, y)
                            || !edge.allowed(x, y, x, ny))) continue;
                    seen[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
        }
        return result;
    }

    private static boolean inside(int x, int y)
    { return x > 0 && y > 0 && x < 103 && y < 103; }
}
