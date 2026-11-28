package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.PathfinderConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The Efficient Walker planner keeps out of Draynor Manor undead tree rings where a short detour exists. */
class DraynorManorDangerousRouteTest {
    private static final WorldPoint START = new WorldPoint(3109, 3330, 0);
    private static List<TransportCatalog.TransportEdge> edges;
    private static CollisionRoutePlanner planner;
    private static long previousBudget;

    @BeforeAll static void load() throws Exception {
        edges = TransportCatalog.loadAllTransports();
        planner = CollisionRoutePlanner.create(edges, e -> true);
        previousBudget = CollisionRoutePlanner.searchBudgetNanos;
        CollisionRoutePlanner.searchBudgetNanos = 60_000_000_000L;
    }

    @AfterAll static void restore() {
        CollisionRoutePlanner.searchBudgetNanos = previousBudget;
    }

    private static boolean ring(WorldPoint tile) {
        return PathfinderConfig.isDangerousRingTile(tile.getX(), tile.getY(), tile.getPlane());
    }

    private static List<WorldPoint> plan(WorldPoint start, WorldPoint target) {
        var result = planner.planRoute(start, List.of(target), Set.of(), Set.of(), new HashSet<>(edges));
        assertFalse(result.path.isEmpty(), "No route " + start + " -> " + target + ": " + result.failure);
        assertEquals(target, result.path.get(result.path.size() - 1));
        return result.path;
    }

    @Test void stepCostStaysInsideTheDistanceBuckets() {
        assertTrue(1 + CollisionRoutePlanner.DANGEROUS_RING_STEP_COST <= 28);
    }

    /**
     * 3110,3344 is blocked, so the courtyard lane must cross 3109,3345 beside the tree at 3108,3346; a
     * ring-free walk goes around the whole manor, longer than the ring cost. Every other ring is avoided.
     */
    @Test void courtyardRouteToFrontDoorCrossesOnlyTheChokepoint() {
        WorldPoint chokepoint = new WorldPoint(3109, 3345, 0);
        List<WorldPoint> path = plan(START, new WorldPoint(3108, 3353, 0));
        for (WorldPoint tile : path)
            assertFalse(ring(tile) && !tile.equals(chokepoint), "Route enters a tree ring at " + tile + ": " + path);
    }

    @Test void manorDestinationsStayReachable() {
        plan(START, new WorldPoint(3109, 3341, 0));
        plan(START, new WorldPoint(3108, 3351, 0));
        plan(START, new WorldPoint(3110, 3350, 0));
        plan(new WorldPoint(3108, 3353, 0), new WorldPoint(3109, 3329, 0));
        WorldPoint besideTree = new WorldPoint(3110, 3340, 0);
        assertTrue(ring(besideTree));
        plan(START, besideTree);
    }

    private static WorldPoint wp(int x, int y) { return new WorldPoint(x, y, 0); }

    @Test void walkClickStopsBeforeTheTreeCorner() {
        List<WorldPoint> tiles = List.of(wp(3109, 3343), wp(3109, 3344), wp(3109, 3345), wp(3110, 3346),
            wp(3111, 3346), wp(3112, 3346), wp(3113, 3347), wp(3114, 3348));
        WorldPoint door = wp(3108, 3353);
        assertEquals(2, WalkerEngine.capClickIndexBeforeDanger(tiles, 6, door), "click the planned chokepoint, not past the ring");
        assertEquals(7, WalkerEngine.capClickIndexBeforeDanger(tiles, 7, wp(3114, 3348)), "the destination is exempt");
    }

    @Test void walkClickAlongTheClearLaneIsUnchanged() {
        List<WorldPoint> tiles = new java.util.ArrayList<>();
        for (int y = 3333; y <= 3344; y++) tiles.add(wp(3109, y));
        assertEquals(11, WalkerEngine.capClickIndexBeforeDanger(tiles, 11, wp(3108, 3353)));
    }
}
