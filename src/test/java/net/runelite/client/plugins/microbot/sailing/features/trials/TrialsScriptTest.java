package net.runelite.client.plugins.microbot.sailing.features.trials;

import net.runelite.api.coords.WorldPoint;

import java.util.List;

public class TrialsScriptTest {

    public static void main(String[] args) {
        advancesAcrossPassedSegmentsAfterLateralCourseChange();
        advancesPastMissedWaypointWithoutWrappingTheRoute();
    }

    private static void advancesAcrossPassedSegmentsAfterLateralCourseChange() {
        var route = List.of(
                new WorldPoint(0, 0, 0),
                new WorldPoint(5, 5, 0),
                new WorldPoint(10, 5, 0),
                new WorldPoint(15, 5, 0));

        assertEquals(2, TrialsScript.getNextWaypointIndex(route, 0, new WorldPoint(10, -9, 0)));
    }

    private static void advancesPastMissedWaypointWithoutWrappingTheRoute() {
        var route = List.of(
                new WorldPoint(0, 0, 0),
                new WorldPoint(5, 0, 0),
                new WorldPoint(10, 0, 0));

        assertEquals(2, TrialsScript.getNextWaypointIndex(route, 0, new WorldPoint(6, 6, 0)));
        assertEquals(0, TrialsScript.getNextWaypointIndex(route, 0, new WorldPoint(-6, 0, 0)));
        assertEquals(2, TrialsScript.getNextWaypointIndex(route, 2, new WorldPoint(0, 0, 0)));
        assertEquals(0, TrialsScript.getNextWaypointIndex(route, 2, new WorldPoint(10, 0, 0)));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

}
