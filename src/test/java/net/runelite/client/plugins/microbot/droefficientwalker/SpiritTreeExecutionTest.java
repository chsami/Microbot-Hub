package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpiritTreeExecutionTest {
    static class Engine { Object activeTransport; }
    SpiritTreeSupport bridge;
    Engine engine;
    Client client;
    Player player;
    Widget root, option;
    MockedStatic<Rs2Widget> widgets;
    MockedStatic<SceneObjects> objects;
    MockedStatic<net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard> keys;
    boolean spiritTreesEnabled = true, guildEnabled = true;
    static final WorldPoint GE = new WorldPoint(3184,3508,0), GUILD = new WorldPoint(1251,3750,0);

    @BeforeEach void setup() throws Exception {
        client=mock(Client.class);player=mock(Player.class);when(client.getLocalPlayer()).thenReturn(player);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(client.isClientThread()).thenReturn(true);
        when(player.getWorldLocation()).thenReturn(GE);
        engine=new Engine();
        var config=new EfficientWalkerConfig(){
            public boolean useSpiritTrees(){return spiritTreesEnabled;}
            public boolean spiritTreeFarmingGuild(){return guildEnabled;}
        };
        bridge=new SpiritTreeSupport(client,config,getClass().getClassLoader(),Engine.class,engine,"unused");
        set(bridge,"installed",true);var field=WalkerTestReflection.declaredField(Engine.class, "activeTransport");field.setAccessible(true);set(bridge,"activeTransportField",field);
        Class<?> edge=Class.forName(SpiritTreeSupport.class.getName()+"$SpiritEdge");
        Constructor<?> ctor=edge.getDeclaredConstructors()[0];ctor.setAccessible(true);
        engine.activeTransport=new Object();((Map<Object,Object>)get(bridge,"injectedEdges")).put(engine.activeTransport,ctor.newInstance(GE,GUILD,1295,"Grand Exchange","Farming Guild"));
        bridge.planningFor(new WorldPoint(1234,3700,0));
        root=mock(Widget.class);option=mock(Widget.class);when(root.getChildren()).thenReturn(new Widget[]{option});
        when(option.getText()).thenReturn("B: Farming Guild");when(option.getBounds()).thenReturn(new Rectangle(10,10,100,20));
        widgets=mockStatic(Rs2Widget.class);objects=mockStatic(SceneObjects.class);
        keys=mockStatic(net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.class);
        widgets.when(()->Rs2Widget.isWidgetVisible(ComponentID.ADVENTURE_LOG_CONTAINER)).thenReturn(true);
        widgets.when(()->Rs2Widget.hasWidgetText("Spirit Tree Locations",187,0,false)).thenReturn(true);
        widgets.when(()->Rs2Widget.getWidget(187,3)).thenReturn(root);
    }
    @AfterEach void cleanup(){keys.close();objects.close();widgets.close();}
    @Test void loadingMenuShellWaitsForOptionsWithoutAnotherTreeClick()throws Exception {
        widgets.when(()->Rs2Widget.hasWidgetText("Spirit Tree Locations",187,0,false)).thenReturn(false);
        assertTrue(bridge.beforeEngineTick());objects.verifyNoInteractions();
        assertFalse(bridge.consumeReplanRequest());
        widgets.when(()->Rs2Widget.hasWidgetText("Spirit Tree Locations",187,0,false)).thenReturn(true);
        widgets.when(()->Rs2Widget.clickWidget(option)).thenReturn(true);
        assertTrue(bridge.beforeEngineTick());objects.verifyNoInteractions();
        widgets.verify(()->Rs2Widget.clickWidget(option),times(1));
    }
    @Test void loadingMenuThatNeverPopulatesIsDismissedAfterItsBoundedWait()throws Exception {
        widgets.when(()->Rs2Widget.hasWidgetText("Spirit Tree Locations",187,0,false)).thenReturn(false);
        assertTrue(bridge.beforeEngineTick());set(bridge,"phaseStartedAt",System.currentTimeMillis()-6001L);
        assertTrue(bridge.beforeEngineTick());assertTrue(bridge.consumeReplanRequest());
        objects.verifyNoInteractions();
        keys.verify(()->net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE),times(1));
    }
    @Test void freeWorldRejectsPreviouslySelectedSpiritRouteWithoutInput() throws Exception {
        Method key=WalkerTestReflection.declaredMethod(SpiritTreeSupport.class, "configKey");key.setAccessible(true);
        Object membersKey=key.invoke(bridge);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
        assertNotEquals(membersKey,key.invoke(bridge),"A world-type change invalidates the injected graph");
        assertTrue(bridge.beforeEngineTick());assertTrue(bridge.consumeReplanRequest());
        widgets.verify(()->Rs2Widget.clickWidget(any(Widget.class)),never());
        objects.verifyNoInteractions();
        assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).isEmpty(),"An F2P hop must not blacklist an unlocked tree after returning to members");
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        assertEquals(membersKey,key.invoke(bridge));
    }
    @Test void disabledDestinationIsRejectedWithoutClickingAndRequestsReplanning() throws Exception {
        when(option.getText()).thenReturn("<col=5f5f5f>B: Farming Guild</col>");
        assertTrue(bridge.beforeEngineTick());assertTrue(bridge.consumeReplanRequest());
        widgets.verify(()->Rs2Widget.clickWidget(any(Widget.class)),never());
        assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).contains("Farming Guild"));
        assertFalse(bridge.consumeReplanRequest());
    }
    @Test void missingDestinationTimesOutWithoutReopeningTheSameTree() throws Exception {
        when(option.getText()).thenReturn("B: Farming Guild annex");
        assertTrue(bridge.beforeEngineTick());set(bridge,"phaseStartedAt",System.currentTimeMillis()-7000);
        assertTrue(bridge.beforeEngineTick());assertTrue(bridge.consumeReplanRequest());
        widgets.verify(()->Rs2Widget.clickWidget(any(Widget.class)),never());
        objects.verify(()->SceneObjects.interact(any(TileObject.class),eq("Travel")),never());
    }
    @Test void rejectedWidgetClickDoesNotPretendToBeWaitingForArrival() throws Exception {
        widgets.when(()->Rs2Widget.clickWidget(option)).thenReturn(false);
        assertTrue(bridge.beforeEngineTick());assertEquals("WAITING_FOR_MENU",get(bridge,"phase").toString());
        assertFalse(bridge.consumeReplanRequest());
    }
    @Test void acceptedClickRequiresActualArrivalAndNeverRepeatsItWhileWaiting() throws Exception {
        widgets.when(()->Rs2Widget.clickWidget(option)).thenReturn(true);
        assertTrue(bridge.beforeEngineTick());assertTrue(bridge.beforeEngineTick());
        assertEquals("WAITING_FOR_LANDING",get(bridge,"phase").toString());
        widgets.verify(()->Rs2Widget.clickWidget(option),times(1));
        when(player.getWorldLocation()).thenReturn(GUILD);assertFalse(bridge.beforeEngineTick());
        assertFalse(bridge.consumeReplanRequest());
    }
    @Test void disabledSavedDestinationRetainsAnIssuedLandingEvenAfterTheNativeEdgeClears() throws Exception {
        assertFalse(bridge.hasPendingAction());
        widgets.when(()->Rs2Widget.clickWidget(option)).thenReturn(true);
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.hasPendingAction());
        guildEnabled=false;
        engine.activeTransport=null;
        bridge.refreshForConfigIfNeeded();
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.hasPendingAction());
        assertFalse(bridge.consumeReplanRequest());
        assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).isEmpty());
        widgets.verify(()->Rs2Widget.clickWidget(option),times(1));
        keys.verifyNoInteractions();
        when(player.getWorldLocation()).thenReturn(GUILD);
        assertFalse(bridge.beforeEngineTick());
        assertFalse(bridge.hasPendingAction());
        assertFalse(bridge.consumeReplanRequest());
    }
    @Test void disablingSpiritTreesAfterDispatchWaitsForTheExistingBoundedLandingReceipt() throws Exception {
        widgets.when(()->Rs2Widget.clickWidget(option)).thenReturn(true);
        assertTrue(bridge.beforeEngineTick());
        spiritTreesEnabled=false;
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.hasPendingAction());
        assertFalse(bridge.consumeReplanRequest());
        set(bridge,"phaseStartedAt",System.currentTimeMillis()-16000);
        assertTrue(bridge.beforeEngineTick());
        assertFalse(bridge.hasPendingAction());
        assertTrue(bridge.consumeReplanRequest());
        assertFalse(bridge.consumeReplanRequest());
        widgets.verify(()->Rs2Widget.clickWidget(option),times(1));
    }
    @Test void disablingAnOpenedMenuCannotIssueANewDestinationSelection() throws Exception {
        WorldPoint target=(WorldPoint)get(bridge,"planningTarget");
        widgets.when(()->Rs2Widget.hasWidgetText("Spirit Tree Locations",187,0,false)).thenReturn(false);
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.hasPendingAction());
        guildEnabled=false;
        widgets.when(()->Rs2Widget.hasWidgetText("Spirit Tree Locations",187,0,false)).thenReturn(true);
        assertTrue(bridge.beforeEngineTick());
        assertFalse(bridge.hasPendingAction());
        assertTrue(bridge.consumeReplanRequest());
        widgets.verify(()->Rs2Widget.clickWidget(any(Widget.class)),never());
        objects.verifyNoInteractions();
        assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).isEmpty());
        guildEnabled=true;
        bridge.planningFor(target);
        widgets.when(()->Rs2Widget.clickWidget(option)).thenReturn(true);
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.hasPendingAction());
        widgets.verify(()->Rs2Widget.clickWidget(option),times(1));
        assertFalse(bridge.consumeReplanRequest());
    }
    @Test void reenabledMasterPreferenceCanSelectTheSameJourneyDestination() throws Exception {
        WorldPoint target=(WorldPoint)get(bridge,"planningTarget");
        widgets.when(()->Rs2Widget.hasWidgetText("Spirit Tree Locations",187,0,false)).thenReturn(false);
        assertTrue(bridge.beforeEngineTick());
        spiritTreesEnabled=false;
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.consumeReplanRequest());
        assertFalse(bridge.hasPendingAction());
        assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).isEmpty());
        spiritTreesEnabled=true;
        bridge.planningFor(target);
        widgets.when(()->Rs2Widget.hasWidgetText("Spirit Tree Locations",187,0,false)).thenReturn(true);
        widgets.when(()->Rs2Widget.clickWidget(option)).thenReturn(true);
        assertTrue(bridge.beforeEngineTick());
        widgets.verify(()->Rs2Widget.clickWidget(option),times(1));
        assertFalse(bridge.consumeReplanRequest());
    }
    @Test void preferenceChangesDoNotClearAnObservedDisabledMenuFailure() throws Exception {
        WorldPoint target=(WorldPoint)get(bridge,"planningTarget");
        when(option.getTextColor()).thenReturn(0x5f5f5f);
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.consumeReplanRequest());
        assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).contains("Farming Guild"));
        spiritTreesEnabled=false;
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.consumeReplanRequest());
        spiritTreesEnabled=true;
        bridge.planningFor(target);
        when(option.getTextColor()).thenReturn(0xffffff);
        assertTrue(bridge.beforeEngineTick());
        assertTrue(bridge.consumeReplanRequest());
        assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).contains("Farming Guild"));
        widgets.verify(()->Rs2Widget.clickWidget(any(Widget.class)),never());
    }
    @Test void unconfirmedLandingExcludesDestinationUntilJourneyChanges() throws Exception {
        widgets.when(()->Rs2Widget.clickWidget(option)).thenReturn(true);assertTrue(bridge.beforeEngineTick());
        set(bridge,"phaseStartedAt",System.currentTimeMillis()-16000);
        assertTrue(bridge.beforeEngineTick());assertTrue(bridge.consumeReplanRequest());
        bridge.planningFor(new WorldPoint(1234,3700,0));assertFalse(((Set<?>)get(bridge,"rejectedDestinations")).isEmpty());
        bridge.planningFor(new WorldPoint(1300,3700,0));assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).isEmpty());
    }
    @Test void cancellationRestoresRoutesForTheNextJourney() throws Exception {
        when(option.getTextColor()).thenReturn(0x5f5f5f);assertTrue(bridge.beforeEngineTick());
        bridge.cancel();assertTrue(((Set<?>)get(bridge,"rejectedDestinations")).isEmpty());assertFalse(bridge.consumeReplanRequest());
    }
    @Test void hiddenOptionDoesNotCountAsAvailable() throws Exception {
        when(option.isHidden()).thenReturn(true);assertTrue(bridge.beforeEngineTick());
        widgets.verify(()->Rs2Widget.clickWidget(any(Widget.class)),never());
    }
    @Test void unlaidOutOptionWaitsWithoutSendingAnInvalidClick() {
        when(option.getBounds()).thenReturn(new Rectangle());
        assertTrue(bridge.beforeEngineTick());
        widgets.verify(()->Rs2Widget.clickWidget(any(Widget.class)),never());
        assertFalse(bridge.consumeReplanRequest());
    }
    @Test void rejectedDestinationIsRemovedFromEveryOriginInTheRealPlannerGraph() throws Exception {
        SpiritTreeSupport graph=realGraph(new EfficientWalkerConfig(){
            public boolean spiritTreeFarmingGuild(){return true;}
        });
        Map<Object,Object> edges=(Map<Object,Object>)get(graph,"injectedEdges");
        assertTrue(edges.values().stream().filter(edge->label(edge).equals("Farming Guild")).count()>1);
        Object guild=edges.values().stream().filter(edge->label(edge).equals("Farming Guild")).findFirst().orElseThrow();
        Method reject=WalkerTestReflection.declaredMethod(SpiritTreeSupport.class, "reject",guild.getClass(),String.class);
        reject.setAccessible(true);reject.invoke(graph,guild,"test menu refused");
        assertTrue(graph.consumeReplanRequest());
        graph.refreshForConfigIfNeeded();
        edges=(Map<Object,Object>)get(graph,"injectedEdges");
        assertFalse(edges.values().stream().anyMatch(edge->label(edge).equals("Farming Guild")));
        assertTrue(edges.values().stream().anyMatch(edge->label(edge).equals("Grand Exchange")));
        assertTrue(edges.values().stream().anyMatch(edge->label(edge).equals("Tree Gnome Village")));
        graph.cancel();graph.refreshForConfigIfNeeded();
        edges=(Map<Object,Object>)get(graph,"injectedEdges");
        assertTrue(edges.values().stream().anyMatch(edge->label(edge).equals("Farming Guild")));
    }
    @Test void defaultRealGraphExcludesFarmedOriginsAndDestinations() throws Exception {
        SpiritTreeSupport graph=realGraph(new EfficientWalkerConfig(){});
        Map<Object,Object> edges=(Map<Object,Object>)get(graph,"injectedEdges");
        assertFalse(edges.isEmpty());
        for(Object edge:edges.values()) {
            for(String name:List.of("Etceteria","Brimhaven","Port Sarim","Hosidius","Farming Guild"))
                assertNotEquals(name,label(edge));
            WorldPoint origin=(WorldPoint)get(edge,"origin");
            assertTrue(origin.distanceTo2D(GUILD)>5,"Unchecked farmed origin must also be absent");
        }
    }
    @Test void rebuildingAnActiveSpiritRouteRequestsReplanningInsteadOfLeavingAStaleEdge() throws Exception {
        boolean[] farmed={true};
        SpiritTreeSupport graph=realGraph(new EfficientWalkerConfig(){
            public boolean spiritTreeFarmingGuild(){return farmed[0];}
        });
        Map<Object,Object> edges=(Map<Object,Object>)get(graph,"injectedEdges");
        Object selected=edges.entrySet().stream().filter(entry->label(entry.getValue()).equals("Farming Guild"))
            .findFirst().orElseThrow().getKey();
        Object actualEngine=get(graph,"engine");
        Field active=WalkerTestReflection.declaredField(actualEngine.getClass(), "activeTransport");active.setAccessible(true);active.set(actualEngine,selected);
        farmed[0]=false;graph.refreshForConfigIfNeeded();
        assertTrue(graph.consumeReplanRequest());
    }
    @Test void facadeReplanDropsThePinnedRouteAndRetainsTheFinalDestination() throws Exception {
        try(ApeAtollGreegreeIntegrationTest.Fixture fixture=new ApeAtollGreegreeIntegrationTest.Fixture()) {
            // Keep this recovery assertion synchronous while production publication is paced.
            set(fixture.walker,"routeSubmission",TestRouteSubmissions.immediate());
            RecoveryEngine recovering=new RecoveryEngine();
            WorldPoint target=recovering.destination;
            set(fixture.walker,"engine",recovering);set(fixture.walker,"engineClass",RecoveryEngine.class);
            NetworkTransportSupport networks=mock(NetworkTransportSupport.class);
            SpiritTreeSupport spirits=mock(SpiritTreeSupport.class);
            CatalogTeleportSupport teleports=mock(CatalogTeleportSupport.class);
            set(fixture.walker,"networkTransportSupport",networks);
            set(fixture.walker,"spiritTreeSupport",spirits);
            set(fixture.walker,"catalogTeleportSupport",teleports);
            Method replan=WalkerTestReflection.declaredMethod(EfficientWalker.class, "replanAfterSpiritTreeFailure");
            replan.setAccessible(true);replan.invoke(fixture.walker);
            assertEquals(target,recovering.destination,"Capture the final goal before cancel clears it");
            assertEquals(1,recovering.cancellations);assertEquals(1,recovering.plans);
            var order=inOrder(networks);
            order.verify(networks).clearCompleteRoute();
            order.verify(networks).refreshForPlanning(target);
            verify(spirits).planningFor(target);
            verify(spirits,never()).cancel();
        }
    }
    public static class RecoveryEngine {
        WorldPoint destination=new WorldPoint(1234,3700,0);
        int cancellations,plans;
        public WorldPoint getDestination(){return destination;}
        public void cancel(){cancellations++;destination=null;}
        public boolean startRouteTo(WorldPoint target){plans++;destination=target;return true;}
        public void shutdown(){}
    }
    private SpiritTreeSupport realGraph(EfficientWalkerConfig config) throws Exception {
        String shadow="net.runelite.client.plugins.microbot.droefficientwalker.engine";
        Constructor<?> constructor=Class.forName(EfficientWalker.class.getName()+"$NavigationEngineClassLoader")
            .getDeclaredConstructor(ClassLoader.class);
        constructor.setAccessible(true);
        ClassLoader loader=(ClassLoader)constructor.newInstance(getClass().getClassLoader());
        Class<?> type=Class.forName(shadow + ".WalkerEngine",true,loader);
        Object actualEngine=mock(type);
        Field transports=WalkerTestReflection.declaredField(type, "transports");transports.setAccessible(true);transports.set(actualEngine,List.of());
        SpiritTreeSupport graph=new SpiritTreeSupport(client,config,loader,type,actualEngine,shadow);
        graph.install();assertTrue((Boolean)get(graph,"installed"),"Use the real planner and maintained data");
        return graph;
    }
    private static String label(Object edge) {
        try{return (String)get(edge,"destinationLabel");}catch(Exception ex){throw new AssertionError(ex);}
    }
    static Object get(Object object,String name)throws Exception {Field f=WalkerTestReflection.declaredField(object.getClass(), name);f.setAccessible(true);return f.get(object);}
    static void set(Object object,String name,Object value)throws Exception {Field f=WalkerTestReflection.declaredField(object.getClass(), name);f.setAccessible(true);f.set(object,value);}
}
