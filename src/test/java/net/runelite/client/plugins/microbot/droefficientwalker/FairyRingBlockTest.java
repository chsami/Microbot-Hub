package net.runelite.client.plugins.microbot.droefficientwalker;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportResources;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.jupiter.api.Test;

class FairyRingBlockTest {
    private static final WorldPoint ALQ = new WorldPoint(3597, 3495, 0);

    @Test
    void rootNetworkNeverUsesAlq() {
        List<TransportCatalog.TransportEdge> edges = TransportCatalog.loadFairyRings().getTransports();
        assertFalse(edges.stream().anyMatch(e -> ALQ.equals(e.origin)));
        assertFalse(edges.stream().anyMatch(e -> ALQ.equals(e.destination)));
        assertFalse(edges.isEmpty());
    }

    @Test
    void engineNetworkNeverUsesAlq() throws Exception {
        Class<?> network = Class.forName(getClass().getPackageName() + ".engine.FairyRingNetwork");
        Class<?> catalog = Class.forName(getClass().getPackageName() + ".engine.TransportCatalog");
        Method load = catalog.getDeclaredMethod("loadFairyRings");
        load.setAccessible(true);
        Method get = network.getDeclaredMethod("getTransports");
        get.setAccessible(true);
        List<?> edges = (List<?>) get.invoke(load.invoke(null));
        boolean origin = false;
        boolean destination = false;
        for (Object edge : edges) {
            origin |= ALQ.equals(field(edge, "origin"));
            destination |= ALQ.equals(field(edge, "destination"));
        }
        assertFalse(origin);
        assertFalse(destination);
        assertFalse(edges.isEmpty());
    }

    @Test
    void supportTransportsNeverUseAlq() {
        List<Transport> rows = new ArrayList<>();
        TransportResources.loadAllFromResources().values().forEach(rows::addAll);
        List<Transport> rings = rows.stream().filter(t -> t.getType() == TransportType.FAIRY_RING).collect(java.util.stream.Collectors.toList());
        assertFalse(rings.stream().anyMatch(t -> ALQ.equals(t.getOrigin())));
        assertFalse(rings.stream().anyMatch(t -> ALQ.equals(t.getDestination())));
        assertFalse(rings.isEmpty());
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
