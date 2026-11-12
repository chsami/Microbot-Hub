package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.api.widgets.Widget;

public class GreatConchTransportTest
{
    private List<Transport> catalog()
    {
        List<Transport> rows = new ArrayList<>();
        TransportResources.loadAllFromResources().values().forEach(rows::addAll);
        return rows;
    }

    @Test public void completeRouteCatalogIncludesBothDirectionsAndFullUnlocks()
    {
        List<Transport> rows = catalog();
        int inbound = 0, outbound = 0;
        for (Transport row : rows)
        {
            if (row.getType() != TransportType.CHARTER_SHIP
                || !GreatConchTransports.isCharterConnection(row.getOrigin(), row.getDestination())) continue;
            assertEquals(QuestState.FINISHED, row.getQuests().get(Quest.TROUBLED_TORTUGANS));
            assertTrue(TransportExecutionRegistry.canExecute(row));
            assertEquals("Coins", row.getCurrencyName());
            assertTrue(row.getCurrencyAmount() >= 900 && row.getCurrencyAmount() <= 4200);
            if (GreatConchTransports.DOCK.equals(row.getDestination()))
            {
                inbound++;
                assertEquals("The Summer Shore", row.getDisplayInfo());
                assertEquals(0, row.getOrigin().getPlane());
            }
            else
            {
                outbound++;
                assertEquals(0, row.getDestination().getPlane());
                if ("Prifddinas".equals(row.getDisplayInfo()))
                    assertEquals(QuestState.FINISHED, row.getQuests().get(Quest.SONG_OF_THE_ELVES));
                if ("Mos Le'Harmless".equals(row.getDisplayInfo()))
                {
                    assertEquals(QuestState.FINISHED, row.getQuests().get(Quest.CABIN_FEVER));
                    assertEquals(900, row.getCurrencyAmount());
                }
                assertTrue(rows.stream().anyMatch(other -> other.getType() == TransportType.CHARTER_SHIP
                    && other.getDestination().equals(GreatConchTransports.DOCK)
                    && other.getOrigin().distanceTo2D(row.getDestination()) <= 5
                    && other.getCurrencyAmount() == row.getCurrencyAmount()));
            }
        }
        assertEquals(20, inbound);
        assertEquals(20, outbound);
        List<Transport> rings = new ArrayList<>();
        for (Transport row : rows)
            if (row.getType() == TransportType.FAIRY_RING
                && GreatConchTransports.isRingConnection(row.getOrigin(), row.getDestination())) rings.add(row);
        assertTrue(rings.size() > 20);
        for (Transport ring : rings)
            assertEquals(QuestState.FINISHED, ring.getQuests().get(Quest.TROUBLED_TORTUGANS));
    }

