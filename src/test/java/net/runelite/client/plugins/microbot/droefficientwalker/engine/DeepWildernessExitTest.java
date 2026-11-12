package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.WildernessTravelPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeepWildernessExitTest {
    private static final WorldPoint HILL = new WorldPoint(3042, 10319, 0);
    private static final WorldPoint STAIR = new WorldPoint(3044, 10323, 0);
    private static final WorldPoint SURFACE = new WorldPoint(3044, 3927, 0);
    private static final WorldPoint SOUTH = new WorldPoint(3044, 3918, 0);
    private static final WorldPoint FEROX = new WorldPoint(3130, 3631, 0);

    @Test void bundledStaircaseCanExitTheReportedHillGiantTile() throws Exception {
        List<TransportCatalog.TransportEdge> edges = TransportCatalog.loadAllTransports();
        assertTrue(edges.stream().anyMatch(e -> e.objectId == 16665
            && STAIR.equals(e.origin) && SURFACE.equals(e.destination)));
        CollisionRoutePlanner planner = CollisionRoutePlanner.create(edges, e -> true);
        boolean previousPermission = CollisionRoutePlanner.allowWildernessTravel;
        WorldPoint previousOrigin = CollisionRoutePlanner.aioTravelOrigin;
        WorldPoint previousTarget = CollisionRoutePlanner.aioTravelTarget;
        long previousBudget = CollisionRoutePlanner.searchBudgetNanos;
        try {
            // Reachability is asserted here; the 2s production budget is machine-dependent for this long surface route.
            CollisionRoutePlanner.searchBudgetNanos = 60_000_000_000L;
            CollisionRoutePlanner.aioTravelTarget = FEROX;
            for (boolean permission : List.of(false, true)) {
                CollisionRoutePlanner.allowWildernessTravel = permission;
                for (WorldPoint origin : List.of(HILL, SURFACE)) {
                    CollisionRoutePlanner.aioTravelOrigin = origin;
                    for (List<WorldPoint> targets : List.of(List.of(SOUTH), List.of(FEROX),
                        List.of(FEROX, new WorldPoint(3130, 3637, 0)))) {
                        var result = planner.planRoute(origin, targets, Set.of(), Set.of(), new HashSet<>(edges));
                        assertFalse(result.path.isEmpty(), "Exit from " + origin + " to " + targets + " failure: " + result.failure);
                        assertTrue(result.path.contains(SURFACE), "Use the existing staircase landing");
                        for (int i = 0; i < result.path.size(); i++)
                            assertTrue(executionAllowed(result.path.subList(i, result.path.size()), FEROX, permission),
                                "Remaining route at " + result.path.get(i));
                    }
                }
            }
        } finally {
            CollisionRoutePlanner.allowWildernessTravel = previousPermission;
            CollisionRoutePlanner.aioTravelOrigin = previousOrigin;
            CollisionRoutePlanner.aioTravelTarget = previousTarget;
            CollisionRoutePlanner.searchBudgetNanos = previousBudget;
        }
    }

    @Test void executionRejectsUnrelatedDepthSafeEntryAndReentry() throws Exception {
        assertFalse(executionAllowed(List.of(HILL, new WorldPoint(3100, 3927, 0), FEROX), FEROX, true));
        assertFalse(executionAllowed(List.of(HILL, new WorldPoint(3044, 3935, 0), FEROX), FEROX, true));
        assertFalse(executionAllowed(List.of(FEROX, HILL, SURFACE, FEROX), FEROX, true));
        assertFalse(executionAllowed(List.of(HILL, SURFACE, FEROX, SURFACE, FEROX), FEROX, true));
        assertFalse(executionAllowed(List.of(new WorldPoint(3042, 10319, 1), STAIR, SURFACE, FEROX), FEROX, false));
        assertFalse(executionAllowed(List.of(FEROX, HILL), HILL, false));
        assertTrue(executionAllowed(List.of(FEROX, HILL), HILL, true));
    }

    private static boolean executionAllowed(List<WorldPoint> path, WorldPoint target, boolean allowed) throws Exception {
        var method = WildernessTravelPolicy.class.getDeclaredMethod("routeAllowed", List.class, WorldPoint.class, boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, path, target, allowed);
    }

    @Test void planningAdmitsOnlyTheDungeonExitAndKeepsSafeOriginRestrictions() {
        Object previous = WildernessTravelPolicy.beginPlanning(List.of(HILL), List.of(FEROX), false, HILL, FEROX);
        try {
            assertFalse(WildernessTravelPolicy.plannerBlocked(STAIR, false));
            assertFalse(WildernessTravelPolicy.plannerBlocked(SURFACE, false));
            assertTrue(WildernessTravelPolicy.plannerBlocked(new WorldPoint(3100, 3927, 0), true));
            assertTrue(WildernessTravelPolicy.plannerBlocked(new WorldPoint(3044, 3935, 0), true));
        } finally { WildernessTravelPolicy.endPlanning(previous); }
        previous = WildernessTravelPolicy.beginPlanning(List.of(HILL, FEROX), List.of(FEROX), true, HILL, FEROX);
        try { assertTrue(WildernessTravelPolicy.plannerBlocked(STAIR, true)); }
        finally { WildernessTravelPolicy.endPlanning(previous); }
        previous = WildernessTravelPolicy.beginPlanning(List.of(HILL), List.of(FEROX), true, FEROX, FEROX);
        try { assertTrue(WildernessTravelPolicy.plannerBlocked(STAIR, true)); }
        finally { WildernessTravelPolicy.endPlanning(previous); }
    }
}
