package net.runelite.client.plugins.microbot.drozulrah;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Bounded scene routing; never delegates a long hazard-sensitive leg to game pathfinding. */
final class ZulrahSafeRoute
{
    interface Edge { boolean allowed(int x, int y, int nx, int ny); }
    private static final int SIZE = 104;

    static int[] next(int sx, int sy, int tx, int ty, Edge edge)
    {
        if (!inside(sx, sy) || !inside(tx, ty)) return null;
        int start = sx * SIZE + sy;
        int goal = tx * SIZE + ty;
        int[] parent = new int[SIZE * SIZE];
        Arrays.fill(parent, -1);
        parent[start] = start;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        int best = start;
        int bestDistance = distance(sx, sy, tx, ty);
        while (!queue.isEmpty())
        {
            int at = queue.remove();
            if (at == goal) { best = at; break; }
            int x = at / SIZE, y = at % SIZE;
            int[][] directions = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
            Arrays.sort(directions, java.util.Comparator.comparingInt(d ->
                    (x + d[0] - tx) * (x + d[0] - tx) + (y + d[1] - ty) * (y + d[1] - ty)));
            for (int[] direction : directions)
                {
                    int dx = direction[0], dy = direction[1];
                    int nx = x + dx, ny = y + dy;
                    if ((dx == 0 && dy == 0) || !inside(nx, ny)
                            || distance(sx, sy, nx, ny) > 32) continue;
                    int id = nx * SIZE + ny;
                    if (parent[id] != -1 || !edge.allowed(x, y, nx, ny)) continue;
                    if (dx != 0 && dy != 0 && (!edge.allowed(x, y, nx, y)
                            || !edge.allowed(x, y, x, ny))) continue;
                    parent[id] = at;
                    queue.add(id);
                    int d = distance(nx, ny, tx, ty);
                    if (d < bestDistance) { bestDistance = d; best = id; }
                }
        }
        if (best == start) return null;
        int second = best;
        while (parent[best] != start) { second = best; best = parent[best]; }
        int x = best / SIZE, y = best % SIZE;
        // Two tiles only when both steps are in the same direction. Otherwise stop at the bend.
        if (second != best && second / SIZE - x == x - sx && second % SIZE - y == y - sy)
            return new int[]{second / SIZE, second % SIZE};
        return new int[]{x, y};
    }

    private static int distance(int x, int y, int tx, int ty)
    { return Math.max(Math.abs(x - tx), Math.abs(y - ty)); }
    private static boolean inside(int x, int y)
    { return x > 0 && y > 0 && x < SIZE - 1 && y < SIZE - 1; }
}
