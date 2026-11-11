package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

/**
 * Bounded distance-to-danger cache for one immutable grid snapshot. Unknown water and
 * hazards count as danger. Values count free tile layers outside the occupied hull;
 * they are a route preference, never permission to pass the hard collision checks.
 */
final class ShoreClearance {
    static final int PREFERRED_TILES = 3;
    private final SeaGrid grid;
    private final Hull hull;
    private final SeaPoint start, goal;
    private final int desired, arrivalRadius;
    private final int hullExtent, distanceCap;
    private final double taperDistance;
    private final Map<Long, byte[]> distances = new HashMap<>();

    ShoreClearance(SeaGrid grid, Hull hull, SeaPoint start, SeaPoint goal,
                   int desired, int arrivalRadius) {
        this.grid = grid; this.hull = hull; this.start = start; this.goal = goal;
        this.desired = desired; this.arrivalRadius = arrivalRadius;
        hullExtent = (int) Math.ceil(hull.reach);
        distanceCap = desired + hullExtent;
        taperDistance = Math.max(6, hull.length / 2.0 + desired * 2.0);
    }

    double penalty(SeaPoint point, int heading) {
        if (desired == 0) return 0;
        // A departing dock and a close final destination may legitimately have no buffer.
        // Only the extra soft preference tapers; the actual hull remains a hard obstacle.
        double taper = Math.min(1.0, Math.min(point.distance(start) / taperDistance,
                Math.max(0, point.distance(goal) - arrivalRadius) / taperDistance));
        double wanted = desired * taper;
        if (wanted == 0 || distance(point.x, point.y) >= wanted + hullExtent) return 0;
        double deficit = Math.max(0, wanted - hull.clearance(point, heading, desired, this::distance));
        return deficit * deficit * .65;
    }

    /** Same supercover as the hard corridor check, including both sides of corner crossings. */
    double maximumPenalty(SeaPoint from, SeaPoint to, int heading) {
        double maximum = penalty(from, heading);
        int nx = Math.abs(to.x - from.x), ny = Math.abs(to.y - from.y);
        int sx = Integer.signum(to.x - from.x), sy = Integer.signum(to.y - from.y);
        int x = from.x, y = from.y, ix = 0, iy = 0;
        while (ix < nx || iy < ny) {
            long lhs = (1L + 2L * ix) * ny, rhs = (1L + 2L * iy) * nx;
            if (lhs == rhs) {
                maximum = Math.max(maximum, penalty(new SeaPoint(x + sx, y), heading));
                maximum = Math.max(maximum, penalty(new SeaPoint(x, y + sy), heading));
                x += sx; y += sy; ix++; iy++;
            } else if (lhs < rhs) { x += sx; ix++; }
            else { y += sy; iy++; }
            maximum = Math.max(maximum, penalty(new SeaPoint(x, y), heading));
        }
        return maximum;
    }

    private int distance(int x, int y) {
        long key = SeaPoint.key(x >> 4, y >> 4);
        byte[] chunk = distances.get(key);
        if (chunk == null) {
            chunk = buildChunk((x >> 4) << 4, (y >> 4) << 4);
            distances.put(key, chunk);
        }
        return chunk[(x & 15) * 16 + (y & 15)];
    }

    /** Local Chebyshev distance transform, with enough surrounding data for every result. */
    private byte[] buildChunk(int baseX, int baseY) {
        int side = 16 + distanceCap * 2;
        int[] separation = new int[side * side];
        Arrays.fill(separation, distanceCap + 1);
        int[] queue = new int[separation.length];
        int head = 0, tail = 0;
        for (int x = 0; x < side; x++) for (int y = 0; y < side; y++) {
            if (!grid.open(baseX + x - distanceCap, baseY + y - distanceCap)) {
                int index = x * side + y;
                separation[index] = 0; queue[tail++] = index;
            }
        }
        while (head < tail) {
            int index = queue[head++], x = index / side, y = index % side;
            int next = separation[index] + 1;
            if (next > distanceCap) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0 || x + dx < 0 || y + dy < 0 || x + dx >= side || y + dy >= side) continue;
                int neighbour = (x + dx) * side + y + dy;
                if (separation[neighbour] > next) {
                    separation[neighbour] = next; queue[tail++] = neighbour;
                }
            }
        }
        byte[] chunk = new byte[256];
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            chunk[x * 16 + y] = (byte) Math.min(distanceCap,
                    separation[(x + distanceCap) * side + y + distanceCap] - 1);
        }
        return chunk;
    }
}
