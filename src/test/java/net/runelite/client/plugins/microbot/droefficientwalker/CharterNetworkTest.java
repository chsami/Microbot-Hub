package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CharterNetworkTest {
    private List<Transport> catalog() {
        List<Transport> list = new ArrayList<>();
        TransportResources.loadAllFromResources().values().forEach(list::addAll);
        return list;
    }
    private Transport leg(List<Transport> rows, WorldPoint from, String destination) {
        return rows.stream().filter(row -> row.getType() == TransportType.CHARTER_SHIP
            && from.equals(row.getOrigin()) && destination.equals(row.getDisplayInfo()))
            .findFirst().orElseThrow(AssertionError::new);
    }
    private NetworkTransportSupport bridge(Client client) {
        return new NetworkTransportSupport(client, mock(EfficientWalkerConfig.class), getClass().getClassLoader(),
            Object.class, new Object(), "unused", mock(CatalogTeleportSupport.class));
    }
    @Test public void everyAvailableDirectedCharterHasOneSurfaceEndpointAndAccessRequirements() {
        List<Transport> rows = catalog(); Set<String> pairs = new HashSet<>(), ports = new HashSet<>();
        for (Transport row : rows) {
            if (row.getType() != TransportType.CHARTER_SHIP) continue;
            assertTrue(pairs.add(row.getOrigin()+"->"+row.getDestination()), "No duplicate directed edge");
            ports.add(row.getDisplayInfo());
            assertEquals(0, row.getOrigin().getPlane()); assertEquals(0,row.getDestination().getPlane());
            assertTrue(TransportExecutionRegistry.canExecute(row)); assertTrue(row.getCurrencyAmount()>0);
        }
        assertEquals(404,pairs.size()); assertEquals(21,ports.size());
        Transport catherby = leg(rows,new WorldPoint(2792,3414,0),"The Pandemonium");
        assertEquals(1200,catherby.getCurrencyAmount());
        assertEquals(QuestState.FINISHED,catherby.getQuests().get(Quest.PANDEMONIUM));
        Transport summer = leg(rows,new WorldPoint(3063,3000,0),"The Summer Shore");
        assertEquals(1600,summer.getCurrencyAmount()); assertEquals(GreatConchTransports.DOCK,summer.getDestination());
        assertEquals(QuestState.FINISHED,summer.getQuests().get(Quest.PANDEMONIUM));
        assertEquals(QuestState.FINISHED,summer.getQuests().get(Quest.TROUBLED_TORTUGANS));
        Transport deep = leg(rows,new WorldPoint(2792,3414,0),"Deepfin Point");
        assertEquals(67,deep.getSkillLevels()[Skill.SAILING.ordinal()]);
        assertTrue(deep.getVarbits().stream().anyMatch(v -> v.getVarbitId()==19566));
        // A route away from a gated port must retain its origin unlock too.
        Transport back = leg(rows,deep.getDestination(),"Catherby");
        assertEquals(67,back.getSkillLevels()[Skill.SAILING.ordinal()]);
        assertTrue(back.getVarbits().stream().anyMatch(v -> v.getVarbitId()==19566));
    }
    @Test public void shipyardDeckStartsCanReachTheSurfaceBeforePlanningAnotherCharter() throws Exception {
        Transport exit = CharterTransportCatalog.deckExits().stream().filter(row -> row.getOrigin()
            .equals(new WorldPoint(2998,3032,1))).findFirst().orElseThrow(AssertionError::new);
        assertEquals(new WorldPoint(3001,3032,0),exit.getDestination());
        assertTrue(CharterTransportCatalog.onDeck(new WorldPoint(3187,2365,1),GreatConchTransports.DOCK));
        assertFalse(CharterTransportCatalog.onDeck(new WorldPoint(2998,3032,1),GreatConchTransports.DOCK));
        // Real collision map and planner, no account mocks: a deck exit connects both planes.
        Map<WorldPoint,Set<Transport>> graph = new HashMap<>(); graph.put(exit.getOrigin(),Set.of(exit));
        PathfinderConfig config = new PathfinderConfig(SplitFlagMap.fromResources(),graph,List.of(),null,null);
        Field cutoff = WalkerTestReflection.declaredField(PathfinderConfig.class, "calculationCutoffMillis"); cutoff.setAccessible(true); cutoff.setLong(config,2000);
        config.getTransports().put(exit.getOrigin(),Set.of(exit));
        Field packed = WalkerTestReflection.declaredField(PathfinderConfig.class, "transportsPacked"); packed.setAccessible(true);
        ((PrimitiveIntHashMap<Set<Transport>>)packed.get(config)).put(WorldPointUtil.packWorldPoint(exit.getOrigin()),Set.of(exit));
        Method plan = WalkerTestReflection.declaredMethod(Rs2PathApi.class, "planWithConfig",Rs2RouteRequest.class,PathfinderConfig.class);
        plan.setAccessible(true);
        Rs2RouteResult result=(Rs2RouteResult)plan.invoke(null,Rs2RouteRequest.to(exit.getOrigin(),exit.getDestination()),config);
        assertTrue(result.isTargetReached(0), result.getTerminationReason()+" "+result.getPath());
        WorldPoint alternate = new WorldPoint(2999,3031,1);
        Rs2RouteResult shifted = (Rs2RouteResult)plan.invoke(null,Rs2RouteRequest.to(alternate,exit.getDestination()),config);
        assertTrue(shifted.isTargetReached(0), shifted.getTerminationReason()+" "+shifted.getPath());
        assertFalse(config.getTransports().containsKey(alternate));
        assertEquals(1, config.getTransports().get(exit.getOrigin()).size());
    }
    @Test public void realCollisionPlannerUsesDirectPortsInsteadOfTheKaramjaLoop() throws Exception {
        Map<WorldPoint,Set<Transport>> graph = new HashMap<>();
        for (Transport row : catalog()) if(row.getType()==TransportType.CHARTER_SHIP)
            graph.computeIfAbsent(row.getOrigin(),point->new HashSet<>()).add(row);
        PathfinderConfig config=new PathfinderConfig(SplitFlagMap.fromResources(),graph,List.of(),null,null);
        Field cutoff=WalkerTestReflection.declaredField(PathfinderConfig.class, "calculationCutoffMillis");cutoff.setAccessible(true);cutoff.setLong(config,3000);
        Field packed=WalkerTestReflection.declaredField(PathfinderConfig.class, "transportsPacked");packed.setAccessible(true);
        PrimitiveIntHashMap<Set<Transport>> byTile=(PrimitiveIntHashMap<Set<Transport>>)packed.get(config);
        graph.forEach((point,rows)->{config.getTransports().put(point,rows);byTile.put(WorldPointUtil.packWorldPoint(point),rows);});
        Method plan=WalkerTestReflection.declaredMethod(Rs2PathApi.class, "planWithConfig",Rs2RouteRequest.class,PathfinderConfig.class);plan.setAccessible(true);
        for(WorldPoint[] pair:List.of(new WorldPoint[]{new WorldPoint(2792,3414,0),new WorldPoint(3063,3000,0)},
            new WorldPoint[]{new WorldPoint(3063,3000,0),GreatConchTransports.DOCK})) {
            Rs2RouteResult route=(Rs2RouteResult)plan.invoke(null,Rs2RouteRequest.to(pair[0],pair[1]),config);
            assertTrue(route.isTargetReached(0), route.getTerminationReason()+" "+route.getPath());
            assertEquals(1,route.getTransportSteps().size());
            assertEquals(pair[0],route.getTransportSteps().get(0).getFrom());
            assertEquals(pair[1],route.getTransportSteps().get(0).getTo());
        }
    }
    @Test public void bankedFareAndActualPandemoniumArrivalProduceACompleteBankRoute() throws Exception {
        Map<WorldPoint,Set<Transport>> graph = new HashMap<>();
        Transport charter = leg(catalog(),new WorldPoint(2792,3414,0),"The Pandemonium");
        graph.put(charter.getOrigin(),new HashSet<>(Set.of(charter)));
        PathfinderConfig config = new PathfinderConfig(SplitFlagMap.fromResources(),graph,List.of(),null,null);
        Field cutoff=WalkerTestReflection.declaredField(PathfinderConfig.class, "calculationCutoffMillis");cutoff.setAccessible(true);cutoff.setLong(config,3000);
        graph.forEach((point,rows)->{config.getTransports().put(point,rows);config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(point),rows);});
        Method plan=WalkerTestReflection.declaredMethod(Rs2PathApi.class, "planWithConfig",Rs2RouteRequest.class,PathfinderConfig.class);plan.setAccessible(true);
        WorldPoint bank = new WorldPoint(3039,2999,0);
        Rs2RouteResult route=(Rs2RouteResult)plan.invoke(null,Rs2RouteRequest.to(charter.getOrigin(),bank),config);
        assertTrue(route.isTargetReached(0), route.getTerminationReason().toString());
        assertEquals(new WorldPoint(3063,3000,0),route.getTransportSteps().get(0).getTo());
        Rs2TransportLoadout supplies=TransportSupplyPlanner.supplies(TransportSupplyPlanner.edges(route),
            items->Rs2TransportLoadout.empty(),id->id==995?1200:0,id->0);
        assertTrue(supplies.isSatisfiable());assertEquals(Map.of(995,1200),supplies.getWithdrawals());
        assertFalse(TransportSupplyPlanner.supplies(TransportSupplyPlanner.edges(route),
            items->Rs2TransportLoadout.empty(),id->id==995?1199:0,id->0).isSatisfiable());
        WorldPoint corrected=DestinationCorrections.resolve(new WorldPoint(3062,2985,0));
        Rs2RouteResult approach=(Rs2RouteResult)plan.invoke(null,Rs2RouteRequest.to(charter.getOrigin(),corrected),config);
        assertTrue(approach.isTargetReached(0), approach.getTerminationReason().toString());
        Transport amulet=catalog().stream().filter(r->"Sailors' amulet: The Pandemonium".equals(r.getDisplayInfo())).findFirst().orElseThrow();
        assertEquals(new WorldPoint(3058,2975,0),amulet.getDestination());
    }
    @Test public void fareQuotesApplyEachDiscountOnceIncludingMandatoryMosDiscount() {
        WorldPoint c=new WorldPoint(2792,3414,0),p=new WorldPoint(3063,3000,0),s=GreatConchTransports.DOCK;
        assertEquals(1200,CharterFare.quote(c,p,1200,false,false));
        assertEquals(600,CharterFare.quote(c,p,1200,true,false));
        assertEquals(300,CharterFare.quote(c,p,1200,true,true));
        assertEquals(1400,CharterFare.quote(c,p,1200,true,false)+CharterFare.quote(p,s,1600,true,false));
        assertEquals(900,CharterFare.quote(new WorldPoint(3671,2931,0),s,900,true,false));
        assertEquals(450,CharterFare.quote(new WorldPoint(3671,2931,0),s,900,true,true));
    }
    @Test public void entireDiscountedCharterJourneyIsFundedBeforeDeparture() throws Exception {
        Method convert=WalkerTestReflection.declaredMethod(Rs2PathApi.class, "toTransportEdge",Transport.class,int.class);convert.setAccessible(true);
        List<Transport> rows=catalog();
        WorldPoint c=new WorldPoint(2792,3414,0),p=new WorldPoint(3063,3000,0);
        Transport first=leg(rows,c,"The Pandemonium"),second=leg(rows,p,"The Summer Shore");
        List<Rs2TransportEdge> legs=List.of((Rs2TransportEdge)convert.invoke(null,first,600),
            (Rs2TransportEdge)convert.invoke(null,second,800));
        Rs2TransportLoadout ready=TransportSupplyPlanner.supplies(legs,items->Rs2TransportLoadout.empty(),
            id->id==995?400:0,id->id==995?1000:0);
        assertTrue(ready.isSatisfiable());assertEquals(Map.of(995,400),ready.getWithdrawals());
        assertFalse(TransportSupplyPlanner.supplies(legs,items->Rs2TransportLoadout.empty(),
            id->id==995?399:0,id->id==995?1000:0).isSatisfiable());
    }
    @Test public void chargedAmuletDoesNotDependOnChargeDisplayButStillRequiresSelectedMarker() throws Exception {
        Client client=mock(Client.class); when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        CatalogTeleportSupport provider=new CatalogTeleportSupport(client,mock(EfficientWalkerConfig.class),getClass().getClassLoader(),Object.class,new Object(),"unused");
        Method check=WalkerTestReflection.declaredMethod(CatalogTeleportSupport.class, "requirementsPass",Transport.class);check.setAccessible(true);
        List<Transport> amulets=new ArrayList<>();
        for(Transport row:catalog()) if(row.getDisplayInfo()!=null&&row.getDisplayInfo().startsWith("Sailors' amulet:")) amulets.add(row);
        assertEquals(4,amulets.size());
        try(var micro=mockStatic(Microbot.class);var player=mockStatic(Rs2Player.class)) {
            for(Transport row:amulets) {
                micro.when(() -> Microbot.getVarbitValue(18161)).thenReturn(0);
                assertTrue(row.getItemRequirements().stream().allMatch(r ->
                    r.getAlternatives().containsKey(32399) && !r.getAlternatives().containsKey(32398)));
                assertFalse(row.getVarbits().stream().anyMatch(v -> v.getVarbitId()==18161));
                int marker=row.getVarbits().stream().filter(v->v.getVarbitId()!=18161).mapToInt(TransportVarbit::getVarbitId).findFirst().orElse(0);
                if(marker!=0) {
                    micro.when(()->Microbot.getVarbitValue(marker)).thenReturn(0);
                    assertEquals(false,check.invoke(provider,row));
                    micro.when(()->Microbot.getVarbitValue(marker)).thenReturn(1);
                }
                assertEquals(true, check.invoke(provider,row), row.getDisplayInfo());
            }
        }
    }
    @Test public void equippedAmuletIsAValidCarriedMethodWithoutInventoryCopy() throws Exception {
        CatalogTeleportSupport provider=new CatalogTeleportSupport(mock(Client.class),mock(EfficientWalkerConfig.class),getClass().getClassLoader(),Object.class,new Object(),"unused");
        Method carried=WalkerTestReflection.declaredMethod(CatalogTeleportSupport.class, "hasCarriedItem",int[].class);carried.setAccessible(true);
        try(var inv=mockStatic(Rs2Inventory.class);var worn=mockStatic(TravelEquipment.class)) {
            worn.when(()->TravelEquipment.isWearing(32399)).thenReturn(true);
            assertEquals(true,carried.invoke(provider,(Object)new int[]{32399}));
            worn.when(()->TravelEquipment.isWearing(32399)).thenReturn(false);
            assertEquals(false,carried.invoke(provider,(Object)new int[]{32399}));
            inv.when(()->Rs2Inventory.hasItem(32399)).thenReturn(true);
            assertEquals(true,carried.invoke(provider,(Object)new int[]{32399}));
        }
    }
    @Test public void wallGangplankDisembarksAndOnlyObservedSurfaceArrivalCompletes() throws Exception {
        disembark(true); disembark(false);
    }
    @Test public void pandemoniumScreenshotDeckIsRecognizedAndDisembarks() throws Exception {
        WorldPoint deck = new WorldPoint(3068,3002,1), dock = new WorldPoint(3063,3000,0);
        assertTrue(CharterTransportCatalog.onDeck(deck,dock));
        assertFalse(CharterTransportCatalog.onDeck(deck,new WorldPoint(3058,2975,0)));
        assertEquals(dock,CharterTransportCatalog.exitFrom(deck).getDestination());
        disembark(true,deck,dock,new WorldPoint(3065,3001,1));
    }
    private void disembark(boolean lands) throws Exception {
        disembark(lands,new WorldPoint(3187,2365,1),GreatConchTransports.DOCK,new WorldPoint(3187,2368,1));
    }
    private void disembark(boolean lands, WorldPoint deck, WorldPoint dock, WorldPoint gangplank) throws Exception {
        Client client=mock(Client.class); Player player=mock(Player.class);when(client.getLocalPlayer()).thenReturn(player);
        AtomicReference<WorldPoint> location=new AtomicReference<>(deck);
        when(player.getWorldLocation()).thenAnswer(i->location.get());
        ClientThread thread=mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
        Rs2TileObjectCache cache=mock(Rs2TileObjectCache.class);
        when(cache.query()).thenCallRealMethod();
        Rs2TileObjectModel plank=mock(Rs2TileObjectModel.class);
        when(plank.getName()).thenReturn("Gangplank");when(plank.getWorldLocation()).thenReturn(gangplank);
        when(cache.getStream()).thenAnswer(i->List.of(plank).stream());
        NetworkTransportSupport bridge=bridge(client);
        Method exit=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "disembark",WorldPoint.class);exit.setAccessible(true);
        try(var micro=mockStatic(Microbot.class);var objects=mockStatic(SceneObjects.class);var clicks=mockStatic(TransportObjectClick.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);micro.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            objects.when(()->SceneObjects.hasAction(plank,"Cross")).thenReturn(true);
            clicks.when(()->TransportObjectClick.click(plank,"Cross")).thenAnswer(i->{if(lands)location.set(dock);else Thread.currentThread().interrupt();return true;});
            assertEquals(lands?"SUCCESS":"FAILED",exit.invoke(bridge,dock).toString());
            clicks.verify(()->TransportObjectClick.click(plank,"Cross"), atLeastOnce());
        } finally { Thread.interrupted(); }
    }
}
