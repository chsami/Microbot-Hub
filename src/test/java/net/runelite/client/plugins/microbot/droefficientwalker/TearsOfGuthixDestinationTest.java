package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TearsOfGuthixDestinationTest
{
    @Test void destinationIsDiscoverableAndOptInThroughNativeSettings() throws Exception
    {
        EfficientWalkerConfig config = new EfficientWalkerConfig() {};
        assertEquals(WalkerDestination.NONE, config.namedDestination());
        assertNull(config.namedDestination().getWorldPoint());
        assertEquals(Keybind.NOT_SET, config.walkToNamedDestinationHotkey());
        assertEquals(new WorldPoint(3250, 9516, 2), WalkerDestination.TEARS_OF_GUTHIX.getWorldPoint());
        assertEquals(TearsOfGuthixLocation.JUNA_APPROACH, WalkerDestination.TEARS_OF_GUTHIX.getWorldPoint());
        assertEquals("Tears of Guthix (Juna)", WalkerDestination.TEARS_OF_GUTHIX.toString());
        for (String name : List.of("namedDestination", "walkToNamedDestination", "walkToNamedDestinationHotkey"))
        {
            ConfigItem item = EfficientWalkerConfig.class.getMethod(name).getAnnotation(ConfigItem.class);
            assertNotNull(item, name);
            // Main keeps the walk-to-named-location controls visible in the panel.
            assertFalse(item.hidden(), name);
        }
        assertEquals(ConfigButton.class, EfficientWalkerConfig.class.getMethod("walkToNamedDestination").getReturnType());
    }

    @Test void selectionAndUnrelatedButtonsNeverStartOrRetargetTravel() throws Exception
    {
        Fixture f = new Fixture();
        f.change(EfficientWalkerConfig.GROUP, "namedDestination");
        f.change("other-config-group", "walkToNamedDestination");
        f.process();
        verifyNoInteractions(f.walker);
    }

    @Test void buttonQueuesOneRequestAndDoesNotClaimArrival() throws Exception
    {
        Fixture f = new Fixture();
        when(f.config.namedDestination()).thenReturn(WalkerDestination.TEARS_OF_GUTHIX);
        when(f.walker.walkTo(TearsOfGuthixLocation.JUNA_APPROACH)).thenReturn(true);
        f.change(EfficientWalkerConfig.GROUP, "walkToNamedDestination");
        verifyNoInteractions(f.walker);
        f.process();
        f.process();
        verify(f.walker).walkTo(TearsOfGuthixLocation.JUNA_APPROACH);
        verifyNoMoreInteractions(f.walker);
    }

    @Test void hotkeyUsesSameRequestAndDefaultSelectionPreservesExistingRoute() throws Exception
    {
        Fixture f = new Fixture();
        HotkeyListener listener = (HotkeyListener) field(f.plugin, "destinationHotkey");
        listener.hotkeyPressed();
        f.process();
        verifyNoInteractions(f.walker);
        when(f.config.namedDestination()).thenReturn(WalkerDestination.TEARS_OF_GUTHIX);
        when(f.walker.walkTo(TearsOfGuthixLocation.JUNA_APPROACH)).thenReturn(true);
        listener.hotkeyPressed();
        f.process();
        verify(f.walker).walkTo(TearsOfGuthixLocation.JUNA_APPROACH);
        verifyNoMoreInteractions(f.walker);
    }

    @Test void existingGamesNecklaceLandingAndQuestRequirementRemainAvailable() throws Exception
    {
        TeleportProviders.TeleportProvider provider = TeleportProviders.createProviders().stream().filter(p -> "Games necklace: Tears of Guthix".equals(p.name))
            .findFirst().orElseThrow();
        assertEquals(new WorldPoint(3245, 9500, 2), provider.getDestination());
        BooleanSupplier requirement = (BooleanSupplier) field(provider, "availability");
        try (var player = mockStatic(Rs2Player.class))
        {
            player.when(() -> Rs2Player.getQuestState(Quest.TEARS_OF_GUTHIX)).thenReturn(QuestState.NOT_STARTED);
            assertFalse(requirement.getAsBoolean());
            player.when(() -> Rs2Player.getQuestState(Quest.TEARS_OF_GUTHIX)).thenReturn(QuestState.FINISHED);
            assertTrue(requirement.getAsBoolean());
        }
    }

    @Test void plannerConnectsBothLandingsToRecordedApproachWithoutInventedTransports() throws Exception
    {
        String shadow = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
        Constructor<?> loaderCtor = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader")
            .getDeclaredConstructor(ClassLoader.class);
        loaderCtor.setAccessible(true);
        ClassLoader loader = (ClassLoader) loaderCtor.newInstance(getClass().getClassLoader());
        Class<?> plannerClass = Class.forName(shadow + ".CollisionRoutePlanner", true, loader);
        Method build = WalkerTestReflection.declaredMethod(plannerClass, "create", List.class, Predicate.class);
        build.setAccessible(true);
        Object planner = build.invoke(null, List.of(), (Predicate<Object>) edge -> true);
        Method plan = WalkerTestReflection.declaredMethod(plannerClass, "planRoute", WorldPoint.class, Collection.class, Set.class, Set.class, Set.class);
        plan.setAccessible(true);
        // First is the recording's actual landing; second is the existing teleport provider's pin.
        for (WorldPoint landing : List.of(new WorldPoint(3243, 9502, 2), new WorldPoint(3245, 9500, 2)))
        {
            Object result = plan.invoke(planner, landing, List.of(TearsOfGuthixLocation.JUNA_APPROACH), Set.of(), Set.of(), Set.of());
            List<?> path = (List<?>) field(result, "path");
            assertFalse(path.isEmpty(), "Missing approach from " + landing);
            assertEquals(TearsOfGuthixLocation.JUNA_APPROACH, path.get(path.size() - 1));
            assertTrue(path.stream().allMatch(p -> ((WorldPoint) p).getPlane() == 2));
            assertFalse(path.contains(new WorldPoint(3252, 9516, 2)), "Juna's solid object tile is not the approach");
        }
    }

    private static Object field(Object owner, String name) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(owner.getClass(), name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static final class Fixture
    {
        final EfficientWalkerPlugin plugin = new EfficientWalkerPlugin();
        final EfficientWalkerConfig config = mock(EfficientWalkerConfig.class, CALLS_REAL_METHODS);
        final EfficientWalker walker = mock(EfficientWalker.class);

        Fixture() throws Exception
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
            Method process = WalkerTestReflection.declaredMethod(EfficientWalkerPlugin.class, "processHotkey");
            process.setAccessible(true);
            process.invoke(plugin);
            assertNull(((AtomicReference<?>) field(plugin, "pendingHotkey")).get());
        }
    }
}
