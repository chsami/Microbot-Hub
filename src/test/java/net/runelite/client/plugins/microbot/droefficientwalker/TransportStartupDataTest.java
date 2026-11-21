package net.runelite.client.plugins.microbot.droefficientwalker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

class TransportStartupDataTest {
    @Test void runtimeParserLoadsAllFourGateEdgesWithoutWarnings() throws Exception {
        Class<?> loaderType = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader");
        var constructor = loaderType.getDeclaredConstructor(ClassLoader.class);
        constructor.setAccessible(true);
        ClassLoader loader = (ClassLoader) constructor.newInstance(getClass().getClassLoader());
        Class<?> parser = Class.forName("net.runelite.client.plugins.microbot.droefficientwalker.engine.TransportCatalog", true, loader);
        assertSame(loader, parser.getClassLoader(), "Exercise the selected source parser through its isolated engine loader");
        var open = WalkerTestReflection.declaredMethod(parser, "openResource", String.class);
        var parse = WalkerTestReflection.declaredMethod(parser, "parseTransports", java.io.Reader.class);
        open.setAccessible(true);
        parse.setAccessible(true);
        for (String prefix : List.of("droefficientwalker",
            "droefficientwalker/engine")) {
            try (var logs = new Logs(parser);
                 var reader = (java.io.Reader) open.invoke(null, "/net/runelite/client/plugins/microbot/" + prefix + "/transports.tsv")) {
                List<?> edges = (List<?>) parse.invoke(null, reader);
                int gates = 0;
                for (Object edge : edges) {
                    if (!"Colony gate".equals(field(edge, "target"))) continue;
                    gates++;
                    WorldPoint origin = (WorldPoint) field(edge, "origin");
                    WorldPoint destination = (WorldPoint) field(edge, "destination");
                    assertEquals(origin.getX() == 2343 ? 12723 : 12725, field(edge, "objectId"));
                    assertEquals("Open", field(edge, "action"));
                    assertEquals(origin.getX(), destination.getX());
                    assertEquals(1, Math.abs(origin.getY() - destination.getY()));
                    var allowed = WalkerTestReflection.declaredMethod(edge.getClass(), "meetsRequirements", java.util.function.ToIntFunction.class,
                        java.util.function.BiPredicate.class, java.util.function.IntUnaryOperator.class,
                        java.util.function.IntUnaryOperator.class, java.util.function.Predicate.class);
                    allowed.setAccessible(true);
                    java.util.function.ToIntFunction<net.runelite.api.Skill> skills = skill -> 99;
                    java.util.function.BiPredicate<String, Integer> items = (item, amount) -> true;
                    java.util.function.IntUnaryOperator variables = id -> 0;
                    assertEquals(false, allowed.invoke(edge, skills, items, variables, variables,
                        (java.util.function.Predicate<Quest>) quest -> false));
                    assertEquals(true, allowed.invoke(edge, skills, items, variables, variables,
                        (java.util.function.Predicate<Quest>) quest -> quest == Quest.SWAN_SONG));
                }
                assertEquals(4, gates, prefix);
                assertTrue(logs.appender.list.isEmpty(), "Embedded runtime startup warnings: " + prefix);
            }
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = WalkerTestReflection.declaredField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test void bundledCatalogueLoadsWithoutWarningsAndRetainsAllFourQuestGatedColonyEdges() throws Exception {
        try (var logs = new Logs();
             var data = TransportStartupDataTest.class.getResourceAsStream("/net/runelite/client/plugins/microbot/droefficientwalker/engine-data/transports.tsv")) {
            assertNotNull(data);
            List<TransportCatalog.TransportEdge> edges = TransportCatalog.parseTransports(new InputStreamReader(data, StandardCharsets.UTF_8));
            var gates = edges.stream().filter(edge -> edge.target.equals("Colony gate")).collect(java.util.stream.Collectors.toList());
            assertEquals(4, gates.size());
            for (TransportCatalog.TransportEdge gate : gates) {
                assertEquals(gate.origin.getX() == 2343 ? 12723 : 12725, gate.objectId);
                assertEquals("Open", gate.action);
                assertEquals(gate.origin.getX(), gate.destination.getX());
                assertEquals(1, Math.abs(gate.origin.getY() - gate.destination.getY()));
                assertFalse(gate.meetsRequirements(skill -> 99, (item, amount) -> true, id -> 0, id -> 0, quest -> false));
                assertTrue(gate.meetsRequirements(skill -> 99, (item, amount) -> true, id -> 0, id -> 0, quest -> quest == Quest.SWAN_SONG));
            }
            assertTrue(logs.appender.list.isEmpty(), "The actual bundled catalogue should not produce startup warnings");
        }
    }

    @Test void explicitNumericObjectIdsAndEndpointsRemainUnchanged() throws Exception {
        var edges = TransportCatalog.parseTransports(new StringReader("3000 3000 0\t3000 3001 0\tOpen Large gate 12345\t\t\t\t\t\t1\t"));
        assertEquals(1, edges.size());
        assertEquals(12345, edges.get(0).objectId);
        assertEquals("Large gate", edges.get(0).target);
        assertEquals(new WorldPoint(3000, 3000, 0), edges.get(0).origin);
        assertEquals(new WorldPoint(3000, 3001, 0), edges.get(0).destination);
    }

    @Test void missingIdsOutsideTheFourKnownGateEdgesStillReportInvalidData() throws Exception {
        try (var logs = new Logs()) {
            var edges = TransportCatalog.parseTransports(new StringReader("2345 3662 0\t2345 3663 0\tOpen Colony gate\t\t\tSwan Song\t\t\t2\t"));
            assertTrue(edges.isEmpty());
            assertEquals(1, logs.appender.list.size());
            assertNotNull(logs.appender.list.get(0).getThrowableProxy());
        }
    }

    private static final class Logs implements AutoCloseable {
        final Logger logger;
        final Level previous;
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logs() { this(TransportCatalog.class); }
        Logs(Class<?> type) { logger = (Logger) LoggerFactory.getLogger(type); previous = logger.getLevel(); logger.setLevel(Level.WARN); appender.start(); logger.addAppender(appender); }
        public void close() { logger.detachAppender(appender); logger.setLevel(previous); appender.stop(); }
    }
}
