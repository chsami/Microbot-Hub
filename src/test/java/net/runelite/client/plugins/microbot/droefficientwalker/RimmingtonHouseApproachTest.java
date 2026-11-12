package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RimmingtonHouseApproachTest {
    @Test void boardFootprintIsBlockedAndTheRecordedApproachIsWalkable() {
        var map = SplitFlagMap.fromResources();
        assertTrue(blocked(map, new WorldPoint(2952, 3221, 0)));
        assertEquals(new WorldPoint(2954, 3223, 0), DestinationCorrections.resolve(new WorldPoint(2952, 3221, 0)));
        assertFalse(blocked(map, DestinationCorrections.RIMMINGTON_HOUSE_APPROACH));
    }

    @Test void ordinaryNearbyTeleportsAndTheRecordedRatPitsLandingReachTheBoardApproach() throws Exception {
        var config = new PathfinderConfig(SplitFlagMap.fromResources(), new HashMap<>(), List.of(), null, null);
        Field cutoff = WalkerTestReflection.declaredField(PathfinderConfig.class, "calculationCutoffMillis");
        cutoff.setAccessible(true);cutoff.setLong(config, 3000);
        Method plan = WalkerTestReflection.declaredMethod(Rs2PathApi.class, "planWithConfig", Rs2RouteRequest.class, PathfinderConfig.class);
        plan.setAccessible(true);
        WorldPoint target = DestinationCorrections.RIMMINGTON_HOUSE_APPROACH;
        for (WorldPoint landing : List.of(new WorldPoint(2934, 3294, 0), new WorldPoint(3105, 3251, 0), new WorldPoint(3018, 3231, 0))) {
            var result = (Rs2RouteResult) plan.invoke(null, Rs2RouteRequest.to(landing, target), config);
            assertTrue(result.isTargetReached(0), landing + ": " + result.getTerminationReason());
            assertEquals(target, result.getEndpoint().orElseThrow());
        }
    }

    private static boolean blocked(SplitFlagMap map, WorldPoint point) {
        int x = point.getX(), y = point.getY(), plane = point.getPlane();
        return !(map.get(x, y, plane, 0) || map.get(x, y, plane, 1)
            || map.get(x - 1, y, plane, 1) || map.get(x, y - 1, plane, 0));
    }
}
