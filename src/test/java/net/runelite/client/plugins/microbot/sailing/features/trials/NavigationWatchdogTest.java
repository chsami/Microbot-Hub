package net.runelite.client.plugins.microbot.sailing.features.trials;

import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationWatchdogTest {

    @Test
    void frozenBoatIsReportedStuckAfterNoProgressTicks() {
        var watchdog = new NavigationWatchdog(15, 3);

        assertEquals(NavigationWatchdog.Status.MOVING, watchdog.update(7, 12, 100));
        assertEquals(NavigationWatchdog.Status.MOVING, watchdog.update(7, 12, 114));
        assertEquals(NavigationWatchdog.Status.STUCK, watchdog.update(7, 12, 115));
        assertEquals(1, watchdog.getRecoveries());
    }

    @Test
    void approachingBoatIsNeverReportedStuck() {
        var watchdog = new NavigationWatchdog(15, 3);

        for (int tick = 0; tick < 60; tick++) {
            assertEquals(NavigationWatchdog.Status.MOVING, watchdog.update(3, 100 - tick, tick));
        }
    }

    @Test
    void givesUpAfterRepeatedFailedRecoveriesAtTheSameWaypoint() {
        var watchdog = new NavigationWatchdog(15, 3);

        watchdog.update(7, 12, 0);
        assertEquals(NavigationWatchdog.Status.STUCK, watchdog.update(7, 12, 15));
        watchdog.update(7, 12, 20);
        assertEquals(NavigationWatchdog.Status.STUCK, watchdog.update(7, 12, 35));
        watchdog.update(7, 12, 40);
        assertEquals(NavigationWatchdog.Status.STUCK, watchdog.update(7, 12, 55));
        watchdog.update(7, 12, 60);
        assertEquals(NavigationWatchdog.Status.GIVE_UP, watchdog.update(7, 12, 75));
    }

    @Test
    void reachingTheNextWaypointClearsFailedRecoveries() {
        var watchdog = new NavigationWatchdog(15, 3);

        watchdog.update(7, 12, 0);
        assertEquals(NavigationWatchdog.Status.STUCK, watchdog.update(7, 12, 15));
        assertEquals(NavigationWatchdog.Status.MOVING, watchdog.update(8, 9, 20));
        assertEquals(0, watchdog.getRecoveries());
    }

    @Test
    void tickCounterResetRestartsTheTimer() {
        var watchdog = new NavigationWatchdog(15, 3);

        watchdog.update(7, 12, 5000);
        assertEquals(NavigationWatchdog.Status.MOVING, watchdog.update(7, 12, 3));
        assertEquals(NavigationWatchdog.Status.MOVING, watchdog.update(7, 12, 17));
        assertEquals(NavigationWatchdog.Status.STUCK, watchdog.update(7, 12, 18));
    }

    @Test
    void resetClearsTheStuckWaypoint() {
        var watchdog = new NavigationWatchdog(15, 3);

        watchdog.update(7, 12, 0);
        watchdog.update(7, 12, 15);
        watchdog.reset();

        assertEquals(0, watchdog.getRecoveries());
        assertEquals(NavigationWatchdog.Status.MOVING, watchdog.update(7, 12, 16));
        assertEquals(NavigationWatchdog.Status.MOVING, watchdog.update(7, 12, 30));
    }

    @Test
    void recoveryHeadingTurnsAwayThenTheOtherWayThenBack() {
        assertEquals(13, TrialsScript.getRecoveryHeading(9, 1));
        assertEquals(5, TrialsScript.getRecoveryHeading(9, 2));
        assertEquals(1, TrialsScript.getRecoveryHeading(9, 3));
        assertEquals(2, TrialsScript.getRecoveryHeading(14, 1));
        assertEquals(14, TrialsScript.getRecoveryHeading(2, 2));
        for (int heading = 0; heading < 16; heading++) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                int recovery = TrialsScript.getRecoveryHeading(heading, attempt);
                assertTrue(recovery >= 0 && recovery < 16);
                assertTrue(recovery != heading);
            }
        }
    }

    @Test
    void repicksTheNearestWaypointAheadWithinLookahead() {
        var route = List.of(
                new WorldPoint(0, 0, 0),
                new WorldPoint(5, 0, 0),
                new WorldPoint(10, 0, 0),
                new WorldPoint(15, 0, 0),
                new WorldPoint(20, 0, 0),
                new WorldPoint(25, 0, 0),
                new WorldPoint(30, 0, 0));

        assertEquals(2, TrialsScript.getNearestWaypointAheadIndex(route, 0, new WorldPoint(10, 2, 0), Set.of()));
        assertEquals(4, TrialsScript.getNearestWaypointAheadIndex(route, 0, new WorldPoint(30, 0, 0), Set.of()));
        assertEquals(3, TrialsScript.getNearestWaypointAheadIndex(route, 3, new WorldPoint(0, 0, 0), Set.of()));
        assertEquals(6, TrialsScript.getNearestWaypointAheadIndex(route, 6, new WorldPoint(0, 0, 0), Set.of()));
    }

    @Test
    void stuckMessagesUsePlayerWordingWithoutWaypointNumbers() {
        assertEquals("Sailing trials: boat looks stuck, turning away to get free (try 2 of 3).", TrialsScript.boatStuckNotice(2));
        String stop = TrialsScript.boatStuckStopMessage();
        assertTrue(stop.startsWith("Sailing trials stopped: your boat is stuck against an obstacle"));
        assertTrue(stop.contains("Your trial is still running."));
        assertTrue(!stop.contains("waypoint") && !stop.matches(".*\\d{2,}.*"));
    }

    @Test
    void neverRepicksPastASupplyWaypoint() {
        var supply = new WorldPoint(5, 0, 0);
        var route = List.of(
                new WorldPoint(0, 0, 0),
                supply,
                new WorldPoint(10, 0, 0),
                new WorldPoint(15, 0, 0));

        assertEquals(1, TrialsScript.getNearestWaypointAheadIndex(route, 0, new WorldPoint(15, 0, 0), Set.of(supply)));
        assertEquals(1, TrialsScript.getNearestWaypointAheadIndex(route, 1, new WorldPoint(15, 0, 0), Set.of(supply)));
    }
}
