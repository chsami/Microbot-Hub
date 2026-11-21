package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real source planner and normal maintained transport merge, without live collision overrides. */
final class RecordedRoutePlanner implements AutoCloseable
{
    private static final String SHADOW = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
    private static final WorldPoint REPORTED_UPPER_HOPPER = new WorldPoint(3754, 5677, 0);
    final MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
    final MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
    final List<Object> edges;
    final List<Object> nativeEdges;
    final NetworkTransportSupport bridge;
    final Class<?> plannerClass;
    final Method build, plan, usable;
    final Object planner;

    @SuppressWarnings("unchecked") RecordedRoutePlanner() throws Exception
    {
        player.when(Rs2Player::getWorldLocation).thenReturn(REPORTED_UPPER_HOPPER);
        player.when(() -> Rs2Player.getRealSkillLevel(any(Skill.class))).thenReturn(41);
        player.when(() -> Rs2Player.getQuestState(any(Quest.class))).thenReturn(QuestState.NOT_STARTED);
        NetworkGraphRegressionTest fixture = new NetworkGraphRegressionTest();
        fixture.setup();
        ClassLoader loader = (ClassLoader) read(fixture, "loader");
        bridge = (NetworkTransportSupport) read(fixture, "bridge");
        Client client = (Client) read(bridge, "client");
        Player actor = mock(Player.class);
        when(actor.getWorldLocation()).thenReturn(REPORTED_UPPER_HOPPER);
        when(client.getLocalPlayer()).thenReturn(actor);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        microbot.when(Microbot::getClient).thenReturn(client);
        ClientThread thread = mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        microbot.when(Microbot::getClientThread).thenReturn(thread);
        Class<?> edgeClass = (Class<?>) read(fixture, "transportClass");
        for (String[] binding : List.of(new String[]{"transportOriginField", "origin"},
            new String[]{"transportDestinationField", "destination"}, new String[]{"transportActionField", "action"},
            new String[]{"transportTargetField", "target"}, new String[]{"transportObjectIdField", "objectId"}))
        {
            Field field = WalkerTestReflection.declaredField(edgeClass, binding[1]);
            field.setAccessible(true);
            set(bridge, binding[0], field);
        }
        // Exercise the production merge and requirements for all local mine links.
        // Keep the native global object graph for the onward surface journey.
        List<Transport> local = new ArrayList<>();
        for (Transport source : (List<Transport>) read(fixture, "catalog"))
        {
            WorldPoint from = source.getOrigin();
            if (source.getType() == TransportType.TRANSPORT && from != null
                && (MotherlodeTransports.inMine(from)
                    || from.getX() >= 3000 && from.getX() <= 3070
                    && from.getY() >= 9700 && from.getY() <= 9860
                    || MotherlodeTransports.replaces(source)
                    || inPrifddinas(from) || inPrifddinas(source.getDestination())
                    || from.getX() >= 3176 && from.getX() < 3280 && from.getY() >= 12392 && from.getY() < 12496)) local.add(source);
        }
        assertTrue(local.stream().anyMatch(source -> source.getObjectId() == 23969),
            "Load the actual Falador staircase exit from the current catalogue");
        set(bridge, "sharedCatalog", local);
        nativeEdges = new ArrayList<>((List<?>) method(Class.forName(SHADOW + ".TransportCatalog", true, loader), "loadAllTransports").invoke(null));
        usable = method(edgeClass, "meetsRequirements", ToIntFunction.class, BiPredicate.class,
            IntUnaryOperator.class, IntUnaryOperator.class, Predicate.class);
        edges = eligibleEdges(false);
        plannerClass = Class.forName(SHADOW + ".CollisionRoutePlanner", true, loader);
        build = method(plannerClass, "create", List.class, Predicate.class);
        plan = method(plannerClass, "planRoute", WorldPoint.class, Collection.class, Set.class, Set.class, Set.class);
        planner = build.invoke(null, edges, (Predicate<Object>) edge -> true);
    }

    List<Object> eligibleEdges(boolean songOfTheElves) throws Exception
    {
        ToIntFunction<Skill> skills = skill -> 41;
        BiPredicate<String, Integer> items = (name, quantity) -> false;
        IntUnaryOperator variables = id -> 0;
        Predicate<Quest> quests = quest -> songOfTheElves && quest == Quest.SONG_OF_THE_ELVES;
        player.when(() -> Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES))
            .thenReturn(songOfTheElves ? QuestState.FINISHED : QuestState.NOT_STARTED);
        List<Object> merged = new ArrayList<>(nativeEdges);
        ((Map<?, ?>) read(bridge, "sharedCatalogEdges")).clear();
        method(NetworkTransportSupport.class, "addSharedCatalogEdges", List.class).invoke(bridge, merged);
        List<Object> available = new ArrayList<>();
        for (Object edge : merged)
            if ((boolean) usable.invoke(edge, skills, items, variables, variables, quests)) available.add(edge);
        player.when(() -> Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES)).thenReturn(QuestState.NOT_STARTED);
        return available;
    }

    private static boolean inPrifddinas(WorldPoint point)
    {
        return point != null && point.getX() >= 3200 && point.getX() < 3456
            && point.getY() >= 5952 && point.getY() < 6272;
    }

    List<Object> without(WorldPoint from, WorldPoint to) throws Exception
    {
        List<Object> result = new ArrayList<>();
        for (Object edge : edges)
            if (!from.equals(read(edge, "origin")) || !to.equals(read(edge, "destination"))) result.add(edge);
        assertTrue(result.size() < edges.size(), "Remove a real registered transport");
        return result;
    }

    @SuppressWarnings("unchecked") List<WorldPoint> path(WorldPoint from, WorldPoint to, List<Object> routes)
        throws Exception
    {
        Object activePlanner = routes == edges ? planner
            : build.invoke(null, routes, (Predicate<Object>) edge -> true);
        Object result = plan.invoke(activePlanner, from, List.of(to), Set.of(), Set.of(), new HashSet<>(routes));
        return (List<WorldPoint>) read(result, "path");
    }

    @Override public void close() { player.close(); microbot.close(); }
    private static Method method(Class<?> owner, String name, Class<?>... types) throws Exception
    { Method result = WalkerTestReflection.declaredMethod(owner, name, types); result.setAccessible(true); return result; }
    private static Object read(Object owner, String name) throws Exception
    { Field field = WalkerTestReflection.declaredField(owner.getClass(), name); field.setAccessible(true); return field.get(owner); }
    private static void set(Object owner, String name, Object value) throws Exception
    { Field field = WalkerTestReflection.declaredField(owner.getClass(), name); field.setAccessible(true); field.set(owner, value); }
}
