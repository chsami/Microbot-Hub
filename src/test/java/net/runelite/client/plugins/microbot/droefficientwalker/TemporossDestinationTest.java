package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.Keybind;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.util.HotkeyListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemporossDestinationTest
{
    private static final WorldPoint NORTH = new WorldPoint(3295, 3159, 0);
    private static final WorldPoint SOUTH = new WorldPoint(3295, 3157, 0);
    private static final WorldPoint FERRY = new WorldPoint(3272, 3143, 0);
    private static final String SHADOW = "net.runelite.client.plugins.microbot.droefficientwalker.engine";

    @Test void namedDestinationIsSharedAndOnlyExplicitButtonOrHotkeyStartsTravel() throws Exception
    {
        EfficientWalkerConfig defaults = new EfficientWalkerConfig() {};
        assertEquals(WalkerDestination.NONE, defaults.namedDestination());
        assertEquals(Keybind.NOT_SET, defaults.walkToNamedDestinationHotkey());
        assertEquals(new WorldPoint(3156, 2836, 0), TemporossAccess.LOBBY_BANK);
        assertSame(TemporossAccess.LOBBY_BANK, WalkerDestination.TEMPOROSS.getWorldPoint());
        assertEquals("Tempoross (Ruins of Unkah)", WalkerDestination.TEMPOROSS.toString());
        NamedTravel fixture = new NamedTravel();
        when(fixture.config.namedDestination()).thenReturn(WalkerDestination.TEMPOROSS);
        when(fixture.walker.walkTo(TemporossAccess.LOBBY_BANK)).thenReturn(true);
        fixture.change(EfficientWalkerConfig.GROUP, "namedDestination");
        fixture.change("unrelated", "walkToNamedDestination");
        fixture.process();
        verifyNoInteractions(fixture.walker);
        fixture.change(EfficientWalkerConfig.GROUP, "walkToNamedDestination");
        verifyNoInteractions(fixture.walker);
        fixture.process();
        fixture.process();
        verify(fixture.walker).walkTo(TemporossAccess.LOBBY_BANK);
        verifyNoMoreInteractions(fixture.walker);
        clearInvocations(fixture.walker);
        ((HotkeyListener)field(fixture.plugin, "destinationHotkey")).hotkeyPressed();
        fixture.process();
        verify(fixture.walker).walkTo(TemporossAccess.LOBBY_BANK);
        verifyNoMoreInteractions(fixture.walker);
    }

    @Test void shortcutAdmissionRequiresExactMaintainedTypeActionIdAndEndpoints()
    {
        assertTrue(TemporossAccess.isArenaShortcut(shortcut(NORTH, SOUTH, "Enter", 33348, TransportType.AGILITY_SHORTCUT)));
        assertTrue(TemporossAccess.isArenaShortcut(shortcut(SOUTH, NORTH, "Climb", 33344, TransportType.AGILITY_SHORTCUT)));
        assertFalse(TemporossAccess.isArenaShortcut(null));
        assertFalse(TemporossAccess.isArenaShortcut(shortcut(NORTH, SOUTH, "Enter", 33348, TransportType.TRANSPORT)));
        assertFalse(TemporossAccess.isArenaShortcut(shortcut(NORTH, SOUTH, "Climb", 33348, TransportType.AGILITY_SHORTCUT)));
        assertFalse(TemporossAccess.isArenaShortcut(shortcut(NORTH, SOUTH, "Enter", 33344, TransportType.AGILITY_SHORTCUT)));
        assertFalse(TemporossAccess.isArenaShortcut(shortcut(SOUTH, NORTH, "Enter", 33348, TransportType.AGILITY_SHORTCUT)));
        assertFalse(TemporossAccess.isArenaShortcut(shortcut(new WorldPoint(3295, 3159, 1), SOUTH,
            "Enter", 33348, TransportType.AGILITY_SHORTCUT)));
    }

    @Test void maintainedShortcutsAndFerriesKeepTheirExistingRequirementsAndExecutors() throws Exception
    {
        NetworkGraphRegressionTest fixture = graphFixture();
        List<Transport> catalog = catalog(fixture);
        List<Transport> shortcuts = catalog.stream().filter(TemporossAccess::isArenaShortcut).collect(java.util.stream.Collectors.toList());
        assertEquals(2, shortcuts.size());
        Method convert = (Method)field(fixture, "convert");
        Object bridge = field(fixture, "bridge");
        Class<?> edgeType = (Class<?>)field(fixture, "transportClass");
        Method usable = method(edgeType, "meetsRequirements", ToIntFunction.class, BiPredicate.class,
            IntUnaryOperator.class, IntUnaryOperator.class, Predicate.class);
        BiPredicate<String, Integer> items = (name, count) -> true;
        IntUnaryOperator vars = id -> 0;
        for (Transport source : shortcuts)
        {
            assertEquals(70, source.getSkillLevels()[Skill.AGILITY.ordinal()]);
            assertEquals(QuestState.FINISHED, source.getQuests().get(Quest.PRINCE_ALI_RESCUE));
            assertEquals(TransportExecutionRegistry.Executor.OBJECT, TransportExecutionRegistry.executorFor(source).orElseThrow());
            Object edge = convert.invoke(bridge, source, NORTH);
            assertNotNull(edge);
            assertEquals(false, usable.invoke(edge, (ToIntFunction<Skill>)skill -> skill == Skill.AGILITY ? 69 : 99,
                items, vars, vars, (Predicate<Quest>)quest -> true));
            assertEquals(false, usable.invoke(edge, (ToIntFunction<Skill>)skill -> 99,
                items, vars, vars, (Predicate<Quest>)quest -> quest != Quest.PRINCE_ALI_RESCUE));
            assertEquals(true, usable.invoke(edge, (ToIntFunction<Skill>)skill -> 99,
                items, vars, vars, (Predicate<Quest>)quest -> true));
        }
        List<Transport> ferries = catalog.stream().filter(t -> t.getType() == TransportType.BOAT
            && t.getObjectId() == 41311).collect(java.util.stream.Collectors.toList());
        assertEquals(4, ferries.size(), "Reuse the four maintained forward/reverse rows without duplicates");
        assertEquals(Set.of(
            "3271,3144->3148,2843", "3272,3143->3148,2843",
            "3148,2843->3272,3143", "3148,2842->3272,3143"),
            ferries.stream().map(t -> xy(t.getOrigin()) + "->" + xy(t.getDestination()))
                .collect(java.util.stream.Collectors.toSet()));
        for (Transport ferry : ferries)
        {
            assertEquals("Board", ferry.getAction());
            assertEquals("Ferry", ferry.getName());
            assertEquals(0, ferry.getOrigin().getPlane());
            assertEquals(0, ferry.getDestination().getPlane());
            assertEquals(TransportExecutionRegistry.Executor.TERMINAL_TRAVEL,
                TransportExecutionRegistry.executorFor(ferry).orElseThrow());
        }
    }

    @Test void plannerConnectsRecordedTeleportFerryAndLandingToLobby() throws Exception
    {
        NetworkGraphRegressionTest fixture = graphFixture();
        ClassLoader loader = (ClassLoader)field(fixture, "loader");
        List<Object> edges = new ArrayList<>((List<?>)method(Class.forName(SHADOW + ".TransportCatalog", true, loader),
            "loadAllTransports").invoke(null));
        Method convert = (Method)field(fixture, "convert");
        Object bridge = field(fixture, "bridge");
        for (Transport source : catalog(fixture))
        {
            if (source.getOrigin() == null) continue;
            Object edge = convert.invoke(bridge, source, NORTH);
            if (edge != null) edges.add(edge);
        }
        Class<?> plannerType = Class.forName(SHADOW + ".CollisionRoutePlanner", true, loader);
        Method build = method(plannerType, "create", List.class, Predicate.class);
        Method plan = method(plannerType, "planRoute", WorldPoint.class, Collection.class,
            Set.class, Set.class, Set.class);
        Object planner = build.invoke(null, edges, (Predicate<Object>)edge -> true);
        for (WorldPoint start : List.of(new WorldPoint(3293, 3163, 0), FERRY, new WorldPoint(3148, 2842, 0)))
        {
            Object result = plan.invoke(planner, start, List.of(TemporossAccess.LOBBY_BANK), Set.of(), Set.of(), new HashSet<>(edges));
            List<?> path = (List<?>)field(result, "path");
            assertFalse(path.isEmpty(), "Missing recorded approach from " + start + "; failure=" + field(result, "failure"));
            assertEquals(TemporossAccess.LOBBY_BANK, path.get(path.size() - 1));
            assertTrue(path.stream().allMatch(p -> ((WorldPoint)p).getPlane() == 0));
        }
        Object shortcut = plan.invoke(planner, NORTH, List.of(SOUTH), Set.of(), Set.of(), new HashSet<>(edges));
        List<?> path = (List<?>)field(shortcut, "path");
        assertFalse(path.isEmpty());
        assertEquals(SOUTH, path.get(path.size() - 1));
    }

    private static NetworkGraphRegressionTest graphFixture() throws Exception
    {
        NetworkGraphRegressionTest fixture = new NetworkGraphRegressionTest();
        fixture.setup();
        return fixture;
    }

    @SuppressWarnings("unchecked")
    private static List<Transport> catalog(NetworkGraphRegressionTest fixture) throws Exception
    {
        return (List<Transport>)field(fixture, "catalog");
    }

    private static Transport shortcut(WorldPoint origin, WorldPoint destination, String action, int id, TransportType type)
    {
        return new Transport(origin, destination, "Arena shortcut", type, true, action, "Arena", id);
    }

    private static String xy(WorldPoint point) { return point.getX() + "," + point.getY(); }

    private static Object field(Object owner, String name) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(owner.getClass(), name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Method method(Class<?> owner, String name, Class<?>... types) throws Exception
    {
        Method method = WalkerTestReflection.declaredMethod(owner, name, types);
        method.setAccessible(true);
        return method;
    }

    private static final class NamedTravel
    {
        final EfficientWalkerPlugin plugin = new EfficientWalkerPlugin();
        final EfficientWalkerConfig config = mock(EfficientWalkerConfig.class, CALLS_REAL_METHODS);
        final EfficientWalker walker = mock(EfficientWalker.class);

        NamedTravel() throws Exception
        {
            for (String name : List.of("config", "walker"))
            {
                Field field = WalkerTestReflection.declaredField(EfficientWalkerPlugin.class, name);
                field.setAccessible(true);
                field.set(plugin, name.equals("config") ? config : walker);
            }
        }

        void change(String group, String key)
        {
            ConfigChanged event = new ConfigChanged();
            event.setGroup(group);
            event.setKey(key);
            plugin.onConfigChanged(event);
        }

        void process() throws Exception
        {
            method(EfficientWalkerPlugin.class, "processHotkey").invoke(plugin);
            assertNull(((AtomicReference<?>)field(plugin, "pendingHotkey")).get());
        }
    }
}
