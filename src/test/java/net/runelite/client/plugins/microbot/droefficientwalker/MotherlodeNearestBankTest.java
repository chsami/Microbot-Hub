package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MotherlodeNearestBankTest
{
    private static final String SHADOW = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
    private static final WorldPoint UPPER_HOPPER = new WorldPoint(3754, 5677, 0);

    @Test void genericNearestBankSelectsMlmBankThroughTheDescendingLadder() throws Exception
    {
        List<WorldPoint> path = nearestBankPath(true);
        assertFalse(path.isEmpty(), "The real nearest-bank request must leave the upper hopper");
        assertEquals(UPPER_HOPPER, path.get(0));
        assertEquals(MotherlodeTransports.BANK_APPROACH, path.get(path.size() - 1));
        int upper = path.indexOf(MotherlodeTransports.UPPER_LADDER_APPROACH);
        assertTrue(upper >= 0, "Approach the upper ladder before leaving its collision component");
        assertEquals(MotherlodeTransports.LOWER_LADDER_APPROACH, path.get(upper + 1),
            "Cross floors through the selected transport, not an ordinary walking edge");
    }

    @Test void genericNearestBankCannotEscapeTheUpperLevelWithoutTheDescendingLadder() throws Exception
    {
        assertTrue(nearestBankPath(false).isEmpty(),
            "Bank enumeration and live collision must not fabricate a passage between MLM levels");
    }

    @SuppressWarnings("unchecked")
    private List<WorldPoint> nearestBankPath(boolean includeDescent) throws Exception
    {
        Constructor<?> loaderConstructor = Class.forName(EfficientWalker.class.getName()
            + "$NavigationEngineClassLoader").getDeclaredConstructor(ClassLoader.class);
        loaderConstructor.setAccessible(true);
        ClassLoader loader = (ClassLoader) loaderConstructor.newInstance(getClass().getClassLoader());
        Class<?> edgeClass = Class.forName(SHADOW + ".TransportCatalog$TransportEdge", true, loader);
        Class<?> plannerClass = Class.forName(SHADOW + ".CollisionRoutePlanner", true, loader);
        Class<?> engineClass = Class.forName(SHADOW + ".WalkerEngine", true, loader);
        Class<?> configClass = Class.forName(SHADOW + ".WalkerEngineConfig", true, loader);
        Class<?> requestClass = Class.forName(SHADOW + ".WalkerEngine$RouteRequest", true, loader);
        Class<?> captureClass = Class.forName(SHADOW + ".WalkerEngine$PlanningSnapshot", true, loader);

        Constructor<?> edgeConstructor = edgeClass.getDeclaredConstructor(WorldPoint.class, WorldPoint.class,
            String.class, String.class, int.class, List.class, List.class, List.class, List.class, List.class,
            boolean.class, int[].class);
        edgeConstructor.setAccessible(true);
        List<Object> edges = new ArrayList<>();
        for (Transport transport : MotherlodeTransports.create())
        {
            if (!includeDescent && MotherlodeTransports.isUpperLadderDescent(transport)) continue;
            edges.add(edgeConstructor.newInstance(transport.getOrigin(), transport.getDestination(),
                transport.getAction(), transport.getName(), transport.getObjectId(),
                List.of(), List.of(), List.of(), List.of(), List.of(), false, new int[0]));
        }
        Method build = WalkerTestReflection.declaredMethod(plannerClass, "create", List.class, Predicate.class);
        build.setAccessible(true);
        Object planner = build.invoke(null, edges, (Predicate<Object>) edge -> true);
        // All Boolean options, including teleports and banked supplies, are disabled.
        Object config = Proxy.newProxyInstance(loader, new Class<?>[]{configClass}, (proxy, method, args) ->
            method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 30 : null);

        Client client = mock(Client.class);
        Player player = mock(Player.class);
        WorldView view = mock(WorldView.class);
        Scene scene = mock(Scene.class);
        CollisionData collision = mock(CollisionData.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(client.getEnergy()).thenReturn(10_000);
        when(client.isClientThread()).thenReturn(true);
        when(player.getWorldLocation()).thenReturn(UPPER_HOPPER);
        when(player.getWorldView()).thenReturn(view);
        when(player.getLocalLocation()).thenReturn(new LocalPoint(50 * 128 + 64, 50 * 128 + 64));
        when(view.getId()).thenReturn(WorldView.TOPLEVEL);
        when(view.isTopLevel()).thenReturn(true);
        when(view.getSizeX()).thenReturn(104);
        when(view.getSizeY()).thenReturn(104);
        when(view.getBaseX()).thenReturn(UPPER_HOPPER.getX() - 50);
        when(view.getBaseY()).thenReturn(UPPER_HOPPER.getY() - 50);
        when(view.getScene()).thenReturn(scene);
        when(scene.getTiles()).thenReturn(new Tile[1][104][104]);
        when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});
        int[][] flags = new int[104][104];
        for (int[] column : flags) Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL);
        when(collision.getFlags()).thenReturn(flags);

        try (var dro = mockStatic(Microbot.class); var players = mockStatic(Rs2Player.class);
             var inventory = mockStatic(Rs2Inventory.class); var equipment = mockStatic(Rs2Equipment.class))
        {
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::isLoggedIn).thenReturn(true);
            players.when(Rs2Player::isMember).thenReturn(true);
            players.when(Rs2Player::isInMemberWorld).thenReturn(true);
            players.when(Rs2Player::getWorldLocation).thenReturn(UPPER_HOPPER);
            players.when(() -> Rs2Player.getQuestState(any(Quest.class))).thenReturn(QuestState.NOT_STARTED);

            // Keep the captured request intact while invoking the real solver synchronously.
            // Neither queued executor is drained, so this test performs planning only.
            List<Runnable> queued = new ArrayList<>();
            Executor queue = queued::add;
            Constructor<?> engineConstructor = engineClass.getDeclaredConstructor(Client.class, configClass,
                List.class, plannerClass, Executor.class, Executor.class);
            engineConstructor.setAccessible(true);
            Object engine = engineConstructor.newInstance(client, config, edges, planner, queue, queue);
            assertEquals(true, engineClass.getMethod("walkToNearestBank").invoke(engine));
            Object request = read(engine, "activeRequest");
            assertNull(read(request, "destination"), "A generic bank request begins without an explicit destination");
            assertEquals(true, read(request, "nearestBank"), "Retain the real nearest-bank request mode");

            Method capture = WalkerTestReflection.declaredMethod(engineClass, "captureAndStartPlanning", requestClass, Player.class, boolean.class);
            capture.setAccessible(true);
            assertEquals(true, capture.invoke(engine, request, player, false));
            Object snapshot = read(engine, "pendingPlanSnapshot");
            assertNotNull(snapshot, "The real capture must queue a global bank search");
            assertSame(request, read(snapshot, "request"));
            assertSame(request, read(engine, "activeRequest"));
            List<WorldPoint> candidates = (List<WorldPoint>) read(snapshot, "destinations");
            assertTrue(candidates.contains(BankLocation.MOTHERLOAD.getWorldPoint()));
            assertTrue(candidates.contains(BankLocation.GRAND_EXCHANGE.getWorldPoint()),
                "Keep real bank enumeration instead of supplying one explicit target");
            assertTrue(((Set<?>) read(snapshot, "edgeOverrides")).isEmpty(),
                "Fully blocked fake live flags must add no walking overrides");
            assertFalse(queued.isEmpty(), "The actual request must queue its planning worker");

            Method solve = WalkerTestReflection.declaredMethod(engineClass, "solveRouteSnapshot", captureClass);
            solve.setAccessible(true);
            Object result = solve.invoke(engine, snapshot);
            Object route = read(result, "globalRoute");
            assertNotNull(route, "Generic nearest-bank planning must produce a route result");
            assertSame(request, read(engine, "activeRequest"), "Planning must preserve the bank request's identity");
            return (List<WorldPoint>) read(route, "path");
        }
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException
    {
        Field field = WalkerTestReflection.declaredField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }
}
