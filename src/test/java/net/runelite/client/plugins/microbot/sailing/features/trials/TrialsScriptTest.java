package net.runelite.client.plugins.microbot.sailing.features.trials;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialInfo;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialLocations;

import java.util.List;

public class TrialsScriptTest {

    public static void main(String[] args) {
        advancesAcrossPassedSegmentsAfterLateralCourseChange();
        advancesPastMissedWaypointWithoutWrappingTheRoute();
        selectsRumActionOnlyWhenEligible();
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

    private static void selectsRumActionOnlyWhenEligible() {
        var trial = new TrialInfo();
        trial.Location = TrialLocations.TemporTantrum;
        trial.TotalPrimaryObjectivesNeeded = 1;

        assertEquals("Collect-rum", TrialsScript.getRumAction(trial, 15));

        trial.HasRum = true;
        assertEquals("Deliver-rum", TrialsScript.getRumAction(trial, 15));

        assertNull(TrialsScript.getRumAction(trial, 16));

        trial.CollectedPrimaryObjectives = 1;
        assertNull(TrialsScript.getRumAction(trial, 15));

        trial.CollectedPrimaryObjectives = 0;
        trial.Location = TrialLocations.JubblyJive;
        assertNull(TrialsScript.getRumAction(trial, 15));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertNull(Object actual) {
        if (actual != null) {
            throw new AssertionError("Expected null but got " + actual);
        }
    }

}
