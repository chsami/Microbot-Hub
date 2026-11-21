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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise admission itself, including the bank coordinator's rejection boundary. */
class SelectedTransitionCapabilityTest
{
    private static final WorldPoint FROM = new WorldPoint(3000,3000,0), TO = FROM.dy(20);

    @Test void maintainedSupplyDependentEdgeIsAdmittedBeforeItemsAreCarried() throws Exception {
        Harness h = new Harness();
        try (var inventory = mockStatic(Rs2Inventory.class); var bank = mockStatic(Rs2Bank.class)) {
            assertTrue(h.network.canExecuteSelectedEdges(List.of(edge("Open", Rs2TransportExecutor.OBJECT))));
            assertNull(h.network.selectedTransitionRejection());
            inventory.verifyNoInteractions();
            bank.verifyNoInteractions();
        }
    }

    @Test void sameEndpointsWithDifferentActionAreRejectedWithIdentifyingReason() throws Exception {
        Harness h = new Harness();
        assertFalse(h.network.canExecuteSelectedEdges(List.of(edge("Climb", Rs2TransportExecutor.OBJECT))));
        String reason = h.network.selectedTransitionRejection();
        assertTrue(reason.contains("no matching executable capability"));
        assertTrue(reason.contains("action=Climb"));
        assertTrue(reason.contains("object=123"));
        assertTrue(reason.contains("destination="));
    }

    @Test void unsupportedSelectedExecutorIsRejectedDespiteMatchingCatalogEndpoints() throws Exception {
        Harness h = new Harness();
        assertFalse(h.network.canExecuteSelectedEdges(List.of(edge("Open", Rs2TransportExecutor.UNSUPPORTED))));
        assertTrue(h.network.selectedTransitionRejection().contains("executor=UNSUPPORTED"));
    }

    @Test void completeMaintainedRouteWithoutNativePrefixIsAdmitted() throws Exception {
        Harness h = new Harness();
        assertTrue(h.network.canExecuteCompleteRoute(choice(List.of(
            Rs2RouteStep.transport(FROM,TO,edge("Open",Rs2TransportExecutor.OBJECT))),null)));
    }

    @Test void completeRouteRejectsUnavailableNativePrefixBeforeWithdrawal() throws Exception {
        Harness h = new Harness();
        var prefix = new WeightedRouteAdvisor.BankedTeleportOption("Missing teleport",FROM,Map.of(8007,1),1);
        assertFalse(h.network.canExecuteCompleteRoute(choice(List.of(
            Rs2RouteStep.transport(FROM,TO,edge("Open",Rs2TransportExecutor.OBJECT))),prefix)));
        assertTrue(h.network.selectedTransitionRejection().contains("native prefix unavailable: Missing teleport"));
    }

    @Test void nativeItemProviderCannotBePlacedAfterAWalkingLeg() throws Exception {
        Harness h = new Harness();
        Rs2TransportEdge item = new Rs2TransportEdge(null,TO,Rs2TransportType.TELEPORTATION_ITEM,
            Rs2TransportExecutor.ITEM_TELEPORT,Rs2TerminalTravelMode.UNSUPPORTED,"Item teleport",null,null,-1,
            1,true,true,false,20,"",0,List.of(new Rs2TransportItemRequirement(Map.of(8007,1))));
        when(h.catalog.supportsSelectedEdge(item)).thenReturn(true);
        assertFalse(h.network.canExecuteCompleteRoute(choice(List.of(
            Rs2RouteStep.walk(FROM,FROM.dx(1)),Rs2RouteStep.transport(FROM.dx(1),TO,item)),null)));
        assertTrue(h.network.selectedTransitionRejection().contains("native item teleport must be the initial prefix"));
    }

    @Test void rejectedSelectedRouteCannotPopulateFallbackReservesOrConstructSupplyLoadout() throws Exception {
        try (HousePortalRecoveryTest.Fixture f = new HousePortalRecoveryTest.Fixture();
             var supplies = mockStatic(TransportSupplyPlanner.class);
             var bank = mockStatic(Rs2Bank.class)) {
            Harness h = new Harness();
            Object originalNetwork = get(f.walker,"networkTransportSupport");
            set(f.walker,"networkTransportSupport",h.network);
            try {
            AtomicReference<Object> fallback = new AtomicReference<>();
            Method method = WalkerTestReflection.declaredMethod(f.coordinator.getClass(),
                "bankBackedTransportRequirements",WorldPoint.class,boolean.class,WorldPoint.class,List.class,AtomicReference.class);
            method.setAccessible(true);
            Object result = method.invoke(f.coordinator,TO,false,FROM,
                List.of(edge("Climb",Rs2TransportExecutor.OBJECT)),fallback);
            assertNotNull(result);
            assertSame(result,fallback.get(),"Timeout fallback must retain the empty rejection result");
            assertEquals(Map.of(),get(result,"withdrawals"));
            assertEquals(List.of(),get(result,"equipment"));
            supplies.verifyNoInteractions();
            bank.verifyNoInteractions();
            } finally {
                set(f.walker,"networkTransportSupport",originalNetwork);
            }
        }
    }

    private static final class Harness {
        final CatalogTeleportSupport catalog = mock(CatalogTeleportSupport.class);
        final NetworkTransportSupport network = mock(NetworkTransportSupport.class,CALLS_REAL_METHODS);
        Harness() throws Exception {
            Transport source = new Transport(FROM,TO,"Maintained gate",TransportType.TRANSPORT,false,"Open","Gate",123);
            source.setItemIdRequirements(Set.of(Set.of(995)));
            set(network,"catalogTeleportSupport",catalog);
            set(network,"sharedCatalog",List.of(source));
            set(network,"sharedCatalogEdges",new IdentityHashMap<>());
            set(network,"capabilityGroups",List.of(Set.of(source)));
            set(network,"baseTransports",List.of());
            set(network,"injectedEdges",new IdentityHashMap<>());
            set(network,"charterRows",List.of());
            set(network,"minigameRows",List.of());
            set(network,"kourendMinecartStops",List.of());
            set(network,"engineClass",ProviderEngine.class);
            set(network,"engine",new ProviderEngine());
        }
    }

    public static final class ProviderEngine { private final List<Object> teleportProviders = List.of(); }

    private static CompleteRouteDiscovery.Choice choice(List<Rs2RouteStep> steps,WeightedRouteAdvisor.BankedTeleportOption prefix) {
        Rs2RouteResult route = mock(Rs2RouteResult.class);
        when(route.getSteps()).thenReturn(steps);
        when(route.getTerminationReason()).thenReturn(Rs2RouteTermination.TARGET_REACHED);
        return new CompleteRouteDiscovery.Choice("test",route,prefix,Rs2TransportLoadout.empty(),1);
    }

    private static Rs2TransportEdge edge(String action,Rs2TransportExecutor executor) {
        return new Rs2TransportEdge(FROM,TO,Rs2TransportType.TRANSPORT,executor,Rs2TerminalTravelMode.UNSUPPORTED,
            "Maintained gate",action,"Gate",123,1,false,false,false,0,"",0,
            List.of(new Rs2TransportItemRequirement(Map.of(995,1))));
    }
    private static Object get(Object object,String name) throws Exception {
        Field field=WalkerTestReflection.declaredField(object.getClass(),name);field.setAccessible(true);return field.get(object);
    }
    private static void set(Object object,String name,Object value) throws Exception {
        Field field=WalkerTestReflection.declaredField(object.getClass(),name);field.setAccessible(true);field.set(object,value);
    }
}
