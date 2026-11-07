package net.runelite.client.plugins.microbot.geflipper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the real Back and Finish caller paths without sending any client input. */
class FlipperBackSafetyTest {
    private static final int BACK = 30474244;

    @Test
    void bothSpeedModesHoverValidateAndPressBackOnceBeforeResettingTheWatchdog() throws Exception {
        for (boolean varying : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(varying)) {
                assertTrue(fixture.script.backToOverview());
                assertEquals(Arrays.asList("press", "release", "click"), fixture.backend.events);
                assertFalse(fixture.setup.visible);
                assertEquals(0, field(FlipperScript.class, "offerScreenOpenTime").getLong(fixture.script));
                assertEquals(0, field(FlipperScript.class, "offerScreenActionCount").getInt(fixture.script));
                assertTrue(fixture.uiReads > 0);
            }
        }
    }

    @Test
    void aClosedExchangeOrHiddenBackCannotStartAMovementOrResetTheWatchdog() throws Exception {
        for (boolean varying : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(varying)) {
                fixture.contents.visible = false;
                assertFalse(fixture.script.backToOverview());
                fixture.contents.visible = true;
                fixture.back.visible = false;
                assertFalse(fixture.script.backToOverview());
                assertEquals(0, fixture.backend.moves);
                fixture.assertNoPressOrReset();
            }
        }
    }

    @Test
    void theExchangeDisappearingDuringMovementCannotTurnBackIntoAWalkingClick() throws Exception {
        for (boolean varying : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(varying)) {
                fixture.backend.afterMove = () -> {
                    fixture.contents.visible = fixture.setup.visible = fixture.back.visible = false;
                    fixture.entry.option = "Walk here";
                    fixture.entry.type = MenuAction.WALK;
                };
                assertFalse(fixture.script.backToOverview());
                assertTrue(fixture.backend.moves > 0);
                fixture.assertNoPressOrReset();
            }
        }
    }

    @Test
    void graphsAndUnrelatedDefaultsAreNeverAcceptedAsBack() throws Exception {
        for (String option : new String[]{"Copilot graph", "Walk here", "View offer", "Confirm", "Nothing"}) {
            try (Fixture fixture = new Fixture(false)) {
                fixture.entry.option = option;
                assertFalse(fixture.script.backToOverview());
                fixture.assertNoPressOrReset();
            }
        }
    }

    @Test
    void aMenuChangeAfterHoverValidationCancelsBothSpeedModesBeforePress() throws Exception {
        for (boolean varying : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(varying)) {
                fixture.script.afterSuccessfulWait = () -> fixture.entry.option = "Walk here";
                assertFalse(fixture.script.backToOverview());
                fixture.assertNoPressOrReset();
            }
        }
    }

    @Test
    void aLateWorldMenuCannotReplaceThePublishedNativeBackAction() throws Exception {
        for (boolean varying : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(varying)) {
                fixture.backend.beforePress = () -> {
                    fixture.contents.visible = fixture.setup.visible = fixture.back.visible = false;
                    fixture.entry.option = "Walk here";
                    fixture.entry.type = MenuAction.WALK;
                };
                assertTrue(fixture.script.backToOverview());
                assertEquals(Arrays.asList("Back"), fixture.backend.dispatched);
                assertEquals("Back", fixture.backend.pending.getOption());
                assertEquals(MenuAction.CC_OP, fixture.backend.pending.getType());
                assertEquals(BACK, fixture.backend.pending.getParam1());
            }
        }
    }

    @Test
    void aCancelledStationaryInvocationClearsOnlyItsOwnedPendingBackEntry() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.backend.afterPublish = () -> fixture.backend.position = new Point(1, 1);
            assertFalse(fixture.script.backToOverview());
            assertNull(fixture.backend.pending);
            fixture.assertNoPressOrReset();
        }
    }

    @Test
    void aBackLabelCannotSubstituteAnotherWidgetOperation() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.back.actions = new String[]{"Close"};
            assertFalse(fixture.script.backToOverview());
            fixture.assertNoPressOrReset();
        }
    }

    @Test
    void replacedOrMovedBackWidgetsCannotReuseAnEarlierHoverValidation() throws Exception {
        for (boolean replaced : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(true)) {
                fixture.script.afterSuccessfulWait = () -> {
                    if (replaced) fixture.widgets.put(BACK, fixture.new Node(BACK));
                    else fixture.back.bounds.translate(1, 0);
                };
                assertFalse(fixture.script.backToOverview());
                fixture.assertNoPressOrReset();
            }
        }
    }

    @Test
    void aBackPressThatDoesNotRecoverTheOfferScreenRetainsWatchdogStateForRetry() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.backend.afterClick = () -> { };
            assertFalse(fixture.script.backToOverview());
            assertEquals(Arrays.asList("press", "release", "click"), fixture.backend.events);
            assertEquals(123, field(FlipperScript.class, "offerScreenOpenTime").getLong(fixture.script));
            assertEquals(3, field(FlipperScript.class, "offerScreenActionCount").getInt(fixture.script));
        }
    }

    @Test
    void finishCanDismissAnOfferAndItsInputPromptThroughVerifiedBackWithoutEscape() throws Exception {
        for (boolean varying : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(varying)) {
                fixture.startFinishWithPendingSuggestion();
                fixture.promptOpen = true;
                assertTrue(fixture.processFinish());
                assertEquals(Arrays.asList("press", "release", "click"), fixture.backend.events);
                assertFalse(fixture.promptOpen);
                assertFalse(fixture.setup.visible);
                assertTrue(fixture.script.isFinishing(), "Finish must retain its request for the next overview tick");
                assertEquals(0, fixture.keyboardCanvasReads, "Finish recovery must not issue an unconditional ESC");
            }
        }
    }

    @Test
    void finishWithAnUnavailableBackRetainsItsRequestWithoutClosingTheExchangeOrClickingTheWorld() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.startFinishWithPendingSuggestion();
            fixture.entry.option = "Walk here";
            assertTrue(fixture.processFinish());
            assertTrue(fixture.contents.visible);
            assertTrue(fixture.setup.visible);
            assertTrue(fixture.script.isFinishing());
            assertEquals(0, fixture.keyboardCanvasReads);
            fixture.assertNoPressOrReset();
        }
    }

    @Test
    void aFinishStuckForThirtySecondsNamesWhatItWaitsForAndStillDoesExactlyWhatItDidBefore() throws Exception {
        try (DiagnosticLines lines = new DiagnosticLines(); Fixture fixture = new Fixture(false)) {
            fixture.startFinishWithPendingSuggestion();
            fixture.entry.option = "Walk here";
            assertTrue(fixture.processFinish());
            assertTrue(lines.events.isEmpty(), "The first sight of a wait only starts the clock");
            ageTheCurrentFinishWait(fixture.script);
            assertTrue(fixture.processFinish());
            assertEquals(1, lines.events.size());
            assertEquals(Level.WARN, lines.events.get(0).getLevel());
            String line = lines.events.get(0).getFormattedMessage();
            assertTrue(line.startsWith("Finish: still waiting for "), line);
            assertTrue(line.contains("the Grand Exchange overview to be open"),
                "An offer screen is open, so the overview is not: " + line);
            assertTrue(line.matches("(?s).*\\(3[1-9] s so far\\)\\..*"), line);
            // Logging changed nothing: same outcome as the earlier test of this situation.
            assertTrue(fixture.contents.visible);
            assertTrue(fixture.setup.visible);
            assertTrue(fixture.script.isFinishing());
            assertEquals(0, fixture.keyboardCanvasReads);
            fixture.assertNoPressOrReset();
        }
    }

    @Test
    void aMuleSellDownThatWaitsForFillsByDesignDoesNotFillTheLogWithWaits() throws Exception {
        try (DiagnosticLines lines = new DiagnosticLines(); Fixture fixture = new Fixture(false)) {
            fixture.startFinishWithPendingSuggestion();
            fixture.entry.option = "Walk here";
            field(FlipperScript.class, "muleFinish").setBoolean(fixture.script, true);
            assertTrue(fixture.processFinish());
            ageTheCurrentFinishWait(fixture.script);
            assertTrue(fixture.processFinish());
            assertTrue(lines.events.isEmpty(), "A mule sell-down waiting for fills is not a stuck Finish");
        }
    }

    /** The tick reads the wall clock, so make the wait it is already tracking look 31 s old. */
    private static void ageTheCurrentFinishWait(FlipperScript script) throws Exception {
        long earlier = System.currentTimeMillis() - 31_000;
        field(FlipperScript.class, "finishWaitSince").setLong(script, earlier);
        field(FlipperScript.class, "finishWaitLoggedAt").setLong(script, earlier);
    }

    /** Listens on the diagnostics logger only, and puts its level and additivity back afterwards. */
    private static final class DiagnosticLines implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(FlipperPlugin.DIAGNOSTICS_LOGGER);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final List<ILoggingEvent> events = appender.list;
        private final Level previousLevel = logger.getLevel();
        private final boolean previousAdditive = logger.isAdditive();
        private DiagnosticLines() {
            appender.setContext(logger.getLoggerContext());
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
        }
        @Override public void close() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }
    }

    private static final class TestScript extends FlipperScript {
        private Runnable afterSuccessfulWait = () -> { };
        @Override public boolean isRunning() { return true; }
        @Override boolean waitForUi(BooleanSupplier condition, int timeout) {
            boolean ready = condition.getAsBoolean();
            if (ready) afterSuccessfulWait.run();
            return ready;
        }
    }

    private static final class Entry {
        private MenuAction type = MenuAction.CC_OP;
        private String option = "Back";
        private final MenuEntry value = (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(),
            new Class<?>[]{MenuEntry.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getType": return type;
                    case "getOption": return option;
                    case "getTarget": return "";
                    case "getIdentifier": return 1;
                    case "getParam0": case "getItemId": return -1;
                    case "getParam1": return BACK;
                    default: throw new AssertionError("Unexpected Back-menu read: " + method.getName());
                }
            });
    }

    private static final class Fixture implements AutoCloseable {
        private final Field clientField = field(Microbot.class, "client");
        private final Field threadField = field(Microbot.class, "clientThread");
        private final Field mouseField = field(Microbot.class, "mouse");
        private final Object previousClient = clientField.get(null), previousThread = threadField.get(null),
            previousMouse = mouseField.get(null);
        private final MenuEntry previousPending = Microbot.targetMenu;
        private final boolean previousPause = Microbot.pauseAllScripts.get();
        private final TestScript script = new TestScript();
        private final Map<Integer, Node> widgets = new HashMap<>();
        private final Node contents = node(InterfaceID.GeOffers.CONTENTS), setup = node(InterfaceID.GeOffers.SETUP),
            back = node(BACK);
        private final Entry entry = new Entry();
        private final Backend backend = new Backend();
        private final FinishSessionTest.Fixture copilot = new FinishSessionTest.Fixture();
        private final GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
        private boolean onClientThread, promptOpen;
        private int uiReads, keyboardCanvasReads;
        private final ClientThread inline = new ClientThread() {
            @Override public <T> Optional<T> runOnClientThreadOptional(Callable<T> call) {
                assertFalse(onClientThread, "Back recovery must not nest client-thread dispatch");
                onClientThread = true;
                uiReads++;
                try { return Optional.ofNullable(call.call()); }
                catch (Exception failure) { throw new AssertionError(failure); }
                finally { onClientThread = false; }
            }
        };

        private Fixture(boolean varying) throws Exception {
            Menu menu = (Menu) Proxy.newProxyInstance(Menu.class.getClassLoader(), new Class<?>[]{Menu.class},
                (proxy, method, args) -> {
                    assertTrue(onClientThread);
                    if (method.getName().equals("getMenuEntries")) return new MenuEntry[]{entry.value};
                    throw new AssertionError("Unexpected Back-menu access: " + method.getName());
                });
            ItemContainer inventory = (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
                new Class<?>[]{ItemContainer.class}, (proxy, method, args) -> {
                    assertTrue(onClientThread);
                    if (method.getName().equals("getItems")) return new Item[]{new Item(995, 1000)};
                    throw new AssertionError("Unexpected inventory access: " + method.getName());
                });
            Arrays.setAll(offers, index -> (GrandExchangeOffer) Proxy.newProxyInstance(
                GrandExchangeOffer.class.getClassLoader(), new Class<?>[]{GrandExchangeOffer.class},
                (proxy, method, args) -> {
                    assertTrue(onClientThread);
                    if (method.getName().equals("getState")) return GrandExchangeOfferState.EMPTY;
                    throw new AssertionError("Unexpected offer access: " + method.getName());
                }));
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getGameState")) return GameState.LOGGED_IN;
                    if (method.getName().equals("getCanvas")) {
                        keyboardCanvasReads++;
                        throw new AssertionError("Back recovery cannot send keyboard input");
                    }
                    assertTrue(onClientThread, "Back UI read outside client thread: " + method.getName());
                    switch (method.getName()) {
                        case "getWidget": {
                            int id = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                            Node node = widgets.get(id);
                            return node == null ? null : node.widget;
                        }
                        case "getCanvasWidth": case "getCanvasHeight": return 100;
                        case "getMouseCanvasPosition": return backend.position;
                        case "isMenuOpen": return false;
                        case "getMenu": return menu;
                        case "getGrandExchangeOffers": return offers;
                        case "getItemContainer": return inventory;
                        case "getVarcIntValue": return promptOpen ? 1 : 0;
                        default: throw new AssertionError("Unexpected Back-client read: " + method.getName());
                    }
                });
            clientField.set(null, client);
            threadField.set(null, inline);
            mouseField.set(null, new Mouse() {
                @Override public java.awt.Point getMousePosition() { return new java.awt.Point(4, 4); }
                @Override public Point getLastClick() { return new Point(4, 4); }
                @Override public void setLastClick(Point point) { }
                @Override public Mouse click(Point point, NewMenuEntry entry) { throw new AssertionError("Unvalidated Back click"); }
                @Override public Mouse click(int x, int y) { throw new AssertionError("Unvalidated Back click"); }
                @Override public Mouse click(double x, double y) { throw new AssertionError("Unvalidated Back click"); }
                @Override public Mouse click(Rectangle ignored) { throw new AssertionError("Unvalidated Back click"); }
                @Override public Mouse click(int x, int y, boolean right) { throw new AssertionError("Unvalidated Back click"); }
                @Override public Mouse click(Point point) { throw new AssertionError("Unvalidated Back click"); }
                @Override public Mouse click(Point point, boolean right) { throw new AssertionError("Unvalidated Back click"); }
                @Override public Mouse click() { throw new AssertionError("Unvalidated Back click"); }
                @Override public Mouse move(Point point) { throw new AssertionError("Unvalidated Back movement"); }
                @Override public Mouse move(Rectangle bounds) { throw new AssertionError("Unvalidated Back movement"); }
                @Override public Mouse move(int x, int y) { throw new AssertionError("Unvalidated Back movement"); }
                @Override public Mouse move(double x, double y) { throw new AssertionError("Unvalidated Back movement"); }
                @Override public Mouse move(java.awt.Polygon polygon) { throw new AssertionError("Unvalidated Back movement"); }
                @Override public Mouse scrollDown(Point point) { throw new AssertionError(); }
                @Override public Mouse scrollUp(Point point) { throw new AssertionError(); }
                @Override public Mouse drag(Point from, Point to) { throw new AssertionError(); }
            });
            Microbot.targetMenu = null;
            Microbot.pauseAllScripts.set(false);
            InputArbiter.resetForTest();
            field(FlipperScript.class, "config").set(script, new FlipperConfig() {
                @Override public boolean randomizeMouseSpeed() { return varying; }
            });
            field(FlipperScript.class, "mouseMotion").set(script, new GeflipperMouseMotion(backend,
                (minimum, maximum) -> 100, new Random(2),
                (nature, random, x, y) -> nature.getSystemCalls().setMousePosition(x, y)));
            field(FlipperScript.class, "offerScreenOpenTime").setLong(script, 123);
            field(FlipperScript.class, "offerScreenActionCount").setInt(script, 3);
            backend.afterClick = () -> { setup.visible = false; promptOpen = false; };
        }

        private Node node(int id) { Node node = new Node(id); widgets.put(id, node); return node; }
        private void startFinishWithPendingSuggestion() throws Exception {
            FinishSession finish = (FinishSession) field(FlipperScript.class, "finishSession").get(script);
            finish.request(false);
            assertTrue(finish.begin(null, copilot.controller, copilot.manager));
            field(FlipperScript.class, "suggestionManager").set(script, copilot.manager);
        }
        private boolean processFinish() throws Exception {
            Method process = FlipperScript.class.getDeclaredMethod("processFinish", GeUiState.class);
            process.setAccessible(true);
            GeUiState ui = inline.runOnClientThreadOptional(() -> GeUiState.capture(contents.widget, null,
                setup.widget, null, null)).orElseThrow(AssertionError::new);
            return (Boolean) process.invoke(script, ui);
        }
        private void assertNoPressOrReset() throws Exception {
            assertTrue(backend.events.isEmpty(), "A refused Back cannot press the world canvas");
            assertEquals(123, field(FlipperScript.class, "offerScreenOpenTime").getLong(script));
            assertEquals(3, field(FlipperScript.class, "offerScreenActionCount").getInt(script));
        }
        @Override public void close() throws Exception {
            ((FinishSession) field(FlipperScript.class, "finishSession").get(script)).close();
            InputArbiter.resetForTest();
            Microbot.pauseAllScripts.set(previousPause);
            Microbot.targetMenu = previousPending;
            clientField.set(null, previousClient);
            threadField.set(null, previousThread);
            mouseField.set(null, previousMouse);
            ((ScheduledExecutorService) field(ClientThread.class, "scheduledExecutorService").get(inline)).shutdownNow();
        }

        private final class Node {
            private final Rectangle bounds = new Rectangle(20, 30, 35, 25);
            private boolean visible = true;
            private String[] actions = {"Back"};
            private final Widget widget;
            private Node(int id) {
                widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                    (proxy, method, args) -> {
                        assertTrue(onClientThread, "Back widget read outside client thread: " + method.getName());
                        switch (method.getName()) {
                            case "getId": return id;
                            case "getIndex": case "getItemId": return -1;
                            case "isHidden": return !visible;
                            case "getBounds": return bounds;
                            case "getActions": return actions;
                            case "getText": return "";
                            case "getChildren": case "getDynamicChildren": case "getStaticChildren":
                            case "getNestedChildren": return null;
                            default: throw new AssertionError("Unexpected Back-widget read: " + method.getName());
                        }
                    });
            }
        }
    }

    private static final class Backend implements GeflipperMouseMotion.Backend {
        private final MouseMotionNature nature = new MouseMotionNature();
        private final List<String> events = new ArrayList<>();
        private final List<String> dispatched = new ArrayList<>();
        private Point position = new Point(4, 4);
        private MenuEntry pending;
        private int moves;
        private Runnable afterMove = () -> { }, afterClick = () -> { }, beforePress = () -> { },
            afterPublish = () -> { };
        private Backend() {
            nature.setTimeToStepsDivider(8);
            nature.setMinSteps(10);
            nature.setEffectFadeSteps(15);
            nature.setReactionTimeBaseMs(0);
            nature.setReactionTimeVariationMs(0);
            nature.setNoiseProvider(new DefaultNoiseProvider(2));
            nature.setDeviationProvider(new SinusoidalDeviationProvider(10));
            nature.setSpeedManager(distance -> new Pair<>(new Flow(new double[]{1, 1}), 250L));
            nature.setOvershootManager(new DefaultOvershootManager(new Random(2)));
        }
        public boolean worker() { return true; }
        public boolean human() { return false; }
        public GeflipperMouseMotion.Snapshot snapshot() { return new GeflipperMouseMotion.Snapshot(100, 100, nature); }
        public Point position() { return position; }
        public boolean gesture(Consumer<GeflipperMouseMotion.Sink> action) {
            action.accept(new GeflipperMouseMotion.Sink() {
                public void checkpoint() { }
                public void move(int x, int y) { position = new Point(x, y); moves++; afterMove.run(); }
                public void press(int x, int y) {
                    beforePress.run();
                    events.add("press");
                    dispatched.add(pending == null ? "Unowned press" : pending.getOption());
                }
                public void release(int x, int y) { events.add("release"); }
                public void click(int x, int y) { events.add("click"); afterClick.run(); }
            });
            return true;
        }
        public void delegate(GeflipperMouseMotion.Action action, Point point, NewMenuEntry entry) {
            assertEquals(GeflipperMouseMotion.Action.MOVE, action, "Back may delegate movement, never the unvalidated press");
            position = point;
            moves++;
            afterMove.run();
        }
        public MenuEntry pendingMenu() { return pending; }
        public void pendingMenu(MenuEntry entry) {
            pending = entry;
            if (entry != null) afterPublish.run();
        }
        public void clicked(Point point) { }
        public long millis() { return 0; }
        public void sleep(long duration) { }
    }
}
