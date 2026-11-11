package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WildernessTransitTest {
    private static final WorldPoint GE = new WorldPoint(3165,3487,0);
    private static final WorldPoint ARCEUUS = new WorldPoint(1760,3853,0);
    private static final WorldPoint MLM = MotherlodeTransports.BANK_APPROACH;
    private static final WorldPoint DITCH_SAFE = new WorldPoint(3137,3520,0);
    private static final WorldPoint DITCH_WILD = new WorldPoint(3137,3523,0);
    private static final WorldPoint MAGE = new WorldPoint(3106,3559,0);
    private static final WorldPoint ABYSS = new WorldPoint(3035,4852,0);

    @Test void safeRoutesRejectBothRecordedShortcutsEvenWithWildernessEnabled() {
        for (WorldPoint target : List.of(ARCEUUS, MLM, new WorldPoint(2659,2676,0))) {
            assertFalse(WildernessTravelPolicy.routeAllowed(List.of(GE,DITCH_SAFE,DITCH_WILD,MAGE,ABYSS,target),target,true));
            assertFalse(WildernessTravelPolicy.routeAllowed(List.of(GE,new WorldPoint(3090,3475,0),new WorldPoint(3154,3924,0),target),target,true));
            assertTrue(WildernessTravelPolicy.routeAllowed(List.of(GE,target),target,true));
        }
    }

    @Test void escapeAndExplicitDestinationsAreDifferentFromTransit() {
        assertTrue(WildernessTravelPolicy.routeAllowed(List.of(MAGE,DITCH_WILD,DITCH_SAFE,GE),GE,false));
        assertFalse(WildernessTravelPolicy.routeAllowed(List.of(MAGE,DITCH_SAFE,DITCH_WILD,GE),GE,true));
        assertFalse(WildernessTravelPolicy.routeAllowed(List.of(DITCH_WILD,MAGE,GE),GE,true));
        assertTrue(WildernessTravelPolicy.routeAllowed(List.of(GE,DITCH_SAFE,DITCH_WILD,MAGE),MAGE,true));
        assertTrue(WildernessTravelPolicy.requiresWilderness(new WorldPoint(2273,4695,0)));
        assertFalse(WildernessTravelPolicy.requiresWilderness(new WorldPoint(3154,3638,0)));
        assertFalse(WildernessTravelPolicy.requiresWilderness(new WorldPoint(3410,9950,3)));
    }

    @Test void planningScopeRestoresAfterNestedQueriesAndAcrossWorkers() throws Exception {
        Object previous = WildernessTravelPolicy.beginPlanning(List.of(GE),List.of(ARCEUUS),true);
        try {
            assertTrue(WildernessTravelPolicy.plannerBlocked(DITCH_WILD,true));
            Object inner = WildernessTravelPolicy.beginPlanning(List.of(GE),List.of(MAGE),true);
            try { assertFalse(WildernessTravelPolicy.plannerBlocked(MAGE,true)); }
            finally { WildernessTravelPolicy.endPlanning(inner); }
            assertTrue(WildernessTravelPolicy.plannerBlocked(MAGE,true));
            var result = new java.util.concurrent.FutureTask<>(() -> WildernessTravelPolicy.plannerBlocked(MAGE,true));
            new Thread(result).start(); assertFalse(result.get());
        } finally { WildernessTravelPolicy.endPlanning(previous); }
        assertFalse(WildernessTravelPolicy.plannerBlocked(MAGE,true));
    }

    @Test void realPlannerRejectsCheapWildernessTransitBeforeChoosingItsSafeAlternative() throws Exception {
        Fixture f = new Fixture();
        for (WorldPoint target : List.of(ARCEUUS,MLM)) {
            List<Object> edges = new ArrayList<>();
            edges.add(f.edge(DITCH_SAFE,DITCH_WILD,"Cross","Wilderness Ditch",23271));
            edges.add(f.edge(MAGE,ABYSS,"Teleport","Mage of Zamorak",2581));
            edges.add(f.edge(ABYSS,target,"Exit","Rift",0));
            WorldPoint safeDeparture = new WorldPoint(3220,3417,0);
            edges.add(f.edge(safeDeparture,target,"Teleport","Safe travel",0));
            Object planner = f.build(edges);
            f.permission(true);
            for (WorldPoint start : List.of(GE,DITCH_SAFE)) {
                List<WorldPoint> path = f.path(planner,start,target,edges);
                assertFalse(path.isEmpty(),"Safe alternative must remain available");
                assertEquals(target,path.get(path.size()-1));
                assertTrue(path.contains(safeDeparture),"Must choose safe departure despite cheaper dangerous shortcut");
                assertFalse(WildernessTravelPolicy.containsWilderness(path));
            }
        }
    }

    @Test void safeDepartureDoesNotAcquireWildernessPermissionFromTeleportCandidates() throws Exception {
        Fixture f = new Fixture(); f.permission(true);
        List<Object> edges=List.of(f.edge(MAGE,ARCEUUS,"Teleport","Unsafe shortcut",0),
            f.edge(new WorldPoint(3220,3417,0),ARCEUUS,"Teleport","Safe travel",0));
        Object planner=f.build(edges);
        Method multi=WalkerTestReflection.declaredMethod(f.type, "planRoute",Collection.class,Collection.class,Set.class,Set.class,Set.class);multi.setAccessible(true);
        Object result=multi.invoke(planner,List.of(GE,MAGE),List.of(ARCEUUS),Set.of(),Set.of(),new HashSet<>(edges));
        Field field=WalkerTestReflection.declaredField(result.getClass(), "path");field.setAccessible(true);
        @SuppressWarnings("unchecked") List<WorldPoint> path=(List<WorldPoint>)field.get(result);
        assertFalse(path.isEmpty()); assertEquals(GE,path.get(0));
        assertFalse(WildernessTravelPolicy.containsWilderness(path));
    }

    @Test void virtualTeleportAndIntermediateWildDestinationCannotOverrideTheActualSafeJourney() throws Exception {
        Fixture f = new Fixture();f.permission(true);
        Field origin=f.type.getField("aioTravelOrigin"),target=f.type.getField("aioTravelTarget");
        origin.setAccessible(true);target.setAccessible(true);origin.set(null,GE);target.set(null,ARCEUUS);
        List<Object> edges=List.of(f.edge(MAGE,ARCEUUS,"Exit","Cheap shortcut",0));
        Object planner=f.build(edges);
        assertTrue(f.path(planner,MAGE,ARCEUUS,edges).isEmpty(),"Synthetic landing in Wilderness is not an escape from the actual safe origin");
        assertTrue(f.path(planner,GE,MAGE,edges).isEmpty(),"Intermediate Wild endpoint must not override the real safe destination");
    }

    @Test void plannerCanExitWildernessButDoesNotGrantSafeTargetsEntryPermission() throws Exception {
        Fixture f = new Fixture();
        List<Object> edges = List.of(f.edge(DITCH_WILD,DITCH_SAFE,"Cross","Wilderness Ditch",23271));
        Object planner = f.build(edges);
        f.permission(false);
        List<WorldPoint> path = f.path(planner,DITCH_WILD,GE,edges);
        assertFalse(path.isEmpty()); assertTrue(path.contains(DITCH_SAFE));
        assertTrue(WildernessTravelPolicy.routeAllowed(path,GE,false));
        assertTrue(f.path(planner,GE,MAGE,edges).isEmpty());
        f.permission(true);
        List<Object> entry = List.of(f.edge(DITCH_SAFE,DITCH_WILD,"Cross","Wilderness Ditch",23271));
        assertFalse(f.path(f.build(entry),GE,MAGE,entry).isEmpty());
    }

    private static final class Fixture {
        final Class<?> type;
        final Constructor<?> edge;
        final Method build,plan;
        Fixture() throws Exception {
            String shadow="net.runelite.client.plugins.microbot.droefficientwalker.engine";
            Constructor<?> ctor=Class.forName(EfficientWalker.class.getName()+"$NavigationEngineClassLoader").getDeclaredConstructor(ClassLoader.class);
            ctor.setAccessible(true);
            ClassLoader loader=(ClassLoader)ctor.newInstance(getClass().getClassLoader());
            Class<?> row=Class.forName(shadow + ".TransportCatalog$TransportEdge",true,loader);
            edge=row.getDeclaredConstructor(WorldPoint.class,WorldPoint.class,String.class,String.class,int.class,
                List.class,List.class,List.class,List.class,List.class,boolean.class,int[].class);edge.setAccessible(true);
            type=Class.forName(shadow + ".CollisionRoutePlanner",true,loader);
            build=WalkerTestReflection.declaredMethod(type, "create",List.class,Predicate.class);build.setAccessible(true);
            plan=WalkerTestReflection.declaredMethod(type, "planRoute",WorldPoint.class,Collection.class,Set.class,Set.class,Set.class);plan.setAccessible(true);
        }
        Object edge(WorldPoint from,WorldPoint to,String action,String name,int id) throws Exception {
            return edge.newInstance(from,to,action,name,id,List.of(),List.of(),List.of(),List.of(),List.of(),false,new int[0]);
        }
        void permission(boolean allowed) throws Exception {
            Field field=type.getField("allowWildernessTravel");field.setAccessible(true);field.setBoolean(null,allowed);
        }
        Object build(List<Object> edges) throws Exception {return build.invoke(null,edges,(Predicate<Object>)e->true);}
        @SuppressWarnings("unchecked") List<WorldPoint> path(Object planner,WorldPoint start,WorldPoint target,List<Object> edges) throws Exception {
            Object result=plan.invoke(planner,start,List.of(target),Set.of(),Set.of(),new HashSet<>(edges));
            Field path=WalkerTestReflection.declaredField(result.getClass(), "path");path.setAccessible(true);return (List<WorldPoint>)path.get(result);
        }
    }
}
