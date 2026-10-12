package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mmcaves.enums.DungeonRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonRouteTest {
    @Test
    void squeezeHolesMatchRecordedTargetsAndExits() {
        DungeonRoute route = DungeonRoute.HOLE_2;
        assertEquals(new WorldPoint(2521, 9155, 1), route.squeezeHoleForWaypoint(10));
        assertEquals(new WorldPoint(2524, 9155, 1), route.waypoints().get(10));
        assertEquals(new WorldPoint(2555, 9152, 1), route.squeezeHoleForWaypoint(18));
        assertEquals(new WorldPoint(2558, 9152, 1), route.waypoints().get(18));
        assertTrue(route.isObstacleWaypoint(10));
        assertTrue(route.isObstacleWaypoint(18));
        assertTrue(route.isObstacleWaypoint(21));
        assertFalse(route.isObstacleWaypoint(17));
    }

    @Test
    void pressurePadsAreAttachedToTheirRecordedLandingTiles() {
        DungeonRoute route = DungeonRoute.HOLE_2;
        assertEquals(new WorldPoint(2517, 9148, 1), route.pressurePadForWaypoint(8));
        assertEquals(new WorldPoint(2518, 9148, 1), route.waypoints().get(8));
        assertEquals(new WorldPoint(2519, 9150, 1), route.pressurePadForWaypoint(9));
        assertEquals(new WorldPoint(2519, 9151, 1), route.waypoints().get(9));
        assertEquals(new WorldPoint(2574, 9165, 1), route.pressurePadForWaypoint(21));
        assertEquals(new WorldPoint(2574, 9166, 1), route.waypoints().get(21));
        assertEquals(null, route.pressurePadForWaypoint(7));
        assertEquals(null, route.pressurePadForWaypoint(10));
    }

    @Test
    void holeTwoMatchesRecordedEntranceAndCheckTile() {
        DungeonRoute route = DungeonRoute.HOLE_2;
        assertTrue(route.isMapped());
        assertEquals(28772, route.holeId());
        assertEquals(new WorldPoint(2509, 9173, 1), route.waypoints().get(0));
        assertEquals(new WorldPoint(2572, 9168, 1), route.checkTile());
        assertTrue(route.waypoints().size() > 10);
        for (int i = 1; i < route.waypoints().size(); i++) {
            assertTrue(route.waypoints().get(i - 1).distanceTo(route.waypoints().get(i)) <= 13,
                    "Waypoint gap " + i + " exceeds the nearby-click range");
        }
    }

    @Test
    void otherHolesCannotNavigateWithoutRecordedPaths() {
        for (DungeonRoute route : DungeonRoute.values()) {
            if (route == DungeonRoute.HOLE_2) continue;
            assertFalse(route.isMapped());
            assertTrue(route.waypoints().isEmpty());
        }
    }
}
