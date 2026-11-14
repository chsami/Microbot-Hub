package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;

import java.util.*;
import java.lang.reflect.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TzhaarAccessTest {
    @Test void efficientWalkersImportedCatalogueContainsRecordedVolcanoCaveAndVentDoor() throws Exception {
        var fixture=new NetworkGraphRegressionTest();fixture.setup();
        Field field=WalkerTestReflection.declaredField(NetworkGraphRegressionTest.class, "catalog");field.setAccessible(true);
        @SuppressWarnings("unchecked") List<Transport> catalog=(List<Transport>)field.get(fixture);
        List<Transport> access=new ArrayList<>();for(Transport t:catalog)if(TzhaarAccess.isLink(t))access.add(t);
        for(int id:new int[]{11441,18969,11835,11836,30266})assertTrue(access.stream().anyMatch(t->t.getObjectId()==id),"Missing recorded access object "+id);
        assertTrue(access.stream().anyMatch(t->t.getOrigin().equals(new WorldPoint(2474,5138,0))&&t.getDestination().equals(new WorldPoint(2474,5136,0))));
        assertTrue(access.stream().anyMatch(t->t.getOrigin().equals(new WorldPoint(2474,5136,0))&&t.getDestination().equals(new WorldPoint(2474,5138,0))));
        assertFalse(TzhaarAccess.isLink(new Transport(new WorldPoint(3163,3485,0),new WorldPoint(2474,5136,0),"Wrong",TransportType.TRANSPORT,true,"Pass","Hot vent door",30266,2)));
    }
    @Test void collisionGraphCanReturnFromKaramjaToTheMorUlRekBank() throws Exception {
        var graph=TransportResources.loadAllFromResources();
        var config=new PathfinderConfig(SplitFlagMap.fromResources(),graph,List.of(),null,null);
        Field cutoff=WalkerTestReflection.declaredField(PathfinderConfig.class, "calculationCutoffMillis");cutoff.setAccessible(true);cutoff.setLong(config,5000L);
        graph.forEach((p,rows)->{if(p!=null){config.getTransports().put(p,rows);config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(p),rows);}});
        Method method=WalkerTestReflection.declaredMethod(Rs2PathApi.class, "planWithConfig",Rs2RouteRequest.class,PathfinderConfig.class);method.setAccessible(true);
        WorldPoint target=new WorldPoint(2543,5141,0);
        var route=(Rs2RouteResult)method.invoke(null,Rs2RouteRequest.to(new WorldPoint(2918,3176,0),target),config);
        assertTrue(route.isTargetReached(0),route.getTerminationReason().toString());assertFalse(route.getTransportSteps().isEmpty());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2918,3176","2399,5177"})
    void efficientWalkerPlannerUsesGroupingLandingOrKaramjaToReachTheInnerBank(int x,int y) throws Exception {
        var fixture=new NetworkGraphRegressionTest();fixture.setup();
        Field catalogField=WalkerTestReflection.declaredField(NetworkGraphRegressionTest.class, "catalog");catalogField.setAccessible(true);
        @SuppressWarnings("unchecked") List<Transport> catalog=(List<Transport>)catalogField.get(fixture);
        Field bridgeField=WalkerTestReflection.declaredField(NetworkGraphRegressionTest.class, "bridge");bridgeField.setAccessible(true);
        Field convertField=WalkerTestReflection.declaredField(NetworkGraphRegressionTest.class, "convert");convertField.setAccessible(true);
        Field builderField=WalkerTestReflection.declaredField(NetworkGraphRegressionTest.class, "buildPlanner");builderField.setAccessible(true);
        Object bridge=bridgeField.get(fixture);Method convert=(Method)convertField.get(fixture),builder=(Method)builderField.get(fixture);
        List<Object> edges=new ArrayList<>();WorldPoint start=new WorldPoint(x,y,0),target=new WorldPoint(2543,5141,0);
        for(Transport t:catalog)if(t.getOrigin()!=null){Object edge=convert.invoke(bridge,t,start);if(edge!=null)edges.add(edge);}
        Object planner=builder.invoke(null,edges,(java.util.function.Predicate<Object>)edge->true);
        Method plan=WalkerTestReflection.declaredMethod(planner.getClass(), "planRoute",WorldPoint.class,Collection.class,Set.class,Set.class,Set.class);plan.setAccessible(true);
        Object result=plan.invoke(planner,start,List.of(target),Set.of(),Set.of(),new HashSet<>(edges));
        Field pathField=WalkerTestReflection.declaredField(result.getClass(), "path");pathField.setAccessible(true);
        @SuppressWarnings("unchecked") List<WorldPoint> path=(List<WorldPoint>)pathField.get(result);
        assertFalse(path.isEmpty());assertEquals(target,path.get(path.size()-1));
        if(y<4000)assertTrue(path.contains(new WorldPoint(2480,5175,0)),"Karamja route must use the recorded cave landing");
        assertTrue(path.contains(new WorldPoint(2474,5136,0)),"Route must cross the recorded vent door");
    }
}
