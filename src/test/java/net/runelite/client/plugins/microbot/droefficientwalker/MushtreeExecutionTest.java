package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MushtreeExecutionTest
{
    // Sticky Swamp -> House matches the user's recorded 30924 Use, widget 608:4,
    // and observed arrival at (3764, 3879, 1). Other origins retain their established anchors.
    private static final WorldPoint[] ORIGINS = {
        new WorldPoint(3676, 3755, 0), new WorldPoint(3764, 3880, 1),
        new WorldPoint(3757, 3756, 0), new WorldPoint(3676, 3871, 0)};
    private static final WorldPoint[] OBJECTS = {
        new WorldPoint(3677, 3755, 0), new WorldPoint(3764, 3880, 1),
        new WorldPoint(3758, 3756, 0), new WorldPoint(3674, 3871, 0)};
    private static final WorldPoint[] LANDINGS = {
        new WorldPoint(3764, 3879, 1), new WorldPoint(3760, 3758, 0),
        new WorldPoint(3676, 3871, 0), new WorldPoint(3676, 3755, 0)};
    private static final String[] LABELS = {
        "House on the Hill", "Verdant Valley", "Mushroom Meadow", "Sticky Swamp"};





    @Test void sharedCatalogMushtreesUseTheSameOpenMenuExecutor() throws Exception
    {
        try (Fixture f = new Fixture(0)) {
            f.menuOpen.set(true);
            when(f.mouse.click(f.bounds)).thenAnswer(call -> { f.location.set(LANDINGS[0]); return f.mouse; });
            var transport = mock(net.runelite.client.plugins.microbot.shortestpath.Transport.class);
            when(transport.getType()).thenReturn(net.runelite.client.plugins.microbot.shortestpath.TransportType.MAGIC_MUSHTREE);
            when(transport.getOrigin()).thenReturn(ORIGINS[0]);
            when(transport.getDestination()).thenReturn(LANDINGS[0]);
            var execute = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "executeSharedTransport",
                net.runelite.client.plugins.microbot.shortestpath.Transport.class);
            execute.setAccessible(true);
            assertEquals("SUCCESS", execute.invoke(f.bridge, transport).toString());
            f.objects.verifyNoInteractions(); verify(f.mouse, times(1)).click(f.bounds);
        }
    }



    private static final class Fixture implements AutoCloseable
    {
        final AtomicBoolean menuOpen = new AtomicBoolean();
        final int index;
        final Client client = mock(Client.class);
        final GameObject tree = mock(GameObject.class);
        final Widget destination = mock(Widget.class);
        final Mouse mouse = mock(Mouse.class);
        final Rectangle bounds = new Rectangle(100, 100, 150, 30);
        final AtomicReference<WorldPoint> location;
        final MockedStatic<Microbot> micro = mockStatic(Microbot.class);
        final MockedStatic<SceneObjects> objects = mockStatic(SceneObjects.class);
        final NetworkTransportSupport bridge;
        final Object edge;
        final Method execute;

        Fixture(int index) throws Exception {
            this.index = index;
            location = new AtomicReference<>(ORIGINS[index]);
            int objectId = index == 1 ? 30920 : 30924;
            when(tree.getId()).thenReturn(objectId);
            when(tree.getWorldLocation()).thenReturn(OBJECTS[index]);
            objects.when(() -> SceneObjects.<TileObject>getAll(any(), eq(OBJECTS[index]), eq(index == 1 ? 8 : 2)))
                .thenAnswer(call -> ((Predicate<TileObject>) call.getArgument(0)).test(tree) ? List.of(tree) : List.of());
            objects.when(() -> SceneObjects.interact((TileObject) tree, "Use")).thenAnswer(call -> {
                menuOpen.set(true);return true;
            });
            Player player = mock(Player.class);
            when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenAnswer(call -> location.get());
            Widget root = mock(Widget.class);
            when(root.getName()).thenReturn("");
            when(root.isHidden()).thenAnswer(call -> !menuOpen.get());
            when(destination.getName()).thenReturn("");
            when(root.getText()).thenReturn("Mycelium Transportation System");
            when(root.getChildren()).thenReturn(new Widget[]{destination});
            when(destination.getId()).thenReturn((608 << 16) | (index == 0 ? 4 : 8));
            when(destination.getText()).thenReturn("<col=ffffff>" + LABELS[index] + "</col>");
            when(destination.getBounds()).thenReturn(bounds);
            when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
            ClientThread thread = mock(ClientThread.class);
            when(thread.invoke(any(Supplier.class))).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
            micro.when(Microbot::getClient).thenReturn(client);
            micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(Microbot::getMouse).thenReturn(mouse);
            bridge = new NetworkTransportSupport(client, new EfficientWalkerConfig() { @Override public boolean enableDebugging() { return true; } },
                getClass().getClassLoader(), Object.class, new Object(),
                "net.runelite.client.plugins.microbot.droefficientwalker.engine", mock(CatalogTeleportSupport.class));
            Class<?> type = Class.forName(NetworkTransportSupport.class.getName() + "$EdgeType");
            Class<?> edgeType = Class.forName(NetworkTransportSupport.class.getName() + "$NetworkEdge");
            Constructor<?> constructor = edgeType.getDeclaredConstructor(type, WorldPoint.class,
                WorldPoint.class, String.class, String.class, int.class, int.class);
            constructor.setAccessible(true);
            edge = constructor.newInstance(Enum.valueOf((Class) type, "MUSHTREE"), ORIGINS[index],
                LANDINGS[index], LABELS[index], "Magic Mushtree", objectId, 0);
            execute = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "executeMagicMushtree", edgeType);
            execute.setAccessible(true);
        }

        String execute() throws Exception { return execute.invoke(bridge, edge).toString(); }

        @Override public void close() {
            objects.close();
            micro.close();
        }
    }
}
