package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.*;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrashIslandTransportTest {
    @Test void screenshotTileConnectsWaydarAndLumdoAndReplacesTheLegacyObjectRows() {
        var graph=TransportResources.loadAllFromResources();
        var crash=graph.get(new WorldPoint(2896,2726,0));
        assertNotNull(crash);
        assertTrue(crash.stream().anyMatch(t->t.getObjectId()==1446&&t.getType()==TransportType.NPC
            &&t.getDestination().equals(new WorldPoint(2393,3465,0))));
        assertTrue(crash.stream().anyMatch(t->t.getObjectId()==1454&&t.getType()==TransportType.NPC
            &&t.getDestination().equals(new WorldPoint(2803,2706,0))));
        var rows=graph.values().stream().flatMap(Collection::stream).filter(t->
            ApeAtollTransports.replacesTravel(t.getObjectId(),t.getOrigin())).collect(java.util.stream.Collectors.toList());
        assertEquals(5,rows.size());
        assertTrue(rows.stream().allMatch(t->t.getType()==TransportType.NPC&&"Travel".equals(t.getAction())));
        assertTrue(rows.stream().anyMatch(t->t.getOrigin().equals(new WorldPoint(2649,4518,0))
            &&t.getDestination().equals(new WorldPoint(2894,2726,0))));
        assertFalse(ApeAtollTransports.crashIsland(new WorldPoint(3165,3487,0)));
    }
    @Test void theRealCollisionMapPlansFromTheScreenshotTileToTheGrandTreeBankAndApeAtoll()throws Exception {
        var loaded=TransportResources.loadAllFromResources();
        Map<WorldPoint,Set<Transport>> graph=new HashMap<>();
        loaded.forEach((point,rows)->{if(point!=null)graph.put(point,new HashSet<>(rows));});
        var config=new PathfinderConfig(SplitFlagMap.fromResources(),graph,List.of(),null,null);
        var mapField=WalkerTestReflection.declaredField(PathfinderConfig.class, "map");mapField.setAccessible(true);
        ThreadLocal<?> collisionMap=(ThreadLocal<?>)mapField.get(config);
        try {
            var cutoff=WalkerTestReflection.declaredField(PathfinderConfig.class, "calculationCutoffMillis");cutoff.setAccessible(true);cutoff.setLong(config,3000);
            graph.forEach((point,rows)->{config.getTransports().put(point,rows);config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(point),rows);});
            var plan=WalkerTestReflection.declaredMethod(Rs2PathApi.class, "planWithConfig",Rs2RouteRequest.class,PathfinderConfig.class);plan.setAccessible(true);
            for(WorldPoint[] pair:List.of(new WorldPoint[]{new WorldPoint(2895,2726,0),new WorldPoint(2445,3425,1)},
                    new WorldPoint[]{ApeAtollTransports.CRASH_ISLAND,ApeAtollTransports.APE_ATOLL_LUMDO},
                    new WorldPoint[]{ApeAtollTransports.APE_ATOLL_LUMDO,ApeAtollTransports.CRASH_ISLAND})) {
                var route=(Rs2RouteResult)plan.invoke(null,Rs2RouteRequest.to(pair[0],pair[1]),config);
                assertTrue(route.isTargetReached(0),pair[0]+" -> "+pair[1]+": "+route.getTerminationReason());
                assertFalse(route.getTransportSteps().isEmpty());
                if(pair[1].getPlane()==1)assertEquals(ApeAtollTransports.GRAND_TREE_LANDING,route.getTransportSteps().get(0).getTo());
                else if(pair[1].equals(ApeAtollTransports.APE_ATOLL_LUMDO))assertEquals(ApeAtollTransports.APE_ATOLL_LUMDO,route.getTransportSteps().get(0).getTo());
            }
        } finally {
            // The JUnit worker outlives this config; do not leave its full map and last node chain there.
            collisionMap.remove();
        }
    }
    @Test void oldWaydarSceneryRowFailsButTheNpcTravelRowUsesTheObservedActionAndLanding() throws Exception {
        try(var f=new Fixture();var npcs=mockStatic(Rs2Npc.class);var objects=mockStatic(SceneObjects.class)) {
            var old=new Transport(ApeAtollTransports.CRASH_ISLAND,ApeAtollTransports.GRAND_TREE_LANDING,
                "Grand Tree",TransportType.TRANSPORT,true,"Travel","Waydar",1446,4);
            assertFalse((Boolean)call(f.bridge,"executeTerminalCatalogTransport",Transport.class,old));
            npcs.verifyNoInteractions();
            var waydar=f.mockOwned(Rs2NpcModel.class);
            npcs.when(()->Rs2Npc.getNpc(1446)).thenReturn(waydar);
            npcs.when(()->Rs2Npc.getAvailableAction(waydar,Collections.singletonList("Travel"))).thenReturn("Travel");
            npcs.when(()->Rs2Npc.interact(waydar,"Travel")).thenAnswer(i->{f.here.set(ApeAtollTransports.GRAND_TREE_LANDING);return true;});
            var fixed=ApeAtollTransports.create().stream().filter(t->t.getObjectId()==1446
                &&t.getOrigin().equals(ApeAtollTransports.CRASH_ISLAND)).findFirst().orElseThrow();
            assertTrue((Boolean)call(f.bridge,"executeTerminalCatalogTransport",Transport.class,fixed));
            npcs.verify(()->Rs2Npc.interact(waydar,"Travel"),times(1));
        }
    }
    @Test void crashIslandFallbackDismissesStaleTravelDialogueAndCompletesTheMinigameWithoutRestart() throws Exception {
        try(var f=new Fixture();var bank=mockStatic(net.runelite.client.plugins.microbot.util.bank.Rs2Bank.class);var dialogue=mockStatic(Rs2Dialogue.class);var keys=mockStatic(Rs2Keyboard.class);
            var tabs=mockStatic(Rs2Tab.class);var widgets=mockStatic(Rs2Widget.class)) {
            var talking=new AtomicBoolean(true);
            dialogue.when(Rs2Dialogue::isInDialogue).thenAnswer(i->talking.get());
            keys.when(()->Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE)).thenAnswer(i->{talking.set(false);return null;});
            tabs.when(Rs2Tab::getCurrentTab).thenReturn(InterfaceTab.CHAT);
            var grouping=f.mockOwned(Widget.class);when(grouping.getOnOpListener()).thenReturn(new Object[]{489,0,0});
            var selected=f.mockOwned(Widget.class);when(selected.getText()).thenReturn("rat pits");var teleport=f.mockOwned(Widget.class);
            widgets.when(()->Rs2Widget.getWidget(46333957)).thenReturn(grouping);
            widgets.when(()->Rs2Widget.getWidget(4980747)).thenReturn(selected);
            widgets.when(()->Rs2Widget.getWidget(4980768)).thenReturn(teleport);
            widgets.when(()->Rs2Widget.clickWidget(teleport)).thenReturn(false,true);
            dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(true);
            dialogue.when(()->Rs2Dialogue.clickOption("Ardougne")).thenAnswer(i->{f.here.set(new WorldPoint(2565,3321,0));return true;});
            Object edge=minigame(new WorldPoint(2895,2726,0));
            assertEquals("SUCCESS",call(f.bridge,"executeMinigame",edge.getClass(),edge).toString());
            keys.verify(()->Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE),times(1));
            widgets.verify(()->Rs2Widget.clickWidget(teleport),times(2));
        }
    }
    @Test void aPersistentCrashIslandUiFailureHasOnlyOneRetry()throws Exception {
        try(var f=new Fixture();var bank=mockStatic(net.runelite.client.plugins.microbot.util.bank.Rs2Bank.class);var dialogue=mockStatic(Rs2Dialogue.class);var tabs=mockStatic(Rs2Tab.class);var widgets=mockStatic(Rs2Widget.class)) {
            tabs.when(Rs2Tab::getCurrentTab).thenReturn(InterfaceTab.CHAT);
            var grouping=f.mockOwned(Widget.class);when(grouping.getOnOpListener()).thenReturn(new Object[]{489,0,0});
            var selected=f.mockOwned(Widget.class);when(selected.getText()).thenReturn("rat pits");var teleport=f.mockOwned(Widget.class);
            widgets.when(()->Rs2Widget.getWidget(46333957)).thenReturn(grouping);
            widgets.when(()->Rs2Widget.getWidget(4980747)).thenReturn(selected);
            widgets.when(()->Rs2Widget.getWidget(4980768)).thenReturn(teleport);
            widgets.when(()->Rs2Widget.clickWidget(teleport)).thenReturn(false);
            Object edge=minigame(new WorldPoint(2895,2726,0));
            assertEquals("FAILED",call(f.bridge,"executeMinigame",edge.getClass(),edge).toString());
            widgets.verify(()->Rs2Widget.clickWidget(teleport),times(2));
        }
    }
    @Test void ordinaryMinigameRequestsKeepTheirExistingDialogueGuard() throws Exception {
        try(var f=new Fixture();var dialogue=mockStatic(Rs2Dialogue.class);var keys=mockStatic(Rs2Keyboard.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(true);
            Object edge=minigame(new WorldPoint(3165,3487,0));
            assertEquals("FAILED",call(f.bridge,"executeMinigame",edge.getClass(),edge).toString());
            keys.verifyNoInteractions();
        }
    }
    static Object minigame(WorldPoint origin)throws Exception {
        Class<?> type=Class.forName(NetworkTransportSupport.class.getName()+"$EdgeType");
        Class<?> edge=Class.forName(NetworkTransportSupport.class.getName()+"$NetworkEdge");
        var ctor=edge.getDeclaredConstructor(type,WorldPoint.class,WorldPoint.class,String.class,String.class,int.class,int.class);ctor.setAccessible(true);
        return ctor.newInstance(Enum.valueOf((Class)type,"MINIGAME"),origin,new WorldPoint(2561,3319,0),"Rat Pits: Ardougne","Grouping",-1,0);
    }
    static Object call(Object target,String name,Class<?> type,Object value)throws Exception {
        var m=WalkerTestReflection.declaredMethod(target.getClass(), name,type);m.setAccessible(true);return m.invoke(target,value);
    }
    static final class Fixture implements AutoCloseable {
        private final List<Object> ownedMocks=new ArrayList<>();
        final AtomicReference<WorldPoint> here=new AtomicReference<>(ApeAtollTransports.CRASH_ISLAND);
        Client client;
        org.mockito.MockedStatic<Microbot> micro;
        NetworkTransportSupport bridge;
        Fixture(){
            try {
                client=mockOwned(Client.class);
                micro=mockStatic(Microbot.class,withSettings().stubOnly());
                var actor=mockOwned(Player.class);when(actor.getWorldLocation()).thenAnswer(i->here.get());when(client.getLocalPlayer()).thenReturn(actor);
                var thread=mockOwned(ClientThread.class);when(thread.invoke(any(Supplier.class))).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
                when(thread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
                micro.when(Microbot::getClientThread).thenReturn(thread);micro.when(Microbot::getClient).thenReturn(client);
                bridge=new NetworkTransportSupport(client,mockOwned(EfficientWalkerConfig.class),getClass().getClassLoader(),Object.class,new Object(),"unused",mockOwned(CatalogTeleportSupport.class));
            } catch(RuntimeException | Error failure) {
                try { close(); } catch(RuntimeException | Error cleanupFailure) {
                    if(cleanupFailure!=failure) failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }
        <T> T mockOwned(Class<T> type) {
            // These objects supply state only; interaction assertions use the separate static mocks.
            T value=mock(type,withSettings().stubOnly());
            ownedMocks.add(value);
            return value;
        }
        public void close(){
            Throwable failure=null;
            if(bridge!=null) failure=cleanup(failure,bridge::shutdown);
            bridge=null;
            if(micro!=null) failure=cleanup(failure,micro::closeOnDemand);
            micro=null;
            if(!ownedMocks.isEmpty()) failure=cleanup(failure,()->clearInvocations(ownedMocks.toArray()));
            // Inline-mock Answers can capture this fixture and its mocks. Drop only our handlers.
            for(Object value:ownedMocks) failure=cleanup(failure,()->framework().clearInlineMock(value));
            ownedMocks.clear();
            if(failure instanceof Error) throw (Error)failure;
            if(failure instanceof RuntimeException) throw (RuntimeException)failure;
            if(failure!=null) throw new AssertionError("Failed to clean up Crash Island fixture",failure);
        }
        private static Throwable cleanup(Throwable failure,Runnable action) {
            try { action.run(); }
            catch(Throwable next) {
                if(failure==null) return next;
                if(next!=failure) failure.addSuppressed(next);
            }
            return failure;
        }
    }
}
