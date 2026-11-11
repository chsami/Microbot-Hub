package net.runelite.client.plugins.microbot.droefficientwalker.support;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.poh.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class PortableTransportTest {
    @Test public void fullCatalogAndCollisionDataLoadFromVanillaResources() throws Exception {
        for (String name : List.of("transports.tsv", "restrictions.tsv", "blocked_edges.tsv", "dangerous_tiles.tsv", "collision-map.zip")) {
            try (java.io.InputStream stream = TransportResources.openResource(name)) { assertNotNull(stream, name); assertTrue(stream.read() >= 0); }
        }
        assertTrue(TransportResources.loadAllFromResources().size() > 100);
        assertNotNull(TransportResources.loadRestrictions());
    }
    @Test public void rowParserRetainsConsumptionAndWildernessLimits() {
        Transport tablet = TransportFactory.row(Map.of("Destination", "3222 3218 0", "Consumable", "T",
                "Item IDs", "8013", "Wilderness level", "19"), TransportType.TELEPORTATION_ITEM);
        assertTrue(tablet.isConsumable()); assertEquals(19, tablet.getMaxWildernessLevel());
        assertEquals(Set.of(Set.of(8013)), tablet.getItemIdRequirements());
    }
    @Test public void pairedNetworksKeepActualEndpointNamesAndActions() {
        Transport a = TransportFactory.row(Map.of("Origin", "100 100 0", "Destination", "network",
                "Display info", "A", "menuOption menuTarget objectID", "Travel;Spirit tree;1"), TransportType.SPIRIT_TREE);
        Transport b = TransportFactory.row(Map.of("Origin", "network", "Destination", "200 200 0",
                "Display info", "B"), TransportType.SPIRIT_TREE);
        Transport edge = TransportFactory.pair(a, b);
        assertSame(a, TransportFactory.origin(edge)); assertSame(b, TransportFactory.destination(edge));
        assertEquals(new WorldPoint(100,100,0), edge.getOrigin()); assertEquals(new WorldPoint(200,200,0), edge.getDestination());
        assertEquals("Travel", edge.getAction());
    }
    @Test public void houseDoorsAreAdmittedWithoutPretendingToBeTeleportFurniture() {
        Transport door = new Transport(new WorldPoint(2954,3224,0), new WorldPoint(1877,7052,1),
                "Rimmington -> PoH", TransportType.POH, true, "Home", "Portal", 15478);
        assertEquals(TransportExecutionRegistry.Executor.OBJECT, TransportExecutionRegistry.executorFor(door).get());
    }
    @Test public void correctedNexusSlotKeysDoNotAliasEnumOrdinals() {
        assertEquals(7, NexusPortal.SENNTISTEN.varbitValue()); assertEquals(8, NexusPortal.MARIM.varbitValue());
        assertEquals(10, NexusPortal.LUNAR_ISLE.varbitValue()); assertEquals(154, NexusPortal.SEERS_VILLAGE.varbitValue());
        assertEquals(45, NexusPortal.VARBITS.length);
    }
}
