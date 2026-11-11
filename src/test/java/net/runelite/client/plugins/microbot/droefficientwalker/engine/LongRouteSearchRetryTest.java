package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LongRouteSearchRetryTest {
    private static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3164, 3487, 0);
    private static final WorldPoint BARBARIAN_OUTPOST_BANK = new WorldPoint(2536, 3574, 0);

    @Test void longOverlandRouteToBarbarianOutpostIsFoundWithDefaultBudgets() throws Exception {
        List<TransportCatalog.TransportEdge> edges = TransportCatalog.loadAllTransports();
        CollisionRoutePlanner planner = CollisionRoutePlanner.create(edges, e -> true);
        long previousBudget = CollisionRoutePlanner.searchBudgetNanos;
        try {
            // A budget too small for the first pass must be rescued by the bounded retry.
            CollisionRoutePlanner.searchBudgetNanos = 700_000_000L;
            var result = planner.planRoute(GRAND_EXCHANGE, List.of(BARBARIAN_OUTPOST_BANK),
                Set.of(), Set.of(), new HashSet<>(edges));
            assertFalse(result.path.isEmpty(), "failure: " + result.failure);
        } finally {
            CollisionRoutePlanner.searchBudgetNanos = previousBudget;
        }
    }
}
