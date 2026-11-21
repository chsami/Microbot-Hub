package net.runelite.client.plugins.microbot.droefficientwalker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Public geometry and settled movement from the complete 20261023-204411-335 recording. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrifddinasRecordedRouteTest
{
    private static final WorldPoint MAP_PIN = p(3233, 6179);
    private static final WorldPoint GROVE = p(2209, 3427);
    private RecordedRoutePlanner graph;

    @BeforeAll void loadPlanner() throws Exception { graph = new RecordedRoutePlanner(); }
    @AfterAll void closePlanner() { if (graph != null) graph.close(); }

    static Stream<Arguments> recordedSegments()
    {
        return Stream.of(
            Arguments.of("Spirit-tree landing to northern maples", p(3274, 6123), GROVE, true),
            Arguments.of("City north exit to observed grove stand", p(3263, 6135), p(2209, 3426), true),
            Arguments.of("Exterior north gate to maples", p(2239, 3386), GROVE, false),
            Arguments.of("Maples to recorded dead-tree approach", p(2209, 3426), p(2134, 3408), false),
            Arguments.of("Northern forest westward", p(2134, 3408), p(2130, 3413), false),
            Arguments.of("Northern forest return", p(2130, 3413), p(2171, 3431), false),
            Arguments.of("Observed tablet landing to cave approach", p(3239, 6076), p(3226, 6046), false),
            Arguments.of("Existing tablet pin to cave approach", p(3239, 6077), p(3226, 6046), false),
            Arguments.of("Recorded cave entry", p(3226, 6046), p(3225, 12445), false),
            Arguments.of("Recorded cave exit and southern street", p(3225, 12445), p(3246, 6069), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedSegments")
    void sourcePlannerConnectsRecordedSegments(String label, WorldPoint from, WorldPoint to, boolean cityAccess)
        throws Exception
    {
        List<Object> routes = cityAccess ? graph.eligibleEdges(true) : graph.edges;
        List<WorldPoint> path = graph.path(from, to, routes);
        assertFalse(path.isEmpty(), label + " must connect " + from + " -> " + to);
        assertEquals(from, path.get(0));
        assertEquals(to, path.get(path.size() - 1));
        if (cityAccess)
            assertTrue(usesNorthGate(path), "Keep the real north-gate transport between coordinate spaces");
        if (from.equals(p(3226, 6046))) assertTrue(path.contains(p(3225, 12445)));
        if (from.equals(p(3225, 12445))) assertTrue(path.contains(p(3225, 6046)));
    }

    @Test void foldedNorthernMaplePinResolvesToTheRecordedExteriorGroveOnly()
    {
        EfficientWalker walker = mock(EfficientWalker.class, CALLS_REAL_METHODS);
        assertEquals(GROVE, walker.resolveDestination(MAP_PIN));
        assertEquals(GROVE, DestinationCorrections.resolve(GROVE));
        assertEquals(MAP_PIN, WorldPoint.getMirrorPoint(MAP_PIN, true),
            "This northern display pin is beyond the API's four city mirror regions");
        for (WorldPoint other : List.of(p(3235, 6180), p(3232, 6177), p(3274, 6123),
            new WorldPoint(3233, 6179, 1))) assertEquals(other, walker.resolveDestination(other));
    }

    @Test void correctionStillRequiresCityAccessForACityStart() throws Exception
    {
        WorldPoint target = DestinationCorrections.resolve(MAP_PIN);
        assertTrue(graph.path(p(3274, 6123), target, graph.edges).isEmpty(),
            "Correcting the destination must not bypass the city's quest-gated exits");
        assertFalse(graph.path(p(3274, 6123), target, graph.eligibleEdges(true)).isEmpty());
    }

    @Test void northernCollisionChangesExactlyTheIndependentlyCapturedClearEdges() throws Exception
    {
        byte[] before = null;
        try (ZipInputStream zip = new ZipInputStream(getClass().getResourceAsStream(
            "/net/runelite/client/plugins/microbot/shortestpath/collision-map.zip")))
        {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                if ("33_53".equals(entry.getName())) before = zip.readAllBytes();
        }
        assertNotNull(before);
        byte[] after = before.clone();
        PrifddinasCollisionMap.patch("33_53", after);
        byte[] expected = before.clone();
        List<String> lines = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("prif-northern-passages.tsv"), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = input.readLine()) != null)
                if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
        }
        assertEquals(22, lines.size());
        for (String line : lines)
        {
            String[] cells = line.split("\\t");
            int x = Integer.parseInt(cells[0]), y = Integer.parseInt(cells[1]);
            int plane = Integer.parseInt(cells[2]), direction = Integer.parseInt(cells[3]);
            int source = Integer.parseInt(cells[4]), target = Integer.parseInt(cells[5]);
            int fromWall = direction == 0 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH : CollisionDataFlag.BLOCK_MOVEMENT_EAST;
            int toWall = direction == 0 ? CollisionDataFlag.BLOCK_MOVEMENT_SOUTH : CollisionDataFlag.BLOCK_MOVEMENT_WEST;
            assertEquals(0, (source | target) & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | 0x1000000));
            assertEquals(0, source & fromWall); assertEquals(0, target & toWall);
            int bit = (plane * 4096 + (y % 64) * 64 + x % 64) * 2 + direction;
            assertEquals(0, before[bit / 8] & (1 << (bit % 8)), "This was missing in the original map");
            expected[bit / 8] |= 1 << (bit % 8);
        }
        assertArrayEquals(expected, after, "No unobserved edge, plane or floor may change");
        PrifddinasCollisionMap.patch("33_53", after);
        assertArrayEquals(expected, after, "Applying captured passages twice is idempotent");
        byte[] untouched = before.clone();
        PrifddinasCollisionMap.patch("50_96", untouched);
        assertArrayEquals(before, untouched, "Do not project the northern forest into city-map coordinates");
    }

    private static boolean usesNorthGate(List<WorldPoint> path)
    {
        for (int i = 1; i < path.size(); i++)
        {
            WorldPoint a = path.get(i - 1), b = path.get(i);
            if (a.getY() == 6135 && b.getY() == 3386 && a.getX() >= 3262 && a.getX() <= 3265
                && b.getX() >= 2238 && b.getX() <= 2241) return true;
        }
        return false;
    }

    private static WorldPoint p(int x, int y) { return new WorldPoint(x, y, 0); }
}
