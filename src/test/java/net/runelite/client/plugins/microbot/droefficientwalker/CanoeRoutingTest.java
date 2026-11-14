package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportResources;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.SplitFlagMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CanoeRoutingTest
{
    private static final WorldPoint EDGEVILLE = new WorldPoint(3132, 3510, 0);
    private static final WorldPoint FEROX = new WorldPoint(3154, 3638, 0);
    private static final WorldPoint POND = new WorldPoint(3141, 3796, 0);

    @Test public void canoeCheckboxDefaultsOffImmediatelyBelowBalloons() throws Exception
    {
        assertFalse(new EfficientWalkerConfig() { }.useCanoes());
        ConfigItem canoe = EfficientWalkerConfig.class.getMethod("useCanoes").getAnnotation(ConfigItem.class);
        ConfigItem balloon = EfficientWalkerConfig.class.getMethod("useHotAirBalloons").getAnnotation(ConfigItem.class);
        ConfigItem bank = EfficientWalkerConfig.class.getMethod("useItemsFromBank").getAnnotation(ConfigItem.class);
        assertEquals(balloon.section(), canoe.section());
        assertEquals(balloon.position() + 1, canoe.position());
        assertEquals(canoe.position() + 1, bank.position());
    }

    @Test public void allCatalogCanoesRespectBothPermissionsAndKeepFeroxSafe()
    {
        int safe = 0, wilderness = 0;
        for (var group : TransportResources.loadAllFromResources().values())
        {
            for (Transport row : group)
            {
                if (row.getType() != TransportType.CANOE) continue;
                WorldPoint from = row.getOrigin(), to = row.getDestination();
                assertTrue(WildernessTravelPolicy.isCanoe(row.getAction(), row.getName()));
                assertFalse(WildernessTravelPolicy.canoeAllowed(from, to, false, false));
                assertFalse(WildernessTravelPolicy.canoeAllowed(from, to, false, true));
                assertTrue(WildernessTravelPolicy.canoeAllowed(from, to, true, true));
                boolean danger = WildernessTravelPolicy.isBlocked(from, false) || WildernessTravelPolicy.isBlocked(to, false);
                assertEquals(!danger, WildernessTravelPolicy.canoeAllowed(from, to, true, false), row.getDisplayInfo());
                if (danger) wilderness++; else safe++;
            }
        }
        assertTrue(safe > 0, "Safe canoe network must remain available");
        assertTrue(wilderness > 0, "Wilderness pond links must be gated");
        assertTrue(WildernessTravelPolicy.canoeAllowed(EDGEVILLE, FEROX, true, false));
        assertFalse(WildernessTravelPolicy.canoeAllowed(POND, EDGEVILLE, true, false));
    }

    @Test public void plannerRejectsDisabledOrUnsafeCanoesBeforeLiveRequirementsAndInvalidatesItsCache() throws Exception
    {
        PathfinderConfig planner = new PathfinderConfig(SplitFlagMap.fromResources(), new HashMap<>(),
            Collections.emptyList(), null, null);
        Transport row = mock(Transport.class);
        when(row.getType()).thenReturn(TransportType.CANOE);
        when(row.getOrigin()).thenReturn(EDGEVILLE);
        when(row.getDestination()).thenReturn(POND);
        Method admission = method(PathfinderConfig.class, "useTransport", Transport.class, boolean.class);
        Method fingerprint = method(PathfinderConfig.class, "packTransportRefreshToggleBits");
        long initial = (Long) fingerprint.invoke(planner);
        assertFalse((Boolean) admission.invoke(planner, row, false));
        field(PathfinderConfig.class, "useCanoes").setBoolean(planner, true);
        long canoeOn = (Long) fingerprint.invoke(planner);
        assertNotEquals(initial, canoeOn);
        assertFalse((Boolean) admission.invoke(planner, row, false));
        field(PathfinderConfig.class, "allowWildernessCanoes").setBoolean(planner, true);
        assertNotEquals(canoeOn, ((Long) fingerprint.invoke(planner)).longValue());
    }

    @Test public void executionReplansASelectedCanoeIfItsPermissionWasSwitchedOff() throws Exception
    {
        EfficientWalkerConfig config = mock(EfficientWalkerConfig.class);
        Engine engine = new Engine();
        NetworkTransportSupport bridge = new NetworkTransportSupport(mock(Client.class), config,
            getClass().getClassLoader(), Engine.class, engine, "unused", mock(CatalogTeleportSupport.class));
        for (String name : new String[]{"Origin", "Destination", "Action", "Target"})
            field(NetworkTransportSupport.class, "transport" + name + "Field").set(bridge,
                field(Edge.class, name.toLowerCase()));
        field(NetworkTransportSupport.class, "activeTransportField").set(bridge, field(Engine.class, "selected"));
        field(NetworkTransportSupport.class, "installed").setBoolean(bridge, true);
        when(config.allowWildernessTravel()).thenReturn(true);
        assertTrue(bridge.beforeEngineTick());
        assertEquals(engine.getDestination(), bridge.consumeReplanTarget());
        Method allowed = method(NetworkTransportSupport.class, "canoeAllowed", Object.class);
        when(config.useCanoes()).thenReturn(true);
        when(config.allowWildernessTravel()).thenReturn(false);
        assertFalse((Boolean) allowed.invoke(bridge, engine.selected));
        engine.selected.destination = FEROX;
        assertTrue((Boolean) allowed.invoke(bridge, engine.selected));
        engine.selected.destination = POND;
        when(config.allowWildernessTravel()).thenReturn(true);
        assertTrue((Boolean) allowed.invoke(bridge, engine.selected));
    }

    private static final class Edge
    {
        WorldPoint origin = EDGEVILLE, destination = POND;
        String action = "Paddle Canoe", target = "Canoe Station";
    }
    private static final class Engine
    {
        Edge selected = new Edge();
        public WorldPoint getDestination() { return new WorldPoint(3200, 3800, 0); }
    }
    private static Field field(Class<?> type, String name) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(type, name); field.setAccessible(true); return field;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception
    {
        Method method = WalkerTestReflection.declaredMethod(type, name, parameters); method.setAccessible(true); return method;
    }
}
