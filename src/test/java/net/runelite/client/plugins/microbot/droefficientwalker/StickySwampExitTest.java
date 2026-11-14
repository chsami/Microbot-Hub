package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StickySwampExitTest
{
    private static final WorldPoint START = new WorldPoint(3684, 3756, 0);
    private static final WorldPoint HOUSE = new WorldPoint(3764, 3879, 1);
    private static final WorldPoint ZIPLINE_LANDING = new WorldPoint(3765, 3898, 0);
    // The recording opens Bank Chest 30796 directly from the zip-line landing.
    private static final WorldPoint BANK = new WorldPoint(3765, 3898, 0);

    @Test void sharedCatalogueDropsOnlyTheThreeOutgoingStickySwampCopies() throws Exception
    {
        NetworkGraphRegressionTest fixture = new NetworkGraphRegressionTest();
        fixture.setup();
        List<Transport> catalog = (List<Transport>) field(fixture, "catalog");
        List<Transport> mushtrees = new ArrayList<>();
        for (Transport row : catalog)
            if ("Magic Mushtree".equals(row.getName())) mushtrees.add(row);
        assertEquals(9, mushtrees.size(), "Other origins and incoming routes must remain unchanged");
        assertTrue(mushtrees.stream().noneMatch(row -> new WorldPoint(3677, 3755, 0).equals(row.getOrigin())));
        assertEquals(3, mushtrees.stream().filter(row -> new WorldPoint(3676, 3755, 0).equals(row.getDestination())).count());
        for (WorldPoint origin : List.of(new WorldPoint(3764, 3880, 1),
            new WorldPoint(3758, 3756, 0), new WorldPoint(3674, 3871, 0)))
            assertEquals(3, mushtrees.stream().filter(row -> origin.equals(row.getOrigin())).count());
    }

    @Test void realPlannerCanFollowTheRecordedHouseThenZiplineExit() throws Exception
    {
        NetworkGraphRegressionTest fixture = new NetworkGraphRegressionTest();
        fixture.setup();
        NetworkTransportSupport bridge = (NetworkTransportSupport) field(fixture, "bridge");
        ClassLoader loader = (ClassLoader) field(fixture, "loader");
        Class<?> transport = (Class<?>) field(fixture, "transportClass");
        for (String[] binding : List.of(new String[]{"transportOriginField", "origin"},
            new String[]{"transportDestinationField", "destination"}, new String[]{"transportActionField", "action"},
            new String[]{"transportTargetField", "target"}, new String[]{"transportObjectIdField", "objectId"}))
        {
            Field value = WalkerTestReflection.declaredField(transport, binding[1]);
            value.setAccessible(true);
            set(bridge, binding[0], value);
        }
        String shadow = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
        List<Object> edges = new ArrayList<>((List<?>) method(Class.forName(shadow + ".TransportCatalog", true, loader),
            "loadAllTransports").invoke(null));
        method(NetworkTransportSupport.class, "removeKnownCustomTransportDuplicates", List.class).invoke(bridge, edges);
        Method convert = method(NetworkTransportSupport.class, "buildSharedCatalogTransport", Transport.class, WorldPoint.class);
        for (Transport row : (List<Transport>) field(fixture, "catalog"))
            if (row.getOrigin() != null) edges.add(convert.invoke(bridge, row, START));
        try (var player = mockStatic(Rs2Player.class))
        {
            player.when(() -> Rs2Player.getQuestState(Quest.BONE_VOYAGE)).thenReturn(QuestState.FINISHED);
            assertEquals(21, method(NetworkTransportSupport.class, "addMagicMushtreeEdges", List.class).invoke(bridge, edges));
        }
        assertTrue(edges.stream().anyMatch(edge -> {
            try { return Integer.valueOf(57667).equals(field(edge, "objectId")); }
            catch (Exception failure) { throw new AssertionError(failure); }
        }), "The existing graph must retain the recorded Teeth-grip zip line");
        Class<?> plannerType = Class.forName(shadow + ".CollisionRoutePlanner", true, loader);
        Object planner = method(plannerType, "create", List.class, Predicate.class)
            .invoke(null, edges, (Predicate<Object>) edge -> true);
        Object result = method(plannerType, "planRoute", WorldPoint.class, Collection.class,
            Set.class, Set.class, Set.class).invoke(planner, START, List.of(BANK), Set.of(), Set.of(), new HashSet<>(edges));
        List<WorldPoint> path = (List<WorldPoint>) field(result, "path");
        assertFalse(path.isEmpty(), "The recorded exit must connect to the northern bank; failure=" + field(result, "failure")
            + "; targetUsable=" + method(plannerType, "isUsableRouteTarget", WorldPoint.class).invoke(planner, BANK));
        assertTrue(path.contains(HOUSE), "Exit via the recorded House on the Hill destination");
        assertTrue(path.contains(ZIPLINE_LANDING), "Continue through the recorded zip-line landing");
        assertEquals(BANK, path.get(path.size() - 1));
    }

    @Test void retryUsesTheExistingOwnedChargedWealthProvider() throws Exception
    {
        try (var fixture = new ApeAtollGreegreeIntegrationTest.Fixture())
        {
            fixture.location.set(START);
            fixture.carried.put(11980, 1); // Ring of wealth (5).
            assertEquals(BankLocation.GRAND_EXCHANGE.getWorldPoint(), retryBank(fixture.walker, BANK));
            fixture.location.set(HOUSE);
            assertEquals(BankLocation.GRAND_EXCHANGE.getWorldPoint(), retryBank(fixture.walker, BANK));
            fixture.location.set(START);
            fixture.carried.clear();
            fixture.carried.put(2572, 1); // Uncharged ring is not an available teleport.
            assertNull(retryBank(fixture.walker, BANK));
            fixture.carried.clear();
            assertNull(retryBank(fixture.walker, BANK));
        }
    }

    @Test void recordedZiplineIsOnlyAdmittedForTheSwampJourneyAtTheRequiredLevel() throws Exception
    {
        NetworkGraphRegressionTest fixture = new NetworkGraphRegressionTest();
        fixture.setup();
        NetworkTransportSupport bridge = (NetworkTransportSupport) field(fixture, "bridge");
        Transport zipline = ((List<Transport>) field(fixture, "catalog")).stream()
            .filter(row -> row.getObjectId() == 57667).findFirst().orElseThrow();
        Method allowed = method(NetworkTransportSupport.class, "sharedRequirementsSatisfiedNow", Transport.class);
        try (var player = mockStatic(Rs2Player.class))
        {
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.AGILITY)).thenReturn(76);
            assertEquals(false, allowed.invoke(bridge, zipline));
            set(bridge, "stickySwampBankJourney", true);
            assertEquals(true, allowed.invoke(bridge, zipline));
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.AGILITY)).thenReturn(75);
            assertEquals(false, allowed.invoke(bridge, zipline));
            bridge.cancel();
            assertFalse((Boolean) field(bridge, "stickySwampBankJourney"));
        }
    }

    @Test void retryCannotOverrideTeleportPermissionTeleblockOrAnotherArea() throws Exception
    {
        try (var fixture = new ApeAtollGreegreeIntegrationTest.Fixture())
        {
            fixture.location.set(START);
            fixture.carried.put(11980, 1);
            when(fixture.config.useTeleportations()).thenReturn(false);
            assertNull(retryBank(fixture.walker, BANK));
            when(fixture.config.useTeleportations()).thenReturn(true);
            fixture.player.when(Rs2Player::isTeleBlocked).thenReturn(true);
            assertNull(retryBank(fixture.walker, BANK));
            fixture.player.when(Rs2Player::isTeleBlocked).thenReturn(false);
            assertNull(retryBank(fixture.walker, new WorldPoint(3166, 3485, 0)));
            fixture.location.set(new WorldPoint(3757, 3756, 0));
            assertNull(retryBank(fixture.walker, BANK));
            fixture.location.set(new WorldPoint(3676, 3871, 0));
            assertNull(retryBank(fixture.walker, BANK));
        }
    }

    @Test void aConsumedOrCancelledFailureCannotLeakIntoAnotherJourney() throws Exception
    {
        try (var fixture = new ApeAtollGreegreeIntegrationTest.Fixture())
        {
            NetworkTransportSupport bridge = (NetworkTransportSupport) field(fixture.walker, "networkTransportSupport");
            assertFalse(bridge.consumeStickySwampBankRetry());
            set(bridge, "stickySwampBankRetry", true);
            assertTrue(bridge.consumeStickySwampBankRetry());
            assertFalse(bridge.consumeStickySwampBankRetry());
            set(bridge, "stickySwampBankRetry", true);
            bridge.cancel();
            assertFalse(bridge.consumeStickySwampBankRetry());
        }
    }

    private static WorldPoint retryBank(EfficientWalker walker, WorldPoint target) throws Exception
    {
        return (WorldPoint) method(EfficientWalker.class, "stickySwampRetryBank", WorldPoint.class).invoke(walker, target);
    }
    private static Object field(Object object, String name) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static Method method(Class<?> type, String name, Class<?>... arguments) throws Exception
    {
        Method method = WalkerTestReflection.declaredMethod(type, name, arguments);
        method.setAccessible(true);
        return method;
    }
}
