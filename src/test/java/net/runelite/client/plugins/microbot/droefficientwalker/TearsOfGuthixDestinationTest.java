package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TearsOfGuthixDestinationTest
{
    @Test void junaApproachIsTheMaintainedDestination()
    {
        assertEquals(new WorldPoint(3250, 9516, 2), TearsOfGuthixLocation.JUNA_APPROACH);
    }

    @Test void namedDestinationControlsAreGoneBecauseTestWalkCoversThem()
    {
        for (String name : List.of("namedDestination", "walkToNamedDestination", "walkToNamedDestinationHotkey"))
        {
            assertThrows(NoSuchMethodException.class, () -> EfficientWalkerConfig.class.getMethod(name), name);
        }
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
}
