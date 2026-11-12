package net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Post-BFS line-of-sight path smoothing.
 *
 * <p>The raw BFS path emits one tile per step. Long straight corridors become
 * dozens of adjacent tiles even though a single minimap click from the start
 * of the corridor to the end would traverse the whole stretch. This smoother
 * greedily collapses those runs: for each anchor tile, it advances as far
 * along the path as a line-of-sight walk allows, keeping only the farthest
 * reachable waypoint.
 *
 * <p>Correctness rests on these invariants:
 * <ul>
 *   <li>{@link CollisionMap#canStep} rejects any step across a wall that is
 *       encoded in the collision data, so those door/stair/ladder boundaries
 *       fail LOS and are preserved.</li>
 *   <li>Non-adjacent consecutive path tiles (teleports, boats, POH portals)
 *       fail the cheap adjacency check before we even attempt LOS, so those
 *       boundaries are also preserved.</li>
 *   <li>Tiles listed in {@code transportAnchors} (transport origins and
 *       destinations appearing in the raw path) are never skipped — some
 *       gates aren't encoded as collision walls so {@code canStep} would
 *       otherwise glide past them, hiding the transport from the walker.</li>
 * </ul>
 *
 * <p>Segments are capped at {@link #MAX_SEGMENT_CHEBYSHEV} tiles so that
 * {@code Rs2Walker.isNearPath()} (which tests tiles within ~{@code
 * recalculateDistance} of the player) continues to intersect the path no
 * matter where along a long corridor the player currently is.
 */
public final class PathSmoother {
    static final int MAX_SEGMENT_CHEBYSHEV = 10;

    private PathSmoother() {}

    public static List<WorldPoint> smooth(List<WorldPoint> path, CollisionMap map) {
        return smooth(path, map, Collections.emptySet());
    }

    public static List<WorldPoint> smooth(List<WorldPoint> path, CollisionMap map, Set<WorldPoint> transportAnchors) {
        return smooth(path, map, transportAnchors, Collections.emptySet());
    }

    public static List<WorldPoint> smooth(List<WorldPoint> path, CollisionMap map, Set<WorldPoint> transportAnchors,
                                          Set<Long> blockedTransportEdges) {
        if (path == null || path.size() < 3 || map == null) {
            return path;
        }
        if (transportAnchors == null) {
            transportAnchors = Collections.emptySet();
        }
        if (blockedTransportEdges == null) {
            blockedTransportEdges = Collections.emptySet();
        }

        final int n = path.size();
        List<WorldPoint> result = new ArrayList<>(n);
        result.add(path.get(0));

        int i = 0;
        while (i < n - 1) {
            int j = i + 1;
            while (j + 1 < n
                    && !transportAnchors.contains(path.get(j))
                    && isChebyshevAdjacentSamePlane(path.get(j), path.get(j + 1))
                    && chebyshev(path.get(i), path.get(j + 1)) <= MAX_SEGMENT_CHEBYSHEV
                    && lineOfSight(path.get(i), path.get(j + 1), map, blockedTransportEdges)) {
                j++;
            }
            result.add(path.get(j));
            i = j;
        }

        return result;
    }

    private static boolean isChebyshevAdjacentSamePlane(WorldPoint a, WorldPoint b) {
        if (a.getPlane() != b.getPlane()) return false;
        return Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1;
    }

    private static int chebyshev(WorldPoint a, WorldPoint b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    private static boolean lineOfSight(WorldPoint from, WorldPoint to, CollisionMap map, Set<Long> blockedTransportEdges) {
        if (from.getPlane() != to.getPlane()) return false;
        final int z = from.getPlane();
        int x = from.getX();
        int y = from.getY();
        final int tx = to.getX();
        final int ty = to.getY();
        while (x != tx || y != ty) {
            int dx = Integer.signum(tx - x);
            int dy = Integer.signum(ty - y);
            int fromPacked = WorldPointUtil.packWorldPoint(x, y, z);
            int toPacked = WorldPointUtil.packWorldPoint(x + dx, y + dy, z);
            if (PathfinderConfig.isBlockedTransportStep(fromPacked, toPacked, blockedTransportEdges)) {
                return false;
            }
            if (!map.canStep(x, y, z, dx, dy)) {
                return false;
            }
            x += dx;
            y += dy;
        }
        return true;
    }
}
