/*
 * DroSulliuscep - Copyright (c) 2026, Dorian (DRO / droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by Dorian (DRO / droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.api.coords.WorldPoint;

import java.util.*;

/** Plans on the recorded walk network in either direction, independent of tree order. */
final class RecordedCourseNavigator
{
    private RecordedCourseNavigator() {}

    static WorldPoint point(int node)
    {
        int[] p = RecordedCoursePath.POINTS[node];
        return new WorldPoint(p[0], p[1], 0);
    }

    static WorldPoint approach(int tree)
    {
        return point(RecordedCoursePath.TREE_NODES[tree]);
    }

    static List<WorldPoint> plan(Map<WorldPoint, Integer> reachable, int tree, Set<Integer> blocked)
    {
        int count = RecordedCoursePath.POINTS.length;
        int nearest = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++)
            nearest = Math.min(nearest, reachable.getOrDefault(point(i), Integer.MAX_VALUE));
        if (nearest == Integer.MAX_VALUE) return Collections.emptyList();

        double[] distance = new double[count];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        int[] previous = new int[count];
        Arrays.fill(previous, -1);
        boolean[] visited = new boolean[count];
        // Join only nearby reachable samples. Never jump across a closed vine just because
        // its far side is geometrically closer, or take an unrecorded shortcut across the swamp.
        for (int i = 0; i < count; i++)
        {
            int steps = reachable.getOrDefault(point(i), Integer.MAX_VALUE);
            if (steps <= nearest + 2) distance[i] = steps;
        }
        int goal = RecordedCoursePath.TREE_NODES[tree];
        for (int iteration = 0; iteration < count; iteration++)
        {
            int current = -1;
            for (int i = 0; i < count; i++)
                if (!visited[i] && (current < 0 || distance[i] < distance[current])) current = i;
            if (current < 0 || Double.isInfinite(distance[current])) break;
            if (current == goal) break;
            visited[current] = true;
            for (int[] edge : RecordedCoursePath.EDGES)
            {
                int next = edge[0] == current ? edge[1] : edge[1] == current ? edge[0] : -1;
                if (next < 0 || visited[next]) continue;
                int obstacle = obstacleBetween(point(current), point(next));
                double cost = point(current).distanceTo(point(next))
                        + (blocked.contains(obstacle) ? 5 : 0);
                if (distance[current] + cost < distance[next])
                {
                    distance[next] = distance[current] + cost;
                    previous[next] = current;
                }
            }
        }
        if (Double.isInfinite(distance[goal])) return Collections.emptyList();
        List<WorldPoint> route = new ArrayList<>();
        for (int node = goal; node >= 0; node = previous[node]) route.add(point(node));
        Collections.reverse(route);
        return route;
    }

    static int obstacleBetween(WorldPoint from, WorldPoint to)
    {
        for (int i = 0; i < SulliuscepRouteData.OBSTACLES.size(); i++)
        {
            SulliuscepRouteData.RouteObstacle obstacle = SulliuscepRouteData.OBSTACLES.get(i);
            WorldPoint anchor = obstacle.location();
            // Footprints decoded from recorded anchor/centre coordinates: thick vine 2x1,
            // thick vines 2x2, rake vines 1x1. Test the short observed movement segment.
            int width = obstacle.objectId() == SulliuscepRouteData.VINES_OBJECT_ID ? 1 : 2;
            int height = obstacle.objectId() == SulliuscepRouteData.THICK_VINES_OBJECT_ID ? 2 : 1;
            int steps = Math.max(1, from.distanceTo(to) * 2);
            for (int step = 0; step <= steps; step++)
            {
                double x = from.getX() + (to.getX() - from.getX()) * step / (double) steps;
                double y = from.getY() + (to.getY() - from.getY()) * step / (double) steps;
                if (x >= anchor.getX() - 0.25 && x <= anchor.getX() + width - 0.75
                        && y >= anchor.getY() - 0.25 && y <= anchor.getY() + height - 0.75)
                    return i;
            }
        }
        return -1;
    }
}
