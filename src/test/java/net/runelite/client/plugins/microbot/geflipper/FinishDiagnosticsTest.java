package net.runelite.client.plugins.microbot.geflipper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Finish diagnostics only name what a Finish is waiting for and say which build is running. None of it may
 * change when a Finish completes, and the lines must reach client.log whatever this package's log level is.
 */
class FinishDiagnosticsTest {
    private static final String OVERVIEW = "the Grand Exchange overview to be open";
    private static final String BUYS = "buy offers to be cancelled";
    private static final String COLLECT = "finished offers to be collected";
    private static final String LIST = "inventory items to be listed for sale";
    private static final String SELLS = "sell offers to finish";
    private static final String WAIT = "Copilot to show Wait";
    private static final long T0 = 1_000_000L;

    @TempDir Path temporary;

    @Test
    void nothingIsUnmetWhenEveryConditionHolds() {
        assertEquals("", FinishSession.describeUnmet(snapshot(true, true, true, true, true, true), false));
        assertEquals("", FinishSession.describeUnmet(snapshot(true, true, true, true, true, true), true));
    }

    @Test
    void eachUnmetConditionIsNamedOnItsOwn() {
        assertEquals(OVERVIEW, FinishSession.describeUnmet(snapshot(false, true, true, true, true, true), true));
        assertEquals(BUYS, FinishSession.describeUnmet(snapshot(true, false, true, true, true, true), true));
        assertEquals(COLLECT, FinishSession.describeUnmet(snapshot(true, true, false, true, true, true), true));
        assertEquals(LIST, FinishSession.describeUnmet(snapshot(true, true, true, false, true, true), true));
        assertEquals(SELLS, FinishSession.describeUnmet(snapshot(true, true, true, true, false, true), true));
        assertEquals(WAIT, FinishSession.describeUnmet(snapshot(true, true, true, true, true, false), true));
    }

    @Test
    void activeSellOffersOnlyMatterWhenFinishWaitsForThem() {
        FinishSession.CompletionSnapshot listedOnly = snapshot(true, true, true, true, false, true);
        assertEquals("", FinishSession.describeUnmet(listedOnly, false));
        assertEquals(SELLS, FinishSession.describeUnmet(listedOnly, true));
    }

    @Test
    void everythingUnmetIsNamedInAFixedOrder() {
        FinishSession.CompletionSnapshot nothingDone = snapshot(false, false, false, false, false, false);
        assertEquals(String.join(", ", OVERVIEW, BUYS, COLLECT, LIST, SELLS, WAIT),
            FinishSession.describeUnmet(nothingDone, true));
        assertEquals(String.join(", ", OVERVIEW, BUYS, COLLECT, LIST, WAIT),
            FinishSession.describeUnmet(nothingDone, false));
    }

    @Test
    void anUnreadableExchangeIsNamedNotThrown() {
        assertEquals("a reading of the Grand Exchange", FinishSession.describeUnmet(null, false));
        assertEquals("a reading of the Grand Exchange", FinishSession.describeUnmet(null, true));
    }

