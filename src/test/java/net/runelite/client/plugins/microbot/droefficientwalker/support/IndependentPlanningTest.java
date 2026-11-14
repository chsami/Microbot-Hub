package net.runelite.client.plugins.microbot.droefficientwalker.support;

import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;

import java.util.*;
import java.lang.reflect.Field;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IndependentPlanningTest
{
    @Test
    public void computesRouteWithoutMainWalkerConfiguration() throws Exception
    {
        PathfinderConfig config = new PathfinderConfig(SplitFlagMap.fromResources(),
            new HashMap<>(), Collections.emptyList(), null, null);
        Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
        cutoff.setAccessible(true);
        cutoff.setLong(config, 10000L);
        WorldPoint target = new WorldPoint(3225, 3218, 0);
        Rs2RouteResult result = Rs2PathApi.planWithConfig(
            Rs2RouteRequest.to(new WorldPoint(3222, 3218, 0), target), config);
        assertTrue(result.isTargetReached(0));
        assertEquals(target, result.getEndpoint().orElse(null));
        assertEquals(result.getPath().size() - 1, result.getSteps().size());
    }

    @Test
    public void reflectiveHandlersExist() throws Exception
    {
        Class<?> executor = Class.forName("net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2WalkerTransports");
        for (String name : List.of("handleCanoe", "handleMagicCarpet", "handleGlider", "handleCharterShip", "handleSeasonalTransport", "handleTeleportSpell"))
            assertNotNull(executor.getDeclaredMethod(name, net.runelite.client.plugins.microbot.shortestpath.Transport.class));
        assertNotNull(Rs2HotAirBalloon.class.getDeclaredMethod("handle", Rs2TransportEdge.class));
    }
}
