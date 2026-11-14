package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WildernessGodWarsTransportsTest {
    @Test public void bankPlanningCatalogContainsEveryGwdConnection() {
        var catalog = net.runelite.client.plugins.microbot.droefficientwalker.support.TransportResources.loadAllFromResources();
        for (var expected : WildernessGodWarsTransports.create()) {
            var group = catalog.get(expected.getOrigin());
            assertNotNull(group);
            var actual = group.stream().filter(edge -> expected.getDestination().equals(edge.getDestination())
                && expected.getAction().equals(edge.getAction())).findFirst().orElse(null);
            assertNotNull(actual);
            assertEquals(expected.getObjectId(), actual.getObjectId());
            assertArrayEquals(expected.getSkillLevels(), actual.getSkillLevels());
            assertTrue(actual.getType() == net.runelite.client.plugins.microbot.shortestpath.TransportType.NPC
                || NetworkTransportSupport.isUndergroundObjectTransport(actual));
            assertTrue(net.runelite.client.plugins.microbot.droefficientwalker.support.TransportExecutionRegistry.canExecute(actual));
        }
    }

    @Test public void inboundRouteConnectsSurfaceViaBoulderToObservedInterior() {
        var edges = WildernessGodWarsTransports.create();
        assertEquals(3, edges.size());
        assertEquals(new WorldPoint(3017, 3740, 0), edges.get(0).getOrigin());
        assertEquals(new WorldPoint(3065, 10159, 3), edges.get(0).getDestination());
        assertEquals(new WorldPoint(3056, 10165, 3), edges.get(1).getOrigin());
        assertEquals(3, edges.get(1).getDestination().getPlane());
        assertTrue(edges.get(1).getDestination().distanceTo(edges.get(2).getOrigin()) <= 3);
        assertEquals(new WorldPoint(3034, 10158, 0), edges.get(2).getDestination());
        assertEquals("Enter", edges.get(0).getAction());
        assertEquals("Move", edges.get(1).getAction());
        assertEquals(net.runelite.client.plugins.microbot.shortestpath.TransportType.NPC, edges.get(1).getType());
        assertEquals(6621, edges.get(1).getObjectId());
        assertEquals(new WorldPoint(3049,10165,3), edges.get(2).getOrigin());
        assertEquals("Use", edges.get(2).getAction());
        assertEquals(60, edges.get(1).getSkillLevels()[Skill.STRENGTH.ordinal()]);
    }

    @Test public void boulderAndAnimationRulesAreScopedToTheObservedEdges() {
        var edges = WildernessGodWarsTransports.create();
        assertFalse(WildernessGodWarsTransports.isBoulder(edges.get(0)));
        assertTrue(WildernessGodWarsTransports.isBoulder(edges.get(1)));
        assertFalse(WildernessGodWarsTransports.isCrevice(edges.get(1)));
        assertTrue(WildernessGodWarsTransports.isCrevice(edges.get(2)));
        assertFalse(WildernessGodWarsTransports.isCrevice(null));
    }
}