    @Test public void runtimeChartersMatchCatalogAndDoNotLockExistingPorts() throws Exception
    {
        NetworkTransportSupport bridge = new NetworkTransportSupport(mock(Client.class), mock(EfficientWalkerConfig.class),
            getClass().getClassLoader(), Object.class, new Object(), "unused", mock(CatalogTeleportSupport.class));
        Method read = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "readCharterRows");
        read.setAccessible(true);
        List<?> runtime = (List<?>) read.invoke(bridge);
        List<Transport> catalog = catalog();
        int added = 0, unchanged = 0;
        for (Object row : runtime)
        {
            WorldPoint from = (WorldPoint) field(row, "origin"), to = (WorldPoint) field(row, "destination");
            List<?> quests = (List<?>) field(row, "quests");
            if (!GreatConchTransports.isCharterConnection(from, to))
            {
                unchanged++;
                assertFalse(quests.contains(Quest.TROUBLED_TORTUGANS), "Summer Shore must not lock mainland charters");
                continue;
            }
            added++;
            assertTrue(quests.contains(Quest.TROUBLED_TORTUGANS));
            Transport typed = catalog.stream().filter(t -> t.getType() == TransportType.CHARTER_SHIP
                && from.equals(t.getOrigin()) && to.equals(t.getDestination())).findFirst().orElseThrow(AssertionError::new);
            assertEquals(typed.getCurrencyAmount(), field(row, "fare"));
            assertEquals(typed.getDisplayInfo(), field(row, "displayInfo"));
            assertEquals(typed.getQuests().keySet(), new HashSet<>(quests));
        }
        assertEquals(40, added);
        assertTrue(unchanged > 100);
        Method shared = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "loadSharedCatalog");
        shared.setAccessible(true);
        for (Transport row : (List<Transport>) shared.invoke(bridge))
            assertFalse(row.getType() == TransportType.CHARTER_SHIP, "New charters must use the dedicated executor");
    }

    @Test public void islandDockAndRingConnectToTheReportedDestination() throws Exception
    {
        PathfinderConfig config = new PathfinderConfig(SplitFlagMap.fromResources(), new HashMap<>(), List.of(), null, null);
        Field cutoff = WalkerTestReflection.declaredField(PathfinderConfig.class, "calculationCutoffMillis");
        cutoff.setAccessible(true); cutoff.setLong(config, 2000L);
        WorldPoint target = new WorldPoint(3181, 2419, 0); // Live bank standing tile, 2026-10-01.
        Method plan = WalkerTestReflection.declaredMethod(Rs2PathApi.class, "planWithConfig", Rs2RouteRequest.class, PathfinderConfig.class);
        plan.setAccessible(true);
        for (WorldPoint from : List.of(GreatConchTransports.DOCK, GreatConchTransports.FAIRY_RING))
        {
            Rs2RouteResult route = (Rs2RouteResult) plan.invoke(null, Rs2RouteRequest.to(from, target), config);
            assertTrue(route.isTargetReached(0), "Collision route from " + from + ": " + route);
        }
    }

    @Test public void liveQuestGateRejectsPartialProgressAndAcceptsCompletion() throws Exception
    {
        NetworkTransportSupport bridge = new NetworkTransportSupport(mock(Client.class), mock(EfficientWalkerConfig.class),
            getClass().getClassLoader(), Object.class, new Object(), "unused", mock(CatalogTeleportSupport.class));
        Method read = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "readCharterRows");
        read.setAccessible(true);
        Object row = null;
        for (Object candidate : (List<?>) read.invoke(bridge))
            if (new WorldPoint(3038, 3192, 0).equals(field(candidate, "origin"))
                && GreatConchTransports.DOCK.equals(field(candidate, "destination"))) row = candidate;
        assertNotNull(row);
        Method gate = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "runtimeCharterRequirementsPass", row.getClass());
        gate.setAccessible(true);
        try (var player = mockStatic(Rs2Player.class))
        {
            for (QuestState state : List.of(QuestState.NOT_STARTED, QuestState.IN_PROGRESS, QuestState.FINISHED))
            {
                player.when(() -> Rs2Player.getQuestState(Quest.TROUBLED_TORTUGANS)).thenReturn(state);
                assertEquals(state == QuestState.FINISHED, gate.invoke(bridge, row));
                assertEquals(state == QuestState.FINISHED, GreatConchTransports.unlocked(state));
            }
        }
        assertTrue(GreatConchTransports.isRingConnection(GreatConchTransports.FAIRY_RING, GreatConchTransports.DOCK));
        assertTrue(GreatConchTransports.isRingConnection(GreatConchTransports.DOCK, GreatConchTransports.FAIRY_RING));
    }

    @Test public void confirmedDestinationActionMatchesAndUnrelatedDestinationsDoNot() throws Exception
    {
        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn("<col=ff9040>J: The Summer Shore</col>");
        when(widget.getActions()).thenReturn(new String[]{"The Summer Shore"});
        assertEquals(1, CharterMenu.actionIndex(widget, "The Summer Shore"));
        when(widget.getText()).thenReturn("I: The Pandemonium");
        when(widget.getActions()).thenReturn(new String[]{"The Pandemonium"});
        assertEquals(-1, CharterMenu.actionIndex(widget, "The Summer Shore"));
        assertTrue(GreatConchTransports.onArrivalDeck(new WorldPoint(3187, 2368, 1)));
        assertFalse(GreatConchTransports.onArrivalDeck(GreatConchTransports.DOCK));
        assertFalse(GreatConchTransports.onArrivalDeck(new WorldPoint(3038, 3189, 1)));
    }

    private static Object field(Object object, String name) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true); return field.get(object);
    }
}
