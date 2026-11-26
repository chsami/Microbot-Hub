package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TransportNpcApproachTest
{
    private static final WorldPoint BILL_TEACH_DOCK = new WorldPoint(3709, 3496, 0);
    private static final WorldPoint MOS_LE_HARMLESS = new WorldPoint(3684, 2953, 0);
    private static final WorldPoint APPROACH = new WorldPoint(3699, 3496, 0);

    @Test public void npcBackedTransportsWaitForTheirNpcWhileStillApproaching()
    {
        for (TransportType type : new TransportType[]{TransportType.SHIP, TransportType.BOAT, TransportType.NPC})
        {
            assertTrue(NetworkTransportSupport.awaitTransportNpc(type, APPROACH, BILL_TEACH_DOCK, () -> false), type.name());
            assertFalse(NetworkTransportSupport.awaitTransportNpc(type, APPROACH, BILL_TEACH_DOCK, () -> true), type.name());
        }
    }

    @Test public void theTransportStartsBesideItsOriginEvenWithoutTheNpcSoARealFailureStillCounts()
    {
        WorldPoint beside = new WorldPoint(3705, 3496, 0);
        assertFalse(NetworkTransportSupport.awaitTransportNpc(TransportType.SHIP, beside, BILL_TEACH_DOCK, () -> false));
        assertFalse(NetworkTransportSupport.awaitTransportNpc(TransportType.SHIP, BILL_TEACH_DOCK, BILL_TEACH_DOCK, () -> false));
        assertFalse(NetworkTransportSupport.awaitTransportNpc(TransportType.SHIP, APPROACH, null, () -> false));
    }

    @Test public void otherTransportsNeverLookForAnNpc()
    {
        for (TransportType type : new TransportType[]{TransportType.TRANSPORT, TransportType.CANOE,
            TransportType.CHARTER_SHIP, TransportType.TELEPORTATION_SPELL})
        {
            assertFalse(NetworkTransportSupport.awaitTransportNpc(type, APPROACH, BILL_TEACH_DOCK, () -> {
                throw new AssertionError("target lookup for " + type);
            }), type.name());
        }
    }

    @Test public void anUnloadedShipNpcDoesNotStartOrSuppressTheEdgeWhileThePlayerWalksToIt() throws Exception
    {
        NetworkTransportSupport bridge = new NetworkTransportSupport(mock(Client.class), mock(EfficientWalkerConfig.class),
            getClass().getClassLoader(), Engine.class, new Engine(), "unused", mock(CatalogTeleportSupport.class));
        Transport ship = mock(Transport.class);
        when(ship.getType()).thenReturn(TransportType.SHIP);
        when(ship.getOrigin()).thenReturn(BILL_TEACH_DOCK);
        when(ship.getDestination()).thenReturn(MOS_LE_HARMLESS);
        when(ship.getObjectId()).thenReturn(4016);
        when(ship.getName()).thenReturn("Bill Teach");
        when(ship.getAction()).thenReturn("Travel");
        Client client = (Client) field(NetworkTransportSupport.class, "client").get(bridge);
        Player player = mock(Player.class);
        when(player.getWorldLocation()).thenReturn(APPROACH);
        when(client.getLocalPlayer()).thenReturn(player);
        ClientThread thread = mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
        HashMap<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(BILL_TEACH_DOCK, 10);
        @SuppressWarnings("unchecked")
        Set<String> suppressed = (Set<String>) field(NetworkTransportSupport.class, "failedSharedEdges").get(bridge);
        Method tick = method(NetworkTransportSupport.class, "beforeSharedTransportTick", Transport.class);
        try (MockedStatic<Microbot> micro = mockStatic(Microbot.class);
             MockedStatic<Rs2Tile> tiles = mockStatic(Rs2Tile.class);
             MockedStatic<SceneObjects> objects = mockStatic(SceneObjects.class);
             MockedStatic<Rs2Npc> npcs = mockStatic(Rs2Npc.class))
        {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            tiles.when(() -> Rs2Tile.getReachableTilesFromTile(APPROACH, 24)).thenReturn(reachable);
            objects.when(() -> SceneObjects.getAll(any(), any(WorldPoint.class), anyInt())).thenReturn(Collections.emptyList());
            npcs.when(() -> Rs2Npc.getNpc(anyInt())).thenReturn(null);
            npcs.when(() -> Rs2Npc.getNpc(anyString())).thenReturn(null);

            assertFalse((Boolean) tick.invoke(bridge, ship));
            assertNull(field(NetworkTransportSupport.class, "actionFuture").get(bridge));
            assertTrue(suppressed.isEmpty());
            assertNull(bridge.consumeReplanTarget());
            npcs.verify(() -> Rs2Npc.getNpc(4016));
            npcs.verify(() -> Rs2Npc.getNpc("Bill Teach"));
        }
    }

    private static final class Edge
    {
        WorldPoint origin = BILL_TEACH_DOCK, destination = MOS_LE_HARMLESS;
        String action = "Travel", target = "Bill Teach";
    }
    private static final class Engine
    {
        Edge selected = new Edge();
        public WorldPoint getDestination() { return new WorldPoint(3740, 9373, 0); }
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
