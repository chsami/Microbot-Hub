package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkerSettingsReplanTest {
    private static final WorldPoint TARGET = new WorldPoint(1238, 3729, 0);


    @Test void dispatchedReceiptsAndPreparationRemainOwnedUntilSettledThenRefresh() throws Exception {
        Fixture f = new Fixture();
        f.walker.routeSettingsChanged();
        for (String field : new String[] {"transportAttempt", "teleportAttempt", "pendingDoor"}) {
            set(f.engine, field, new Object());
            f.assertDeferred();
            set(f.engine, field, null);
        }
        CompletableFuture<Void> action = new CompletableFuture<>();
        f.engine.pendingPlanTask = action;
        f.assertDeferred();
        action.complete(null);
        when(f.network.hasPendingAction()).thenReturn(true);
        f.assertDeferred();
        when(f.network.hasPendingAction()).thenReturn(false);
        when(f.spirit.hasPendingAction()).thenReturn(true);
        f.assertDeferred();
        when(f.spirit.hasPendingAction()).thenReturn(false);
        when(f.fairy.isTracking()).thenReturn(true);
        f.assertDeferred();
        when(f.fairy.isTracking()).thenReturn(false);
        when((Boolean) call(f.bank, "isBusy")).thenReturn(true);
        f.assertDeferred();
        when((Boolean) call(f.bank, "isBusy")).thenReturn(false);
        set(f.walker, "apeAtollTabletTarget", TARGET);
        f.assertDeferred();
        set(f.walker, "apeAtollTabletTarget", null);
        CompletableFuture<Void> hold = new CompletableFuture<>();
        set(f.walker, "greegreeHoldAction", hold);
        f.assertDeferred();
        hold.complete(null);
        assertTrue(f.refresh());
        assertEquals(1, f.engine.replans);
    }



    public static class Engine {
        Object transportAttempt, teleportAttempt, pendingDoor, pendingPlanTask;
        EfficientWalker.Status status = EfficientWalker.Status.WALKING;
        WorldPoint destination = TARGET;
        int cancels, replans;
        public EfficientWalker.Status getStatus() { return status; }
        public WorldPoint getDestination() { return destination; }
        public void cancel() { cancels++; status = EfficientWalker.Status.IDLE; destination = null; }
        public boolean startRouteTo(WorldPoint target) { replans++; destination = target; status = EfficientWalker.Status.WALKING; return true; }
    }

    private static class Fixture {
        final EfficientWalker walker = mock(EfficientWalker.class, CALLS_REAL_METHODS);
        final Engine engine = new Engine();
        final WalkerRequestCancellation requests = new WalkerRequestCancellation();
        final NetworkTransportSupport network = mock(NetworkTransportSupport.class);
        final SpiritTreeSupport spirit = mock(SpiritTreeSupport.class);
        final FairyRingEquipmentRestore fairy = mock(FairyRingEquipmentRestore.class);
        final Object bank;

        Fixture() throws Exception {
            Constructor<?> loaderCtor = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader")
                    .getDeclaredConstructor(ClassLoader.class);
            loaderCtor.setAccessible(true);
            set(walker, "engineLoader", loaderCtor.newInstance(getClass().getClassLoader()));
            set(walker, "client", mock(Client.class));
            set(walker, "config", mock(EfficientWalkerConfig.class));
            set(walker, "engine", engine);
            set(walker, "engineClass", Engine.class);
            set(walker, "requestCancellation", requests);
            set(walker, "requestOwnership", new WalkerRequestOwnership());
            set(walker, "routeSubmission", TestRouteSubmissions.immediate());
            set(walker, "routeSettings", new WalkerRouteSettings());
            set(walker, "wildernessTravelResume", new WildernessTravelResume());
            set(walker, "networkTransportSupport", network);
            set(walker, "spiritTreeSupport", spirit);
            set(walker, "fairyRingEquipmentRestore", fairy);
            set(walker, "catalogTeleportSupport", mock(CatalogTeleportSupport.class));
            bank = mock(WalkerTestReflection.declaredField(EfficientWalker.class, "bankTeleportBootstrap").getType());
            set(walker, "bankTeleportBootstrap", bank);
            for (String name : new String[] {"finalTileWatchdog", "nearbyDestinationRecovery"}) {
                Field field = WalkerTestReflection.declaredField(EfficientWalker.class, name);
                set(walker, name, mock(field.getType()));
            }
        }

        boolean refresh() throws Exception {
            return (Boolean) invokeWithSnapshot("refreshRouteSettingsBeforeTick");
        }

        Object invokeWithSnapshot(String method) throws Exception {
            try (var equipment = mockStatic(TravelEquipment.class); var poh = mockStatic(MaxPohSupport.class);
                 var player = mockStatic(Rs2Player.class)) {
                equipment.when(TravelEquipment::planningScope).thenReturn((TravelEquipment.Scope) () -> {});
                return call(walker, method);
            }
        }

        void assertDeferred() throws Exception {
            assertFalse(refresh());
            assertTrue(walker.hasPendingRouteSettings());
            assertEquals(0, engine.cancels);
            assertEquals(0, engine.replans);
        }
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name); field.setAccessible(true); return field.get(object);
    }
    private static Object call(Object object, String name) throws Exception {
        Method method = WalkerTestReflection.declaredMethod(object.getClass(), name); method.setAccessible(true); return method.invoke(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object instanceof EfficientWalker ? WalkerTestReflection.declaredField(EfficientWalker.class, name)
                : WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true); field.set(object, value);
    }
}
