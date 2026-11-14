package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KourendMinecartMenuTest {
    @Test void recordingOriginOpens947AndSelectsHosidiusSouth() throws Exception {
        try (Fixture f = new Fixture(false)) {
            assertEquals("SUCCESS", f.execute(false));
            f.objects.verify(() -> SceneObjects.interact((TileObject) f.cart, "Travel"), times(1));
            f.widgets.verify(() -> Rs2Widget.clickWidget(f.row), times(1));
        }
    }
    @Test void existing947MenuNeverClicksCartAgainIncludingSharedCatalogPath() throws Exception {
        for (boolean shared : List.of(false, true)) try (Fixture f = new Fixture(true)) {
            assertEquals("SUCCESS", f.execute(shared));
            f.objects.verifyNoInteractions();
            f.widgets.verify(() -> Rs2Widget.clickWidget(f.row), times(1));
        }
    }
    @Test void delayedRowLayoutAndLabelWithoutPrefixAreAccepted() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.widgets.when(() -> Rs2Widget.findWidget("6: Hosidius South", List.of(f.root), false)).thenReturn(null);
            when(f.row.getBounds()).thenReturn(new Rectangle(), new Rectangle(20,20,150,20));
            assertEquals("SUCCESS", f.execute(false));
            f.objects.verifyNoInteractions();
        }
    }
    @Test void selectionIsNotArrivalAndCancelledLandingDoesNotSucceed() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.widgets.when(() -> Rs2Widget.clickWidget(f.row)).thenAnswer(c -> {
                Thread.currentThread().interrupt(); return true;
            });
            f.location.set(f.origin);
            try { assertEquals("FAILED", f.execute(false)); }
            finally { Thread.interrupted(); }
            assertEquals(f.origin, f.location.get());
        }
    }
    private static final class Fixture implements AutoCloseable {
        final WorldPoint origin = new WorldPoint(1572,3466,0), landing = new WorldPoint(1804,3479,0);
        final AtomicReference<WorldPoint> location = new AtomicReference<>(origin);
        final AtomicBoolean open;
        final Client client = mock(Client.class);
        final GameObject cart = mock(GameObject.class);
        final Widget root = mock(Widget.class), row = mock(Widget.class);
        final MockedStatic<Microbot> bot = mockStatic(Microbot.class);
        final MockedStatic<Rs2Widget> widgets = mockStatic(Rs2Widget.class);
        final MockedStatic<SceneObjects> objects = mockStatic(SceneObjects.class);
        final NetworkTransportSupport bridge;
        Fixture(boolean initiallyOpen) {
            open = new AtomicBoolean(initiallyOpen);
            var player = mock(Player.class); var thread = mock(ClientThread.class);
            when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenAnswer(c -> location.get());
            when(thread.invoke(any(Supplier.class))).thenAnswer(c -> ((Supplier<?>) c.getArgument(0)).get());
            when(thread.runOnClientThreadOptional(any())).thenAnswer(c -> Optional.ofNullable(((Callable<?>) c.getArgument(0)).call()));
            bot.when(Microbot::getClient).thenReturn(client); bot.when(Microbot::getClientThread).thenReturn(thread);
            widgets.when(() -> Rs2Widget.isHidden(187,3)).thenReturn(true);
            widgets.when(() -> Rs2Widget.isHidden(947,9)).thenAnswer(c -> !open.get());
            widgets.when(() -> Rs2Widget.getWidget(947,9)).thenReturn(root);
            widgets.when(() -> Rs2Widget.findWidget("6: Hosidius South",List.of(root),false)).thenReturn(row);
            widgets.when(() -> Rs2Widget.findWidget("Hosidius South",List.of(root),false)).thenReturn(row);
            when(row.getBounds()).thenReturn(new Rectangle(20,20,150,20));
            widgets.when(() -> Rs2Widget.clickWidget(row)).thenAnswer(c -> {location.set(landing);return true;});
            when(cart.getId()).thenReturn(28835);when(cart.getWorldLocation()).thenReturn(origin);
            objects.when(() -> SceneObjects.<TileObject>getAll(any(),eq(origin),eq(5))).thenReturn(List.of(cart));
            objects.when(() -> SceneObjects.interact((TileObject) cart,"Travel")).thenAnswer(c -> {open.set(true);return true;});
            bridge = new NetworkTransportSupport(client,new EfficientWalkerConfig(){},getClass().getClassLoader(),Object.class,new Object(),
                "net.runelite.client.plugins.microbot.droefficientwalker.engine",mock(CatalogTeleportSupport.class));
        }
        String execute(boolean shared) throws Exception {
            if (shared) {
                Transport transport = mock(Transport.class);
                when(transport.getType()).thenReturn(TransportType.MINECART);when(transport.getObjectId()).thenReturn(28835);
                when(transport.getOrigin()).thenReturn(origin);when(transport.getDestination()).thenReturn(landing);
                when(transport.getDisplayInfo()).thenReturn("6: Hosidius South");
                bot.when(() -> Microbot.getVarbitValue(7796)).thenReturn(11);
                var method = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "executeSharedTransport",Transport.class);
                method.setAccessible(true);return method.invoke(bridge,transport).toString();
            }
            Class<?> kind = Class.forName(NetworkTransportSupport.class.getName()+"$EdgeType");
            Class<?> edge = Class.forName(NetworkTransportSupport.class.getName()+"$NetworkEdge");
            Constructor<?> constructor = edge.getDeclaredConstructor(kind,WorldPoint.class,WorldPoint.class,String.class,String.class,int.class,int.class);
            constructor.setAccessible(true);
            Object route = constructor.newInstance(Enum.valueOf((Class)kind,"MINECART"),origin,landing,"6: Hosidius South","Minecart",28835,0);
            var method = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "executeMinecart",edge);
            method.setAccessible(true);return method.invoke(bridge,route).toString();
        }
        public void close() {bridge.shutdown();objects.close();widgets.close();bot.close();}
    }
}
