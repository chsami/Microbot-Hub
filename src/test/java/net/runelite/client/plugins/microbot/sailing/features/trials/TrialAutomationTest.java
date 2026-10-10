package net.runelite.client.plugins.microbot.sailing.features.trials;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialLocations;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialRanks;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.TrialRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialAutomationTest {
    private static final WorldPoint START = new WorldPoint(0, 0, 0);
    private static final WorldPoint SUPPLY = new WorldPoint(5, 0, 0);
    private static final WorldPoint END = new WorldPoint(10, 0, 0);
    private static final List<WorldPoint> ROUTE = List.of(START, SUPPLY, END);

    @Test
    void findRankReturnsConfiguredRankButton() {
        Widget swordfish = widget("<col=ffffff>Swordfish</col>", false);
        assertSame(swordfish, TrialAutomation.findRank(swordfish, "Swordfish"));
    }

    @Test
    void findRankNeverSelectsAnotherRank() {
        assertNull(TrialAutomation.findRank(widget("Swordfish", false), "Shark"));
    }

    @Test
    void findRankIgnoresHiddenButtons() {
        assertNull(TrialAutomation.findRank(widget("Swordfish", true), "Swordfish"));
    }

    @Test
    void findRankHandlesMissingRoot() {
        assertNull(TrialAutomation.findRank(null, "Marlin"));
    }

    @Test
    void passedRegularWaypointsAreSkipped() {
        assertEquals(2, TrialsScript.getNextWaypointIndex(ROUTE, 0, new WorldPoint(8, 0, 0), Set.of()));
    }

    @Test
    void passedSupplyWaypointIsNotSkipped() {
        assertEquals(1, TrialsScript.getNextWaypointIndex(ROUTE, 0, new WorldPoint(8, 0, 0), Set.of(SUPPLY)));
        assertEquals(1, TrialsScript.getNextWaypointIndex(ROUTE, 1, new WorldPoint(8, 0, 0), Set.of(SUPPLY)));
    }

    @Test
    void supplyWaypointRequiresOneTileArrival() {
        assertEquals(1, TrialsScript.getNextWaypointIndex(ROUTE, 1, new WorldPoint(7, 0, 0), Set.of(SUPPLY)));
        assertEquals(2, TrialsScript.getNextWaypointIndex(ROUTE, 1, new WorldPoint(6, 0, 0), Set.of(SUPPLY)));
    }

    @Test
    void regularWaypointKeepsFiveTileArrival() {
        assertEquals(2, TrialsScript.getNextWaypointIndex(ROUTE, 1, new WorldPoint(7, 0, 0), Set.of()));
        assertEquals(2, TrialsScript.getNextWaypointIndex(ROUTE, 1, new WorldPoint(7, 0, 0)));
    }

    @Test
    void temporMarlinRouteContainsEverySupplyWaypoint() {
        TrialRoute marlin = TrialRoute.AllTrialRoutes.stream()
                .filter(route -> route.Location == TrialLocations.TemporTantrum && route.Rank == TrialRanks.Marlin)
                .findFirst()
                .orElse(null);
        assertNotNull(marlin);
        assertTrue(marlin.getInterpolatedPoints().containsAll(TrialsScript.TEMPOR_MARLIN_SUPPLY_WAYPOINTS));
        assertTrue(marlin.getInterpolatedPoints().contains(new WorldPoint(3037, 2761, 0)));
    }

    private static Widget widget(String text, boolean hidden) {
        return (Widget) Proxy.newProxyInstance(TrialAutomationTest.class.getClassLoader(), new Class<?>[]{Widget.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getText": return text;
                        case "isHidden": return hidden;
                        case "getWidth": return 60;
                        case "getHeight": return 20;
                        default: return null;
                    }
                });
    }
}
