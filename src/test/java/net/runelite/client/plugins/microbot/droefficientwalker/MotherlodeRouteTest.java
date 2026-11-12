package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotherlodeRouteTest
{
    @Test void recordedAccessKeepsMiningGuildRequirementAndOtherLadders() {
        List<Transport> routes = MotherlodeTransports.create();
        assertEquals(6, routes.size());
        assertEquals(60, routes.stream().filter(t -> t.getObjectId() == 30367).findFirst().orElseThrow()
            .getSkillLevels()[Skill.MINING.ordinal()]);
        assertEquals(0, routes.stream().filter(t -> t.getObjectId() == 17385).findFirst().orElseThrow()
            .getSkillLevels()[Skill.MINING.ordinal()]);
        for (Transport route : routes) assertTrue(NetworkTransportSupport.isUndergroundObjectTransport(route));
        Transport elsewhere = new Transport(new WorldPoint(3200, 3200, 0), new WorldPoint(3200, 9600, 0),
            "Other ladder", TransportType.TRANSPORT, true, "Climb-down", "Ladder", 30367);
        assertFalse(MotherlodeTransports.replaces(elsewhere));
    }

    @Test void realCollisionPlannerConnectsBothApproachesAndReturnsThroughTunnel() throws Exception {
        String shadow = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
        Constructor<?> loaderCtor = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader")
            .getDeclaredConstructor(ClassLoader.class);
        loaderCtor.setAccessible(true);
        ClassLoader loader = (ClassLoader) loaderCtor.newInstance(getClass().getClassLoader());
        Class<?> edgeClass = Class.forName(shadow + ".TransportCatalog$TransportEdge", true, loader);
        Constructor<?> edgeCtor = edgeClass.getDeclaredConstructor(WorldPoint.class, WorldPoint.class,
            String.class, String.class, int.class, List.class, List.class, List.class, List.class, List.class, boolean.class, int[].class);
        edgeCtor.setAccessible(true);
        List<Object> edges = new ArrayList<>();
        Object ladder = null;
        for (Transport t : MotherlodeTransports.create()) {
            Object edge = edgeCtor.newInstance(t.getOrigin(), t.getDestination(), t.getAction(), t.getName(), t.getObjectId(),
                List.of(), List.of(), List.of(), List.of(), List.of(), false, new int[0]);
            edges.add(edge);
            if (t.getObjectId() == net.runelite.api.gameval.ObjectID.MOTHERLODE_LADDER_TOP) {
                assertEquals("Climb-down", t.getAction());
                assertEquals(new WorldPoint(3755, 5675, 0), t.getOrigin());
                assertEquals(new WorldPoint(3754, 5672, 0), t.getDestination());
                ladder = edge;
            }
        }
        Class<?> plannerClass = Class.forName(shadow + ".CollisionRoutePlanner", true, loader);
        Method build = WalkerTestReflection.declaredMethod(plannerClass, "create", List.class, Predicate.class); build.setAccessible(true);
        Object planner = build.invoke(null, edges, (Predicate<Object>) e -> true);
        Method plan = WalkerTestReflection.declaredMethod(plannerClass, "planRoute", WorldPoint.class, Collection.class, Set.class, Set.class, Set.class);
        plan.setAccessible(true);
        for (WorldPoint[] segment : List.of(
            new WorldPoint[]{new WorldPoint(2966,3379,0), MotherlodeTransports.FALADOR_STAIRS},
            new WorldPoint[]{MotherlodeTransports.DWARVEN_STAIRS, MotherlodeTransports.CAVE_APPROACH},
            new WorldPoint[]{MotherlodeTransports.SKILLS_NECKLACE_LANDING, MotherlodeTransports.CAVE_APPROACH},
            new WorldPoint[]{MotherlodeTransports.MINE_ENTRY, MotherlodeTransports.BANK_APPROACH})) {
            List<WorldPoint> p = path(plan,planner,segment[0],segment[1],edges);
            assertFalse(p.isEmpty(), "Recorded route segment must connect " + segment[0] + " -> " + segment[1]);
        }
        for (WorldPoint start : List.of(new WorldPoint(2966, 3379, 0), MotherlodeTransports.FALADOR_STAIRS,
            MotherlodeTransports.SKILLS_NECKLACE_LANDING, MotherlodeTransports.MINE_ENTRY)) {
            List<WorldPoint> path = path(plan, planner, start, MotherlodeTransports.BANK_APPROACH, edges);
            assertFalse(path.isEmpty(), "No MLM route from " + start);
            assertEquals(MotherlodeTransports.BANK_APPROACH, path.get(path.size() - 1));
            if (!MotherlodeTransports.inMine(start)) assertTrue(path.contains(MotherlodeTransports.MINE_ENTRY));
            assertTrue(path.stream().anyMatch(MotherlodeTransports.ENTRANCE_ROCKFALLS::contains),
                "Entrance corridor must use one of the observed clearable tiles");
        }
        List<WorldPoint> exit = path(plan, planner, MotherlodeTransports.BANK_APPROACH, MotherlodeTransports.CAVE_APPROACH, edges);
        assertFalse(exit.isEmpty());
        assertEquals(MotherlodeTransports.CAVE_APPROACH, exit.get(exit.size() - 1));

        WorldPoint upperHopper = new WorldPoint(3754, 5677, 0);
        List<WorldPoint> bank = path(plan, planner, upperHopper, MotherlodeTransports.BANK_APPROACH, edges);
        assertFalse(bank.isEmpty(), "The logged upper-hopper tile must be able to reach the bank");
        assertTrue(bank.contains(new WorldPoint(3755, 5675, 0)), "Use the upper ladder approach");
        assertTrue(bank.contains(new WorldPoint(3754, 5672, 0)), "Descend to a walkable lower approach");
        assertNotNull(ladder);
        List<Object> withoutDescent = new ArrayList<>(edges);
        withoutDescent.remove(ladder);
        Object disconnected = build.invoke(null, withoutDescent, (Predicate<Object>) e -> true);
        assertTrue(path(plan, disconnected, upperHopper, MotherlodeTransports.BANK_APPROACH, withoutDescent).isEmpty(),
            "The two levels must remain separated by collision when the ladder is unavailable");
    }

    @Test void bothPlannerCataloguesUseRecordedAccessAndOnlyRecordedCollisionEdgesChange() throws Exception {
        var catalogue = net.runelite.client.plugins.microbot.droefficientwalker.support.TransportResources.loadAllFromResources();
        for (Transport t : MotherlodeTransports.create()) {
            assertTrue(catalogue.get(t.getOrigin()).stream().anyMatch(edge -> edge.getObjectId() == t.getObjectId()
                && edge.getDestination().equals(t.getDestination())));
        }
        String resource = "/net/runelite/client/plugins/microbot/shortestpath/collision-map.zip";
        Map<String, byte[]> before = entries(getClass().getResourceAsStream(resource));
        Map<String, byte[]> after = entries(new java.io.ByteArrayInputStream(
            MotherlodeCollisionMap.patch(getClass().getResourceAsStream(resource))));
        assertEquals(before.keySet(), after.keySet());
        Map<String, Integer> changedBits = new HashMap<>();
        for (String region : before.keySet()) {
            byte[] a = before.get(region), b = after.get(region); assertEquals(a.length, b.length);
            if (!Set.of("58_88", "33_53").contains(region)) assertArrayEquals(a, b, "Unrelated map region " + region);
            int bits = 0;
            for (int i = 0; i < a.length; i++) bits += Integer.bitCount((a[i] ^ b[i]) & 255);
            if (bits > 0) changedBits.put(region, bits);
        }
        assertEquals(Map.of("58_88", 7, "33_53", 22), changedBits,
            "Retain the seven entrance edges and the separately recorded northern Prif passages");
        var map = net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.SplitFlagMap.fromResources();
        assertTrue(map.get(3731,5683,0,0)); assertFalse(map.get(3731,5683,0,1));
        assertTrue(map.get(3733,5680,0,0)); assertTrue(map.get(3733,5680,0,1));
    }

    private static Map<String, byte[]> entries(java.io.InputStream source) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (var zip = new java.util.zip.ZipInputStream(source)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private static List<WorldPoint> path(Method plan, Object planner, WorldPoint start, WorldPoint target, List<Object> edges) throws Exception {
        Object result = plan.invoke(planner, start, List.of(target), Set.of(), Set.of(), new HashSet<>(edges));
        Field field = WalkerTestReflection.declaredField(result.getClass(), "path"); field.setAccessible(true);
        return (List<WorldPoint>) field.get(result);
    }
}