    @Test
    void descriptionIsEmptyExactlyWhenCompletionIsReachableForEverySnapshot() {
        // 64 snapshots x both modes against the real completion rule, so the log can never name a condition
        // Finish does not wait for, nor stay silent about one it does.
        for (int mask = 0; mask < 64; mask++) {
            for (boolean all : new boolean[] {false, true}) {
                FinishSession.CompletionSnapshot snapshot = snapshot((mask & 1) != 0, (mask & 2) != 0,
                    (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0, (mask & 32) != 0);
                FinishSession session = start(new FinishSessionTest.Fixture(), all);
                assertEquals(all, session.waitsForAllSales());
                session.completionReady(snapshot, 0);
                boolean completes = session.completionReady(snapshot, FinishSession.COMPLETE_STABLE_MS);
                assertEquals(completes,
                    FinishSession.describeUnmet(snapshot, session.waitsForAllSales()).isEmpty(),
                    "mask=" + mask + " waitForAllSales=" + all);
            }
        }
    }

    @Test
    void waitsForAllSalesFollowsTheRequestAndTheSystemUpdateOverride() {
        FinishSession session = new FinishSession();
        assertFalse(session.waitsForAllSales());
        session.request(true);
        assertTrue(session.waitsForAllSales());
        session.stopWaitingForSales();
        assertFalse(session.waitsForAllSales());
        FinishSession listing = new FinishSession();
        listing.request(false);
        assertFalse(listing.waitsForAllSales());
    }

    @Test
    void aStuckFinishIsLoggedAfterThirtySecondsThenEveryThirtySeconds() {
        try (Capture capture = new Capture()) {
            FlipperScript script = new FlipperScript();
            FinishSession.CompletionSnapshot stuck = snapshot(true, true, true, false, true, false);
            script.logFinishWait(stuck, T0);
            script.logFinishWait(stuck, T0 + 29_999);
            assertEquals(List.of(), capture.warnings());
            script.logFinishWait(stuck, T0 + 30_000);
            assertEquals(1, capture.warnings().size());
            assertTrue(capture.warnings().get(0).startsWith(
                "Finish: still waiting for " + LIST + ", " + WAIT + " (30 s so far)."), capture.warnings().get(0));
            script.logFinishWait(stuck, T0 + 59_999);
            assertEquals(1, capture.warnings().size());
            script.logFinishWait(stuck, T0 + 60_000);
            assertEquals(2, capture.warnings().size());
            assertTrue(capture.warnings().get(1).contains("(60 s so far)."), capture.warnings().get(1));
        }
    }

    @Test
    void theLineCarriesTheFinishStatusSoCopilotProblemsAreVisible() throws Exception {
        try (Capture capture = new Capture()) {
            FlipperScript script = new FlipperScript();
            FinishSessionTest.Fixture fixture = new FinishSessionTest.Fixture();
            FinishSession session = finishSession(script);
            session.request(false);
            assertTrue(session.begin(null, fixture.controller, fixture.manager));
            fixture.controller.paused.paused = true;
            assertFalse(session.readyForCancellation());
            FinishSession.CompletionSnapshot stuck = snapshot(true, true, true, true, true, false);
            script.logFinishWait(stuck, T0);
            script.logFinishWait(stuck, T0 + 30_000);
            assertEquals(1, capture.warnings().size());
            assertTrue(capture.warnings().get(0).contains(session.status()), capture.warnings().get(0));
            assertTrue(session.status().contains("resume Copilot"), session.status());
        }
    }

    @Test
    void aFinishThatMovesOnRestartsTheClock() {
        try (Capture capture = new Capture()) {
            FlipperScript script = new FlipperScript();
            FinishSession.CompletionSnapshot collecting = snapshot(true, true, false, true, true, true);
            FinishSession.CompletionSnapshot listing = snapshot(true, true, true, false, true, true);
            script.logFinishWait(collecting, T0);
            script.logFinishWait(collecting, T0 + 29_000);
            script.logFinishWait(listing, T0 + 30_000);
            assertEquals(List.of(), capture.warnings(), "A change of what is awaited starts a new wait");
            script.logFinishWait(listing, T0 + 59_999);
            assertEquals(List.of(), capture.warnings());
            script.logFinishWait(listing, T0 + 60_000);
            assertEquals(1, capture.warnings().size());
            assertTrue(capture.warnings().get(0).contains(LIST), capture.warnings().get(0));
            assertFalse(capture.warnings().get(0).contains(COLLECT), capture.warnings().get(0));
            assertTrue(capture.warnings().get(0).contains("(30 s so far)."), capture.warnings().get(0));
        }
    }

    @Test
    void aFinishWithNothingLeftToWaitForLogsNothingAndForgetsTheOldClock() {
        try (Capture capture = new Capture()) {
            FlipperScript script = new FlipperScript();
            FinishSession.CompletionSnapshot stuck = snapshot(true, true, true, false, true, true);
            FinishSession.CompletionSnapshot settled = snapshot(true, true, true, true, true, true);
            script.logFinishWait(stuck, T0);
            script.logFinishWait(settled, T0 + 20_000);
            script.logFinishWait(settled, T0 + 120_000);
            assertEquals(List.of(), capture.warnings(), "Only the settle time is left, which is not worth a line");
            script.logFinishWait(stuck, T0 + 125_000);
            script.logFinishWait(stuck, T0 + 154_999);
            assertEquals(List.of(), capture.warnings(), "The earlier wait must not carry over");
            script.logFinishWait(stuck, T0 + 155_000);
            assertEquals(1, capture.warnings().size());
        }
    }

    @Test
    void sellOffersAreNamedOnlyWhenFinishWaitsForThem() throws Exception {
        try (Capture capture = new Capture()) {
            FinishSession.CompletionSnapshot listedOnly = snapshot(true, true, true, true, false, true);
            FlipperScript listing = new FlipperScript();
            finishSession(listing).request(false);
            listing.logFinishWait(listedOnly, T0);
            listing.logFinishWait(listedOnly, T0 + 30_000);
            assertEquals(List.of(), capture.warnings(), "Listing is enough, so open sell offers are not awaited");
            FlipperScript selling = new FlipperScript();
            finishSession(selling).request(true);
            selling.logFinishWait(listedOnly, T0);
            selling.logFinishWait(listedOnly, T0 + 30_000);
            assertEquals(1, capture.warnings().size());
            assertTrue(capture.warnings().get(0).contains("waiting for " + SELLS + " ("), capture.warnings().get(0));
        }
    }

    @Test
    void anUnreadableExchangeIsLoggedWithoutThrowing() {
        try (Capture capture = new Capture()) {
            FlipperScript script = new FlipperScript();
            script.logFinishWait(null, T0);
            script.logFinishWait(null, T0 + 30_000);
            assertEquals(1, capture.warnings().size());
            assertTrue(capture.warnings().get(0).contains("a reading of the Grand Exchange"),
                capture.warnings().get(0));
        }
    }

    @Test
    void aNewFinishRequestForgetsAnyEarlierWait() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            f.script.logFinishWait(snapshot(true, true, true, false, true, true), T0);
            assertEquals(LIST, field(FlipperScript.class, "finishWaitUnmet").get(f.script));
            assertTrue(f.script.requestFinish(() -> { }));
            assertEquals("", field(FlipperScript.class, "finishWaitUnmet").get(f.script));
        }
    }

    @Test
    void shuttingDownForgetsAnyEarlierWait() throws Exception {
        try (FlipperFinishLifecycleTest.Fixture f = new FlipperFinishLifecycleTest.Fixture(temporary)) {
            f.script.logFinishWait(snapshot(true, true, true, false, true, true), T0);
            assertEquals(LIST, field(FlipperScript.class, "finishWaitUnmet").get(f.script));
            f.script.shutdown();
            assertEquals("", field(FlipperScript.class, "finishWaitUnmet").get(f.script));
        }
    }

    @Test
    void aFailureInsideTheDiagnosticNeverReachesTheFinish() throws Exception {
        FlipperScript script = new FlipperScript();
        // Taking the session away makes the diagnostic's own read fail, as no real run can.
        field(FlipperScript.class, "finishSession").set(script, null);
        assertDoesNotThrow(() -> script.logFinishWait(snapshot(true, true, true, false, true, true), T0));
    }

    @Test
    void startupNamesTheBuildEvenWhileThePackageLoggerIsHeldQuiet() throws Exception {
        Logger own = (Logger) LoggerFactory.getLogger("net.runelite.client.plugins.microbot.geflipper");
        Level previousOwnLevel = own.getLevel();
        try (Capture capture = new Capture()) {
            FlipperPlugin plugin = new FlipperPlugin();
            setPluginField(plugin, "config", new FlipperConfig() { });
            setPluginField(plugin, "flipperScript", new StubScript());
            plugin.startUp();
            try {
                assertEquals(Level.WARN, own.getLevel(), "Premise: this package's own logger is held at WARN");
                assertEquals(List.of("GE Flipper " + FlipperPlugin.version + " started."),
                    capture.messages(Level.INFO));
            } finally {
                plugin.shutDown();
            }
        } finally {
            own.setLevel(previousOwnLevel);
        }
    }

    private static FinishSession start(FinishSessionTest.Fixture fixture, boolean waitForAllSales) {
        FinishSession session = new FinishSession();
        session.request(waitForAllSales);
        assertTrue(session.begin(null, fixture.controller, fixture.manager));
        fixture.manager.completeRequest();
        return session;
    }

    private static FinishSession.CompletionSnapshot snapshot(boolean overview, boolean buys, boolean collected,
            boolean listed, boolean sales, boolean wait) {
        return new FinishSession.CompletionSnapshot(overview, buys, collected, listed, sales, wait);
    }

    private static FinishSession finishSession(FlipperScript script) throws Exception {
        Field field = FlipperScript.class.getDeclaredField("finishSession");
        field.setAccessible(true);
        return (FinishSession) field.get(script);
    }

    private static void setPluginField(FlipperPlugin plugin, String name, Object value) throws Exception {
        Field field = FlipperPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private static final class StubScript extends FlipperScript {
        @Override
        public boolean run(FlipperConfig config) {
            return true;
        }

        @Override
        public void shutdown() {
        }
    }

    /** Listens on the diagnostics logger only, and puts its level and additivity back afterwards. */
    private static final class Capture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(FlipperPlugin.DIAGNOSTICS_LOGGER);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final Level previousLevel = logger.getLevel();
        private final boolean previousAdditive = logger.isAdditive();

        Capture() {
            appender.setContext(logger.getLoggerContext());
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
        }

        List<String> warnings() {
            return messages(Level.WARN);
        }

        List<String> messages(Level level) {
            List<String> values = new ArrayList<>();
            for (ILoggingEvent event : new ArrayList<>(appender.list)) {
                if (event.getLevel() == level) values.add(event.getFormattedMessage());
            }
            return values;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }
    }
}
