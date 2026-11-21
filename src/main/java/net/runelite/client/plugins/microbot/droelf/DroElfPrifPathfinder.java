package net.runelite.client.plugins.microbot.droelf;

import net.runelite.api.coords.WorldPoint;

import java.util.*;

/**
 * Prif-only A* over the embedded Efficient Walker collision graph.
 */
final class DroElfPrifPathfinder {
    private static final int MAX_SEARCH_NODES = 45_000;
    private static final int[][] DIRECTIONS = {
            { 1, 0}, {-1, 0}, {0,  1}, {0, -1},
            { 1, 1}, { 1,-1}, {-1, 1}, {-1,-1}
    };

    List<WorldPoint> findPath(WorldPoint start, WorldPoint requestedGoal, Set<Long> blockedEdges) {
        if (!DroElfPrifCollisionMap.supports(start) || !DroElfPrifCollisionMap.supports(requestedGoal)) {
            return Collections.emptyList();
        }
        if (start.getPlane() != requestedGoal.getPlane()) return Collections.emptyList();

        final WorldPoint goal = normalizeGoal(requestedGoal);
        if (goal == null) return Collections.emptyList();
        if (start.equals(goal)) return Collections.singletonList(start);

        final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
        final Map<Integer, Integer> gScore = new HashMap<>();
        final Map<Integer, Integer> cameFrom = new HashMap<>();
        final Map<Integer, WorldPoint> points = new HashMap<>();

        final int startKey = pack(start);
        final int goalKey = pack(goal);
        gScore.put(startKey, 0);
        points.put(startKey, start);
        open.add(new Node(start, 0, heuristic(start, goal)));

        int searched = 0;
        while (!open.isEmpty() && searched++ < MAX_SEARCH_NODES) {
            final Node current = open.poll();
            final int currentKey = pack(current.point);
            final int knownG = gScore.getOrDefault(currentKey, Integer.MAX_VALUE);
            if (current.g != knownG) continue;

            if (currentKey == goalKey) {
                return reconstruct(startKey, goalKey, cameFrom, points);
            }

            for (int[] direction : DIRECTIONS) {
                final WorldPoint next = new WorldPoint(
                        current.point.getX() + direction[0],
                        current.point.getY() + direction[1],
                        current.point.getPlane());

                if (!DroElfPrifCollisionMap.supports(next)) continue;
                if (!DroElfPrifCollisionMap.canStep(current.point, next)) continue;
                if (blockedEdges != null && blockedEdges.contains(edgeKey(current.point, next))) continue;

                final int moveCost = direction[0] != 0 && direction[1] != 0 ? 14 : 10;
                final int tentative = current.g + moveCost;
                final int nextKey = pack(next);

                if (tentative >= gScore.getOrDefault(nextKey, Integer.MAX_VALUE)) continue;

                cameFrom.put(nextKey, currentKey);
                points.put(nextKey, next);
                gScore.put(nextKey, tentative);
                open.add(new Node(next, tentative, tentative + heuristic(next, goal)));
            }
        }

        return Collections.emptyList();
    }

    private WorldPoint normalizeGoal(WorldPoint requested) {
        if (DroElfPrifCollisionMap.hasAnyExit(requested)) return requested;

        WorldPoint best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    final WorldPoint candidate = new WorldPoint(
                            requested.getX() + dx,
                            requested.getY() + dy,
                            requested.getPlane());
                    if (!DroElfPrifCollisionMap.supports(candidate)
                            || !DroElfPrifCollisionMap.hasAnyExit(candidate)) continue;

                    final int distance = candidate.distanceTo(requested);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private List<WorldPoint> reconstruct(
            int startKey,
            int goalKey,
            Map<Integer, Integer> cameFrom,
            Map<Integer, WorldPoint> points) {
        final LinkedList<WorldPoint> path = new LinkedList<>();
        int key = goalKey;
        final WorldPoint goal = points.get(goalKey);
        if (goal == null) return Collections.emptyList();
        path.addFirst(goal);

        int guard = 0;
        while (key != startKey && guard++ < MAX_SEARCH_NODES) {
            final Integer previous = cameFrom.get(key);
            if (previous == null) return Collections.emptyList();
            key = previous;
            final WorldPoint point = points.get(key);
            if (point == null) return Collections.emptyList();
            path.addFirst(point);
        }
        return path;
    }

    static long edgeKey(WorldPoint a, WorldPoint b) {
        final long pa = pack(a) & 0xFFFFFFFFL;
        final long pb = pack(b) & 0xFFFFFFFFL;
        final long low = Math.min(pa, pb);
        final long high = Math.max(pa, pb);
        return (low << 32) | high;
    }

    static int pack(WorldPoint point) {
        return (point.getX() & 0x7FFF)
                | ((point.getY() & 0x7FFF) << 15)
                | ((point.getPlane() & 0x3) << 30);
    }

    private int heuristic(WorldPoint a, WorldPoint b) {
        final int dx = Math.abs(a.getX() - b.getX());
        final int dy = Math.abs(a.getY() - b.getY());
        final int diagonal = Math.min(dx, dy);
        final int straight = Math.max(dx, dy) - diagonal;
        return diagonal * 14 + straight * 10;
    }

    private static final class Node {
        private final WorldPoint point;
        private final int g;
        private final int f;

        private Node(WorldPoint point, int g, int f) {
            this.point = point;
            this.g = g;
            this.f = f;
        }
    }
}
