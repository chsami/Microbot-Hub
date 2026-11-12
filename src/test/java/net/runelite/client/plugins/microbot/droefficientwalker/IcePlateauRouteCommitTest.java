package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteStep;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IcePlateauRouteCommitTest {
    private static final WorldPoint START = new WorldPoint(3162,3488,0);
    private static final WorldPoint ICE = new WorldPoint(2975,3938,0);
    private static final WorldPoint TARGET = new WorldPoint(3002,3932,0);
    static class Engine { private List<?> teleportProviders = List.of(new Tablet()); }
    static class Tablet { private WorldPoint getDestination() { return ICE; } }

    @Test void selectedSpellRemainsExecutableWithoutSameLandingTablet() throws Exception {
        exercise(true);
    }
    @Test void sameLandingEdgeCannotFalselyConfirmMissingSpell() throws Exception {
        exercise(false);
    }
    private void exercise(boolean executable) throws Exception {
        NetworkGraphRegressionTest fixture = new NetworkGraphRegressionTest();
        fixture.setup();
        NetworkTransportSupport original = (NetworkTransportSupport) value(fixture,"bridge");
        Method catalogMethod = method("loadSharedCatalog");
        List<Transport> catalog = (List<Transport>) catalogMethod.invoke(original);
        Transport spell = catalog.stream().filter(t -> "Ice Plateau Teleport".equals(t.getDisplayInfo()))
            .findFirst().orElseThrow();
        assertNull(spell.getOrigin());
        Method convert = method("buildSharedCatalogTransport",Transport.class,WorldPoint.class);
        Object anchored = convert.invoke(original,spell,START);
        assertNotNull(anchored);
        assertEquals(START,value(anchored,"origin"));
        assertEquals(ICE,value(anchored,"destination"));
        Engine engine = new Engine();
        NetworkTransportSupport bridge = new NetworkTransportSupport(mock(Client.class),mock(EfficientWalkerConfig.class),
            getClass().getClassLoader(),Engine.class,engine,"unused",mock(CatalogTeleportSupport.class));
        for(String[] binding:new String[][]{{"transportOriginField","origin"},{"transportDestinationField","destination"}}) {
            Field source = WalkerTestReflection.declaredField(anchored.getClass(), binding[1]);source.setAccessible(true);
            set(bridge,binding[0],source);
        }
        if(executable) ((Map<Object,Transport>)value(bridge,"sharedCatalogEdges")).put(anchored,spell);
        Method typedMethod = WalkerTestReflection.declaredMethod(Rs2PathApi.class, "toTransportEdge",Transport.class);
        typedMethod.setAccessible(true);
        Rs2TransportEdge typed = (Rs2TransportEdge)typedMethod.invoke(null,spell);
        Rs2RouteResult route = mock(Rs2RouteResult.class);
        when(route.getPath()).thenReturn(List.of(START,ICE,TARGET));
        when(route.getSteps()).thenReturn(List.of(Rs2RouteStep.transport(START,ICE,typed),Rs2RouteStep.walk(ICE,TARGET)));
        bridge.preferCompleteRoute(TARGET,new CompleteRouteDiscovery.Choice("Ice Plateau",route,null,Rs2TransportLoadout.empty(),27));
        Method commit = method("applyCompleteRoute",WorldPoint.class,List.class);
        List<Object> edges = new ArrayList<>(List.of(anchored));
        net.runelite.client.callback.ClientThread thread = mock(net.runelite.client.callback.ClientThread.class);
        when(thread.invoke(any(java.util.function.Supplier.class))).thenAnswer(i -> ((java.util.function.Supplier<?>)i.getArgument(0)).get());
        try (var micro = mockStatic(net.runelite.client.plugins.microbot.Microbot.class)) {
        micro.when(net.runelite.client.plugins.microbot.Microbot::getClientThread).thenReturn(thread);
        if(executable) {
            commit.invoke(bridge,TARGET,edges);
            assertTrue(bridge.completeRouteReady());
            assertEquals(List.of(anchored),edges);
            assertTrue(engine.teleportProviders.isEmpty(),"Unavailable same-landing tablet must not replace the rune spell");
        } else {
            commit.invoke(bridge,TARGET,edges);
            assertFalse(bridge.completeRouteReady());
            assertEquals(List.of(anchored),edges,"An unavailable spell must leave the ordinary graph intact");
            assertEquals(1,engine.teleportProviders.size(),"Unavailable spell must restore original providers");
        }
        }
        bridge.clearCompleteRoute();
        assertEquals(1,engine.teleportProviders.size(),"Original provider list is restored after the route");
    }
    private static Method method(String name,Class<?>... args) throws Exception {
        Method method=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, name,args);method.setAccessible(true);return method;
    }
    private static Object value(Object owner,String name) throws Exception {
        Field field=WalkerTestReflection.declaredField(owner.getClass(), name);field.setAccessible(true);return field.get(owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        Field field=WalkerTestReflection.declaredField(owner.getClass(), name);field.setAccessible(true);field.set(owner,value);
    }
}
