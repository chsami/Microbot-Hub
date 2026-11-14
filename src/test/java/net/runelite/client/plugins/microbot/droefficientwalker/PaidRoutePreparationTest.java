package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteStep;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteTermination;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TerminalTravelMode;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PaidRoutePreparationTest
{
    private static final WorldPoint START = new WorldPoint(2099, 3919, 0);
    private static final WorldPoint TARGET = new WorldPoint(3039, 2994, 0);

    @Test public void splitFundsUnlockPreparationButNotExecution()
    {
        assertTrue(TravelCurrency.hasAmount(10, 20, 30, true));
        assertFalse(TravelCurrency.hasAmount(10, 20, 30, false));
        assertFalse(TravelCurrency.hasAmount(10, 19, 30, true));
        assertTrue(TravelCurrency.hasAmount(Integer.MAX_VALUE, Integer.MAX_VALUE, 30, true));
    }

    @Test public void wholeJourneyWithdrawsOnlyMissingFare()
    {
        Rs2TransportLoadout supplies = TransportSupplyPlanner.supplies(
            List.of(edge(30, List.of()), edge(20, List.of())),
            es -> { assertTrue(es.isEmpty()); return Rs2TransportLoadout.empty(); },
            id -> id == 995 ? 40 : 0, id -> id == 995 ? 10 : 0);
        assertTrue(supplies.isSatisfiable());
        assertEquals(Map.of(995, 40), supplies.getWithdrawals());
    }

    @Test public void insufficientTotalFundsRejectJourney()
    {
        assertFalse(TransportSupplyPlanner.supplies(List.of(edge(30, List.of()), edge(20, List.of())),
            es -> Rs2TransportLoadout.empty(), id -> 39, id -> 10).isSatisfiable());
    }

    @Test public void equipmentAndFareAreBothPrepared()
    {
        Rs2TransportEdge paidEquipment = edge(30, List.of(new Rs2TransportItemRequirement(Map.of(772, 1))));
        Rs2TransportLoadout supplies = TransportSupplyPlanner.supplies(List.of(paidEquipment),
            es -> { assertEquals(List.of(paidEquipment), es);
                return new Rs2TransportLoadout(Map.of(772, 1), List.of(772), true); },
            id -> id == 995 ? 30 : 1, id -> 0);
        assertEquals(Map.of(995, 30, 772, 1), supplies.getWithdrawals());
        assertEquals(List.of(772), supplies.getEquipmentItemIds());
    }

    @Test public void carriedCoinsDoNotRequireAnotherWithdrawal()
    {
        assertTrue(TransportSupplyPlanner.supplies(List.of(edge(30, List.of())),
            es -> Rs2TransportLoadout.empty(), id -> 0, id -> 30).getWithdrawals().isEmpty());
    }

    @Test public void bankFundedWinnerRetainsFareBeforeExecution()
    {
        Rs2TransportEdge ship = edge(30, List.of());
        Rs2RouteResult route = mock(Rs2RouteResult.class);
        when(route.isTargetReached(0)).thenReturn(true);
        when(route.getTerminationReason()).thenReturn(Rs2RouteTermination.TARGET_REACHED);
        when(route.getPath()).thenReturn(List.of(START, TARGET));
        when(route.getSteps()).thenReturn(List.of(Rs2RouteStep.transport(START, TARGET, ship)));
        CompleteRouteDiscovery.Choice choice = new CompleteRouteDiscovery(req -> route,
            (es, prefix) -> TransportSupplyPlanner.supplies(es, items -> Rs2TransportLoadout.empty(),
                id -> id == 995 ? 30 : 0, id -> 0), message -> { }, () -> 0L)
            .compare(START, TARGET, true, List.of(), WeightedRouteAdvisor::teleportSearchDistance);
        assertNotNull(choice);
        assertEquals("bank-funded complete route", choice.name);
        assertEquals(Map.of(995, 30), choice.loadout.getWithdrawals());
    }

    @Test public void failedAsyncPreviewUsesReachableKnownBank() throws Exception
    {
        Client client = mock(Client.class);
        when(client.getTickCount()).thenReturn(20);
        when(client.isClientThread()).thenReturn(true);
        EfficientWalkerConfig config = mock(EfficientWalkerConfig.class);
        when(config.useTeleportations()).thenReturn(true);
        when(config.useItemsFromBank()).thenReturn(true);
        EfficientWalker walker = mock(EfficientWalker.class, CALLS_REAL_METHODS);
        set(walker, "requestOwnership", new WalkerRequestOwnership());
        set(walker, "routeSubmission", TestRouteSubmissions.immediate());
        set(walker, "bankSuppliesAllowed", true);
        set(walker, "apeAtollGreegree", new ApeAtollGreegree());
        set(walker, "wildernessTravelResume", new WildernessTravelResume());
        set(walker, "requestCancellation", new WalkerRequestCancellation());
        Engine engine = new Engine();
        set(walker, "client", client); set(walker, "config", config);
        set(walker, "engine", engine); set(walker, "engineClass", Engine.class);
        Class<?> type = Class.forName(EfficientWalker.class.getName() + "$BankTeleportBootstrap");
        Constructor<?> ctor = type.getDeclaredConstructor(EfficientWalker.class);
        ctor.setAccessible(true);
        Object coordinator = ctor.newInstance(walker);
        set(walker, "bankTeleportBootstrap", coordinator);
        set(walker, "nearbyDestinationRecovery", mock(WalkerTestReflection.declaredField(EfficientWalker.class, "nearbyDestinationRecovery").getType()));
        set(walker, "networkTransportSupport", mock(NetworkTransportSupport.class));
        Object cache = mock(WalkerTestReflection.declaredField(coordinator.getClass(), "loginBankCache").getType(),
            invocation -> invocation.getMethod().getName().equals("isKnown") ? true : RETURNS_DEFAULTS.answer(invocation));
        set(coordinator, "loginBankCache", cache);
        set(coordinator, "pendingTarget", TARGET);
        set(coordinator, "stateStartedTick", 19);
        Field state = WalkerTestReflection.declaredField(type, "state"); state.setAccessible(true);
        state.set(coordinator, Enum.valueOf((Class) state.getType(), "WAITING_DIRECT_PREVIEW"));
        try (var bank = mockStatic(Rs2Bank.class); var player = mockStatic(Rs2Player.class))
        {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            player.when(Rs2Player::getWorldLocation).thenReturn(START);
            Method finish = WalkerTestReflection.declaredMethod(type, "finishInitialPreviewIfReady");
            finish.setAccessible(true); finish.invoke(coordinator);
            assertEquals("WAITING_BANK_SNAPSHOT", state.get(coordinator).toString());
            assertEquals(1, engine.cancels);
            assertEquals(TARGET, get(coordinator, "pendingTarget"));
            assertNull(get(coordinator, "weightedRouteAnalysis"));
        }
    }

    public static class Engine
    {
        int cancels;
        public WorldPoint getDestination() { return TARGET; }
        public EfficientWalker.Status getStatus() { return EfficientWalker.Status.BLOCKED; }
        public EfficientWalker.PlanningFailure getPlanningFailure() { return EfficientWalker.PlanningFailure.ROUTE_UNSUPPORTED; }
        public void cancel() { cancels++; }
    }

    private static Rs2TransportEdge edge(int fare, List<Rs2TransportItemRequirement> items)
    {
        return new Rs2TransportEdge(START, TARGET, Rs2TransportType.SHIP, Rs2TransportExecutor.UNSUPPORTED,
            Rs2TerminalTravelMode.UNSUPPORTED, "Paid ship", "Travel", "Captain", 14985, 10,
            false, false, true, 0, "Coins", fare, items);
    }

    private static void set(Object object, String name, Object value) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true); field.set(object, value);
    }

    private static Object get(Object object, String name) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true); return field.get(object);
    }
}
