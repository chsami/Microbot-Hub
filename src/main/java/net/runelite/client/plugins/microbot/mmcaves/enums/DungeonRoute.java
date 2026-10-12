package net.runelite.client.plugins.microbot.mmcaves.enums;

import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Numbered holes in Kruk's Dungeon. Hole 2's path is recorded for one character only. */
public enum DungeonRoute {
    HOLE_1("Hole 1 (unmapped)", -1, Collections.emptyList()),
    HOLE_2("Hole 2 - middle route", 28772, Arrays.asList(
            tile(2509, 9173), // Jungle Grass arrival
            tile(2507, 9160),
            tile(2505, 9150),
            tile(2505, 9144),
            tile(2514, 9144),
            tile(2514, 9146),
            tile(2511, 9146),
            tile(2511, 9148),
            tile(2518, 9148),
            tile(2519, 9151), // Landing north of the second pressure pad
            tile(2524, 9155),
            tile(2527, 9160),
            tile(2534, 9165),
            tile(2539, 9165),
            tile(2547, 9163),
            tile(2549, 9159),
            tile(2549, 9154),
            tile(2554, 9152),
            tile(2558, 9152),
            tile(2562, 9156),
            tile(2567, 9156),
            tile(2574, 9166), // Landing beyond the final pressure pad
            tile(2572, 9168) // Look-in / Enter hole
    )),
    HOLE_3("Hole 3 (unmapped)", -1, Collections.emptyList()),
    HOLE_4("Hole 4 (unmapped)", -1, Collections.emptyList()),
    HOLE_5("Hole 5 (unmapped)", -1, Collections.emptyList());

    private final String label;
    private final int holeId;
    private final List<WorldPoint> waypoints;

    DungeonRoute(String label, int holeId, List<WorldPoint> waypoints) {
        this.label = label;
        this.holeId = holeId;
        this.waypoints = Collections.unmodifiableList(waypoints);
    }

    public boolean isMapped() { return holeId > 0 && !waypoints.isEmpty(); }
    public int holeId() { return holeId; }
    public List<WorldPoint> waypoints() { return waypoints; }
    public WorldPoint checkTile() { return waypoints.get(waypoints.size() - 1); }

    /** Recorded obstacles keyed by their landing waypoint, not by a nearby tile. */
    public WorldPoint pressurePadForWaypoint(int index) {
        if (this != HOLE_2 || index < 0 || index >= waypoints.size()) return null;
        WorldPoint landing = waypoints.get(index);
        if (landing.equals(tile(2518, 9148))) return tile(2517, 9148);
        if (landing.equals(tile(2519, 9151))) return tile(2519, 9150);
        if (landing.equals(tile(2574, 9166))) return tile(2574, 9165);
        return null;
    }

    public WorldPoint squeezeHoleForWaypoint(int index) {
        if (this != HOLE_2 || index < 0 || index >= waypoints.size()) return null;
        WorldPoint landing = waypoints.get(index);
        if (landing.equals(tile(2524, 9155))) return tile(2521, 9155);
        if (landing.equals(tile(2558, 9152))) return tile(2555, 9152);
        return null;
    }

    public boolean isObstacleWaypoint(int index) {
        return pressurePadForWaypoint(index) != null || squeezeHoleForWaypoint(index) != null;
    }

    @Override
    public String toString() { return label; }

    private static WorldPoint tile(int x, int y) { return new WorldPoint(x, y, 1); }
}
