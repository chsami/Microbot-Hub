package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class NetworkGraphRegressionTest
{
    private static final String SHADOW = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
    private static final WorldPoint START = new WorldPoint(2443, 3083, 0);
    private static final WorldPoint TARGET = new WorldPoint(2442, 3086, 0);
    private ClassLoader loader;
    private NetworkTransportSupport bridge;
    private Method convert;
    private Method buildPlanner;
    private Constructor<?> transportConstructor;
    private Class<?> transportClass;
    private Transport spell;
    private List<Transport> catalog;

    @BeforeEach public void setup() throws Exception
    {
        Class<?> loaderClass = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader");
        Constructor<?> loaderConstructor = loaderClass.getDeclaredConstructor(ClassLoader.class);
        loaderConstructor.setAccessible(true);
        loader = (ClassLoader) loaderConstructor.newInstance(EfficientWalker.class.getClassLoader());
        transportClass = Class.forName(SHADOW + ".TransportCatalog$TransportEdge", true, loader);
        transportConstructor = transportClass.getDeclaredConstructor(WorldPoint.class, WorldPoint.class,
            String.class, String.class, int.class, List.class, List.class, List.class, List.class,
            List.class, boolean.class, int[].class);
        transportConstructor.setAccessible(true);
        bridge = new NetworkTransportSupport(mock(Client.class), mock(EfficientWalkerConfig.class),
            loader, Object.class, new Object(), SHADOW, mock(CatalogTeleportSupport.class));
        setField(bridge, "transportCtor", transportConstructor);
        Constructor<?> skill = Class.forName(SHADOW + ".TransportCatalog$SkillRequirement", true, loader)
            .getDeclaredConstructor(Skill.class, int.class);
        skill.setAccessible(true);
        setField(bridge, "skillCtor", skill);
        Constructor<?> requirement = Class.forName(SHADOW + ".TransportCatalog$VarRequirement", true, loader)
            .getDeclaredConstructor(int.class, int.class, char.class);
        requirement.setAccessible(true);
        setField(bridge, "requirementCtor", requirement);
        convert = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "buildSharedCatalogTransport",
            Transport.class, WorldPoint.class);
        convert.setAccessible(true);
        Method loadCatalog = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "loadSharedCatalog");
        loadCatalog.setAccessible(true);
        catalog = (List<Transport>) loadCatalog.invoke(bridge);
        spell = catalog.stream().filter(t -> "Moonclan Teleport".equals(t.getDisplayInfo()))
            .findFirst().orElseThrow(AssertionError::new);
        buildPlanner = WalkerTestReflection.declaredMethod(Class.forName(SHADOW + ".CollisionRoutePlanner", true, loader), "create", List.class, Predicate.class);
        buildPlanner.setAccessible(true);
    }

    @Test public void duplicateCharterCopiesCannotBypassTheDedicatedExecutor() throws Exception
    {
        for (String[] binding : List.of(new String[]{"transportOriginField","origin"},
            new String[]{"transportDestinationField","destination"},new String[]{"transportActionField","action"},
            new String[]{"transportTargetField","target"},new String[]{"transportObjectIdField","objectId"})) {
            Field field=WalkerTestReflection.declaredField(transportClass, binding[1]); field.setAccessible(true);
            setField(bridge,binding[0],field);
        }
        Object old=transportConstructor.newInstance(new WorldPoint(2792,3414,0),new WorldPoint(3187,2365,1),
            "Charter","Trader Crewmember",1330,List.of(),List.of(),List.of(),List.of(),List.of(),false,new int[0]);
        Object alternative=transportConstructor.newInstance(new WorldPoint(2792,3414,0),new WorldPoint(3187,2368,0),
            "Charter","Trader Crewmember",1330,List.of(),List.of(),List.of(),List.of(),List.of(),false,new int[0]);
        List<Object> edges=new ArrayList<>(List.of(old,alternative));
        Method remove=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "removeKnownCustomTransportDuplicates",List.class);
        remove.setAccessible(true);assertEquals(2,remove.invoke(bridge,edges));assertTrue(edges.isEmpty());
        assertTrue(catalog.stream().noneMatch(row -> row.getType()==net.runelite.client.plugins.microbot.shortestpath.TransportType.CHARTER_SHIP));
    }

    @Test public void deepWildernessCreviceChecksCorrectDiaryInsteadOfLumbridgeTaskCount() throws Exception
    {
        Field id=WalkerTestReflection.declaredField(transportClass, "objectId");id.setAccessible(true);
        setField(bridge,"transportObjectIdField",id);
        Constructor<?> requirement=Class.forName(SHADOW + ".TransportCatalog$VarRequirement",true,loader)
            .getDeclaredConstructor(int.class,int.class,char.class);
        requirement.setAccessible(true);
        Object edge=transportConstructor.newInstance(new WorldPoint(3046,10326,0),new WorldPoint(3048,10336,0),
            "Use","Crevice",17030,List.of(),List.of(),List.of(requirement.newInstance(6312,1,'=')),
            List.of(),List.of(),true,new int[0]);
        Method correct=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "correctWildernessShortcutRequirements",List.class);
        correct.setAccessible(true);correct.invoke(bridge,new ArrayList<>(List.of(edge)));
        Method usable=WalkerTestReflection.declaredMethod(transportClass, "meetsRequirements",java.util.function.ToIntFunction.class,
            java.util.function.BiPredicate.class,java.util.function.IntUnaryOperator.class,
            java.util.function.IntUnaryOperator.class,Predicate.class);
        usable.setAccessible(true);
        java.util.function.ToIntFunction<Skill> skills=skill->99;
        java.util.function.BiPredicate<String,Integer> items=(name,count)->true;
        java.util.function.IntUnaryOperator varplayers=value->0;
        Predicate<net.runelite.api.Quest> quests=quest->true;
        assertEquals(false,usable.invoke(edge,skills,items,(java.util.function.IntUnaryOperator)value->value==6312?1:0,varplayers,quests));
        assertEquals(true,usable.invoke(edge,skills,items,(java.util.function.IntUnaryOperator)value->value==4467?1:0,varplayers,quests));
    }

    @Test public void unanchoredCatalogEntryReproducesScreenshotFailure() throws Exception
    {
        assertNull(spell.getOrigin());
        Object broken = transportConstructor.newInstance(null, spell.getDestination(), spell.getAction(),
            spell.getName(), spell.getObjectId(), List.of(), List.of(), List.of(), List.of(), List.of(),
            false, new int[0]);
        Method adjacent = WalkerTestReflection.declaredMethod(transportClass, "isUnrestrictedDoor");
        adjacent.setAccessible(true);
        try
        {
            adjacent.invoke(broken);
            fail("The engine should reproduce the null-origin failure");
        }
        catch (InvocationTargetException failure)
        {
            assertTrue(failure.getCause() instanceof NullPointerException);
            // Java 11 (the Hub's test JDK) has no helpful NullPointerException text to check.
            String message = failure.getCause().getMessage();
            assertTrue(message == null || message.contains("this.origin"));
        }
    }

    @Test public void castleWarsRouteLoadsCollisionMapWithFullSharedGraph() throws Exception
    {
        List<Object> edges = new ArrayList<>();
        for (Transport source : catalog)
        {
            Object edge = convert.invoke(bridge, source, START);
            assertNotNull(edge, source.getDisplayInfo());
            edges.add(edge);
        }
        assertTrue(edges.size() > 4000);
        Object anchored = convert.invoke(bridge, spell, START);
        assertEquals(START, field(anchored, "origin"));
        assertEquals(spell.getDestination(), field(anchored, "destination"));
        assertNull(spell.getOrigin(), "Do not mutate the shared catalogue");
        Object planner = buildPlanner.invoke(null, edges, (Predicate<Object>) edge -> true);
        assertTrue(((Map<?, ?>) field(planner, "collisionRegions")).size() > 100, "Real collision map loaded");
        Method plan = WalkerTestReflection.declaredMethod(planner.getClass(), "planRoute", WorldPoint.class,
            Collection.class, Set.class, Set.class, Set.class);
        plan.setAccessible(true);
        Object result = plan.invoke(planner, START, List.of(TARGET), Set.of(), Set.of(), new HashSet<>(edges));
        List<WorldPoint> path = (List<WorldPoint>) field(result, "path");
        assertFalse(path.isEmpty());
        assertEquals(START, path.get(0));
        assertEquals(TARGET, path.get(path.size() - 1));
        assertTrue(path.size() <= 5, "Nearby Castle Wars route should just walk");
    }

    @Test public void castleWarsToOuraniaUsesRealCollisionMapAndConfirmedLadder() throws Exception {
        Method nativeCatalog = WalkerTestReflection.declaredMethod(Class.forName(SHADOW + ".TransportCatalog", true, loader), "loadAllTransports");
        nativeCatalog.setAccessible(true);
        List<Object> edges = new ArrayList<>((List<?>) nativeCatalog.invoke(null));
        for (Transport route : catalog) {
            if (route.getOrigin() == null) continue;
            Object edge = convert.invoke(bridge, route, START);
            if (edge != null) edges.add(edge);
        }
        Object planner = buildPlanner.invoke(null, edges, (Predicate<Object>) edge -> true);
        Method plan = WalkerTestReflection.declaredMethod(planner.getClass(), "planRoute", WorldPoint.class,
            Collection.class, Set.class, Set.class, Set.class);
        plan.setAccessible(true);
        for (WorldPoint start : List.of(OuraniaTransports.CAVE_ENTRY, OuraniaTransports.ENTRANCE, new WorldPoint(2468, 3246, 0), START, new WorldPoint(2936,3281,0))) {
            Object result = plan.invoke(planner, start, List.of(OuraniaTransports.ALTAR), Set.of(), Set.of(), new HashSet<>(edges));
            List<WorldPoint> path = (List<WorldPoint>) field(result, "path");
            assertFalse(path.isEmpty(), "Ourania route from " + start + " failure=" + field(result, "failure"));
            assertEquals(OuraniaTransports.ALTAR, path.get(path.size() - 1));
            assertTrue(path.contains(OuraniaTransports.CAVE_ENTRY));
        }
        Object exitResult = plan.invoke(planner, OuraniaTransports.CAVE_ENTRY, List.of(new WorldPoint(2468,3246,0)), Set.of(), Set.of(), new HashSet<>(edges));
        List<WorldPoint> exitPath = (List<WorldPoint>) field(exitResult,"path");
        assertFalse(exitPath.isEmpty(), "Cave exit must connect back to surface travel");
        assertEquals(new WorldPoint(2468,3246,0),exitPath.get(exitPath.size()-1));
        assertEquals(4, catalog.stream().filter(OuraniaTransports::replaces).count());
    }

    @Test public void fixedEdgesStayFixedAndSpellWaitsForPlayerLocation() throws Exception
    {
        assertNull(convert.invoke(bridge, spell, null));
        Transport fixed = catalog.stream().filter(t -> t.getOrigin() != null).findFirst().get();
        assertEquals(fixed.getOrigin(), field(convert.invoke(bridge, fixed, null), "origin"));
    }

    @Test public void houseLegAnchorsToTheRawDepartureWithoutStoringAnInsidePoint() throws Exception {
        WorldPoint rawHouse = new WorldPoint(10910,13717,0);
        MaxPohSupport.Leg leg = new MaxPohSupport.Leg(new MaxPohSupport.Exit("BOX","Grand Exchange",
            new WorldPoint(3164,3487,0),null),MaxPohSupport.Entry.INSIDE);
        Object edge = convert.invoke(bridge,leg,rawHouse);
        assertEquals(rawHouse,field(edge,"origin"));
        assertEquals(leg.getDestination(),field(edge,"destination"));
        assertEquals("Max POH",field(edge,"action"));
        assertNull(leg.getOrigin());
        assertNull(convert.invoke(bridge,leg,null));
    }

    @Test public void spellExecutionDoesNotRequireAnObjectAtNullOrigin() throws Exception
    {
        Method canStart = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "sharedCanStartFrom",
            WorldPoint.class, Transport.class);
        canStart.setAccessible(true);
        assertEquals(true, canStart.invoke(null, START, spell));
        assertEquals(false, canStart.invoke(null, null, spell));
        Transport fixed = catalog.stream().filter(t -> t.getOrigin() != null
            && t.getOrigin().distanceTo(START) > 10).findFirst().get();
        assertEquals(false, canStart.invoke(null, START, fixed));
        assertEquals(true, canStart.invoke(null, fixed.getOrigin(), fixed));
    }

    private static Object field(Object object, String name) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void setField(Object object, String name, Object value) throws Exception
    {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
