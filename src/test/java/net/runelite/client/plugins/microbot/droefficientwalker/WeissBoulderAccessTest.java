package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeissBoulderAccessTest {
    @Test void maintainedCatalogueAdmitsBothDirectionsWithTheExistingObjectExecutor() throws Exception {
        var fixture=new NetworkGraphRegressionTest();fixture.setup();
        var links=catalog(fixture).stream().filter(WeissBoulderAccess::isLink).collect(java.util.stream.Collectors.toList());
        assertEquals(2,links.size());
        assertTrue(links.stream().anyMatch(row->WeissBoulderAccess.EAST.equals(row.getOrigin())));
        assertTrue(links.stream().anyMatch(row->WeissBoulderAccess.WEST.equals(row.getOrigin())));
        for(var link:links)assertEquals(TransportExecutionRegistry.Executor.OBJECT,
                TransportExecutionRegistry.executorFor(link).orElseThrow());
        assertFalse(WeissBoulderAccess.isLink(edge(WeissBoulderAccess.EAST,new WorldPoint(2847,3934,0),"Cross")));
        assertFalse(WeissBoulderAccess.isLink(edge(WeissBoulderAccess.EAST,WeissBoulderAccess.WEST,"Climb")));
        assertFalse(WeissBoulderAccess.isLink(edge(new WorldPoint(2852,3936,1),WeissBoulderAccess.WEST,"Cross")));
    }

    @Test void plannerCrossesFromEitherApproach() throws Exception {
        var fixture=new NetworkGraphRegressionTest();fixture.setup();
        for(var endpoints:List.of(List.of(WeissBoulderAccess.EAST,WeissBoulderAccess.WEST),
                List.of(WeissBoulderAccess.WEST,WeissBoulderAccess.EAST))){
            var path=path(fixture,endpoints.get(0),endpoints.get(1));
            assertFalse(path.isEmpty());assertEquals(endpoints.get(1),path.get(path.size()-1));
            assertTrue(path.contains(endpoints.get(0)));assertTrue(path.contains(endpoints.get(1)));
        }
    }

    @Test void icyBasaltArrivalStaysOnThePatchSide() throws Exception {
        var fixture=new NetworkGraphRegressionTest();fixture.setup();
        var target=new WorldPoint(2847,3934,0);
        var path=path(fixture,new WorldPoint(2846,3938,0),target);
        assertFalse(path.isEmpty());assertEquals(target,path.get(path.size()-1));
        assertTrue(path.stream().allMatch(tile->tile.getX()<=2850),path.toString());
    }

    @Test void proximityOnTheApproachSideDoesNotAcknowledgeTheCrossing() throws Exception {
        var landed=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "sharedTransportLanded",WorldPoint.class,Transport.class);
        landed.setAccessible(true);
        for(var endpoints:List.of(List.of(WeissBoulderAccess.EAST,WeissBoulderAccess.WEST),
                List.of(WeissBoulderAccess.WEST,WeissBoulderAccess.EAST))){
            var transport=edge(endpoints.get(0),endpoints.get(1),"Cross");
            assertEquals(false,landed.invoke(null,endpoints.get(0),transport));
            assertEquals(false,landed.invoke(null,new WorldPoint(2851,3936,0),transport));
            assertEquals(true,landed.invoke(null,endpoints.get(1),transport));
        }
    }

    private static Transport edge(WorldPoint from,WorldPoint to,String action){
        return new Transport(from,to,"Weiss: Little boulder",TransportType.TRANSPORT,true,action,"Little boulder",33312,2);
    }
    @SuppressWarnings("unchecked") private static List<Transport> catalog(Object fixture)throws Exception{
        return (List<Transport>)field(fixture,"catalog");
    }
    @SuppressWarnings("unchecked") private static List<WorldPoint> path(Object fixture,WorldPoint from,WorldPoint to)throws Exception{
        var convert=(Method)field(fixture,"convert");var builder=(Method)field(fixture,"buildPlanner");
        var bridge=field(fixture,"bridge");List<Object> edges=new ArrayList<>();
        for(var transport:catalog(fixture))if(transport.getOrigin()!=null){
            var edge=convert.invoke(bridge,transport,from);if(edge!=null)edges.add(edge);
        }
        var planner=builder.invoke(null,edges,(java.util.function.Predicate<Object>)edge->true);
        var plan=WalkerTestReflection.declaredMethod(planner.getClass(), "planRoute",WorldPoint.class,Collection.class,Set.class,Set.class,Set.class);
        plan.setAccessible(true);
        var result=plan.invoke(planner,from,List.of(to),Set.of(),Set.of(),new HashSet<>(edges));
        return (List<WorldPoint>)field(result,"path");
    }
    private static Object field(Object target,String name)throws Exception{
        var field=WalkerTestReflection.declaredField(target.getClass(), name);field.setAccessible(true);return field.get(target);
    }
}
