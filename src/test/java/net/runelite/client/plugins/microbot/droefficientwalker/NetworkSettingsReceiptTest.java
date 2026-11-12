package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NetworkSettingsReceiptTest {
    private static final WorldPoint ORIGIN = new WorldPoint(3132, 3510, 0);
    private static final WorldPoint LANDING = new WorldPoint(3154, 3638, 0);
    private static final WorldPoint GOAL = new WorldPoint(3164, 3645, 0);

    @Test void disabledCanoeSettingPreservesTheIssuedActionUntilObservedLanding() throws Exception {
        for (boolean nativeEdgeCleared : new boolean[] {false, true}) {
            try (var f = new Fixture()) {
                CompletableFuture<Object> action = f.issuedAction();
                when(f.config.useCanoes()).thenReturn(false);
                if (nativeEdgeCleared) f.engine.selected = null;
                assertTrue(f.bridge.beforeEngineTick());
                assertTrue(f.bridge.hasPendingAction());
                assertSame(action, get(f.bridge, "actionFuture"));
                assertFalse(action.isCancelled());
                assertNull(f.bridge.consumeReplanTarget());

                f.position.set(LANDING);
                assertTrue(f.bridge.beforeEngineTick());
                assertFalse(f.bridge.hasPendingAction());
                assertEquals(GOAL, f.bridge.consumeReplanTarget());
                assertNull(f.bridge.consumeReplanTarget());
            }
        }
    }

    @Test void disabledCanoeSettingStillConsumesTheIssuedWorkersFailureReceipt() throws Exception {
        try (var f = new Fixture()) {
            CompletableFuture<Object> action = f.issuedAction();
            when(f.config.useCanoes()).thenReturn(false);
            action.complete(result("FAILED"));
            assertTrue(f.bridge.hasPendingAction(), "A completed worker still has an unconsumed result");
            assertTrue(f.bridge.beforeEngineTick());
            assertFalse(f.bridge.hasPendingAction());
            assertEquals(1, ((Set<?>) get(f.bridge, "failedSharedEdges")).size());
            assertEquals(GOAL, f.bridge.consumeReplanTarget());
            assertNull(f.bridge.consumeReplanTarget());
        }
    }

    @Test void disabledCanoeSettingCannotKeepAnUnresponsiveIssuedActionForever() throws Exception {
        try (var f = new Fixture()) {
            CompletableFuture<Object> action = f.issuedAction();
            when(f.config.useCanoes()).thenReturn(false);
            set(f.bridge, "actionStartedAt", System.currentTimeMillis() - 2_000_000L);
            assertTrue(f.bridge.beforeEngineTick());
            assertTrue(action.isCancelled());
            assertFalse(f.bridge.hasPendingAction());
            assertEquals(1, ((Set<?>) get(f.bridge, "failedSharedEdges")).size());
            assertEquals(GOAL, f.bridge.consumeReplanTarget());
        }
    }

    @Test void disabledCanoeSettingStillRejectsAnUnissuedSelectedEdge() throws Exception {
        try (var f = new Fixture()) {
            when(f.config.useCanoes()).thenReturn(false);
            assertFalse(f.bridge.hasPendingAction());
            assertTrue(f.bridge.beforeEngineTick());
            assertFalse(f.bridge.hasPendingAction());
            assertEquals(GOAL, f.bridge.consumeReplanTarget());
            assertNull(f.bridge.consumeReplanTarget());
            verifyNoInteractions(f.actions);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Microbot> dro = mockStatic(Microbot.class);
        final Client client = mock(Client.class);
        final EfficientWalkerConfig config = mock(EfficientWalkerConfig.class);
        final AtomicReference<WorldPoint> position = new AtomicReference<>(ORIGIN);
        final Engine engine = new Engine();
        final NetworkTransportSupport bridge;
        final Transport canoe = mock(Transport.class);
        final ExecutorService actions = mock(ExecutorService.class);

        Fixture() throws Exception {
            Player player = mock(Player.class);
            ClientThread thread = mock(ClientThread.class);
            when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenAnswer(call -> position.get());
            when(thread.invoke(any(Supplier.class))).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
            dro.when(Microbot::getClientThread).thenReturn(thread);
            when(config.useCanoes()).thenReturn(true);
            when(config.allowWildernessTravel()).thenReturn(true);
            when(canoe.getType()).thenReturn(TransportType.CANOE);
            when(canoe.getOrigin()).thenReturn(ORIGIN);
            when(canoe.getDestination()).thenReturn(LANDING);
            when(canoe.getAction()).thenReturn("Paddle Canoe");
            when(canoe.getName()).thenReturn("Canoe Station");
            bridge = new NetworkTransportSupport(client, config, getClass().getClassLoader(), Engine.class,
                    engine, "unused", mock(CatalogTeleportSupport.class));
            ((ExecutorService) get(bridge, "actionExecutor")).shutdownNow();
            set(bridge, "actionExecutor", actions);
            for (String name : new String[] {"Origin", "Destination", "Action", "Target"})
                set(bridge, "transport" + name + "Field", field(Edge.class, name.toLowerCase()));
            set(bridge, "activeTransportField", field(Engine.class, "selected"));
            set(bridge, "installed", true);
        }

        CompletableFuture<Object> issuedAction() throws Exception {
            CompletableFuture<Object> action = new CompletableFuture<>();
            set(bridge, "currentSharedTransport", canoe);
            set(bridge, "actionFuture", action);
            set(bridge, "actionStartedAt", System.currentTimeMillis());
            return action;
        }

        @Override public void close() { bridge.shutdown(); dro.close(); }
    }

    private static final class Edge {
        WorldPoint origin = ORIGIN, destination = LANDING;
        String action = "Paddle Canoe", target = "Canoe Station";
    }

    private static final class Engine {
        Edge selected = new Edge();
        public WorldPoint getDestination() { return GOAL; }
    }

    private static Object result(String name) throws Exception {
        return field(Class.forName(NetworkTransportSupport.class.getName() + "$ExecutionResult"), name).get(null);
    }

    private static Object get(Object target, String name) throws Exception { return field(target.getClass(), name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target.getClass(), name).set(target, value); }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = WalkerTestReflection.declaredField(owner, name);
        field.setAccessible(true);
        return field;
    }
}
