package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
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
import net.runelite.client.task.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual waiting collection path with client-thread-only reads and no live input. */
class FlipperWaitCollectTest {
    @TempDir Path temporary;

    @Test
    void aCurrentFundsWaitCollectsThroughTheNativeOperationOnceInEitherSpeedMode() throws Exception {
        for (boolean varying : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(temporary, varying)) {
                Map<String, String> before = new HashMap<>(f.preferences);
                assertNotNull(f.bounds(f.manager, f.controller));
                assertTrue(f.script.collectCompletedWhileWaiting());
                assertEquals(Arrays.asList("press", "release", "click"), f.backend.events);
                assertEquals("Collect to inventory", f.backend.pending.getOption());
                assertEquals(MenuAction.CC_OP, f.backend.pending.getType());
                assertEquals(1, f.backend.pending.getIdentifier());
                assertEquals(0, f.backend.pending.getParam0());
                assertEquals(InterfaceID.GeOffers.COLLECTALL, f.backend.pending.getParam1());
                assertFalse(f.script.collectCompletedWhileWaiting(), "A successful scan consumes the minute");
                assertEquals(3, f.backend.events.size());
                assertEquals(before, f.preferences, "Recovery must not write Copilot or shared preferences");
            }
        }
    }

    @Test
    void anOrdinaryWaitDoesNotScanOffersAndCannotConsumeTheFundsCheck() throws Exception {
        try (Fixture f = new Fixture(temporary, false)) {
            f.panelText("Waiting for a profitable flip");
            assertFalse(f.script.collectCompletedWhileWaiting());
            assertEquals(0, f.offerReads, "An ordinary WAIT cannot trigger the completed-offer watchdog");
            assertEquals(0, f.backend.moves);
            assertTrue(f.backend.events.isEmpty());
            f.panelText("<html><center>Not enough funds</center></html>");
            assertTrue(f.script.collectCompletedWhileWaiting(), "A current funds indication gets its first check");
            assertTrue(f.offerReads > 0);
        }
    }

    @Test
    void detachedHiddenOrWrongManagerFundsLabelsCannotTriggerAScan() throws Exception {
        for (String condition : new String[]{"panelManager", "detachedLabel", "hiddenContainer", "preferencesPanel"}) {
            try (Fixture f = new Fixture(temporary, false)) {
                f.block(condition);
                assertFalse(f.script.collectCompletedWhileWaiting(), condition);
                assertEquals(0, f.offerReads, condition);
                assertEquals(0, f.backend.moves, condition);
                assertTrue(f.backend.events.isEmpty(), condition);
            }
        }
    }

    @Test
    void unsafeWaitingStatesCannotAuthorizeMovementOrCollection() throws Exception {
        for (String condition : new String[]{"paused", "error", "notWait", "menu", "prompt", "popup",
            "fullInventory", "disabledCopilot", "inactiveCopilot", "stopped", "finish",
            "pausedScripts", "closedOverview", "setup", "bank", "blockedSlot", "missingButton"}) {
            try (Fixture f = new Fixture(temporary, false)) {
                f.block(condition);
                assertNull(f.bounds(f.manager, f.controller), condition);
                assertFalse(f.script.collectCompletedWhileWaiting(), condition);
                assertEquals(0, f.backend.moves, condition);
                assertTrue(f.backend.events.isEmpty(), condition);
            }
        }
    }

    @Test
    void earlierManagerOrControllerCannotAuthorizeTheCurrentBoard() throws Exception {
        try (Fixture f = new Fixture(temporary, false)) {
            assertNull(f.bounds(new CopilotWaitingTest.Manager(), f.controller));
            assertNull(f.bounds(f.manager, new CopilotWaitingTest.Controller()));
            assertNotNull(f.bounds(f.manager, f.controller));
            f.copilot.suggestionController = new CopilotWaitingTest.Controller();
            assertNull(f.bounds(f.manager, f.controller));
        }
    }

    @Test
    void partialOrUnavailableOffersCannotAuthorizeTheRealRecoveryPath() throws Exception {
        for (String condition : new String[]{"activeOnly", "missingOffer", "unknownOffer"}) {
            try (Fixture f = new Fixture(temporary, false)) {
                f.block(condition);
                assertNull(f.bounds(f.manager, f.controller));
                assertFalse(f.script.collectCompletedWhileWaiting());
                assertEquals(0, f.backend.moves);
                assertTrue(f.backend.events.isEmpty());
            }
        }
    }

    @Test
    void safetyChangesDuringNaturalMovementCancelBeforeAnyPress() throws Exception {
        for (boolean varying : new boolean[]{false, true}) {
            for (String condition : new String[]{"notWait", "error", "menu", "prompt", "popup",
                "fullInventory", "disabledCopilot", "finish", "activeOnly", "missingButton",
                "ordinaryWait", "panelManager", "detachedLabel", "hiddenContainer", "preferencesPanel",
                "changedSuggestion"}) {
                try (Fixture f = new Fixture(temporary, varying)) {
                    f.backend.afterMove = () -> {
                        try { f.block(condition); }
                        catch (Exception failure) { throw new AssertionError(failure); }
                    };
                    assertFalse(f.script.collectCompletedWhileWaiting(), condition);
                    assertTrue(f.backend.moves > 0, condition);
                    assertTrue(f.backend.events.isEmpty(), condition);
                    assertNull(f.backend.pending, condition);
                    assertFalse(f.script.collectCompletedWhileWaiting(), "A cancelled scan still consumes the minute");
                }
            }
        }
    }

    private static final class TestScript extends FlipperScript {
        boolean running = true;
        @Override public boolean isRunning() { return running; }
    }

    @PluginDescriptor(name = "Synthetic waiting Copilot", enabledByDefault = true)
    public static final class Copilot extends Plugin {
        private Object suggestionController;
        private MainPanel mainPanel;
    }

    public static final class MainPanel extends JPanel {
        private final CopilotPanel copilotPanel;
        MainPanel(Object manager) { copilotPanel = new CopilotPanel(manager); add(copilotPanel); }
    }

    public static final class CopilotPanel extends JPanel {
        private final SuggestionPanel suggestionPanel;
        CopilotPanel(Object manager) { suggestionPanel = new SuggestionPanel(manager); add(suggestionPanel); }
    }

    public static final class SuggestionPanel extends JPanel {
        private Object suggestionManager;
        private boolean isPreferencesPanelVisible;
        private final JPanel suggestionTextContainer = new JPanel();
        private final JLabel suggestionText = new JLabel() {
            @Override public String getText() {
                assertTrue(SwingUtilities.isEventDispatchThread(), "Copilot label must be read on EDT");
                return super.getText();
            }
        };
        SuggestionPanel(Object manager) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            suggestionManager = manager;
            suggestionText.setText("Add at least <FONT COLOR=yellow>1,000</FONT> gp<br>to your inventory<br>to get a flip suggestion");
            suggestionTextContainer.add(suggestionText);
            add(suggestionTextContainer);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Field clientField = field(Microbot.class, "client"), threadField = field(Microbot.class, "clientThread"),
            pluginField = field(Microbot.class, "pluginManager"), mouseField = field(Microbot.class, "mouse");
        final Object previousClient = clientField.get(null), previousThread = threadField.get(null),
            previousPlugins = pluginField.get(null), previousMouse = mouseField.get(null);
        final boolean previousPause = Microbot.pauseAllScripts.get();
        final MenuEntry previousPending = Microbot.targetMenu;
        final TestScript script = new TestScript();
        final Copilot copilot = new Copilot();
        final CopilotWaitingTest.Manager manager = new CopilotWaitingTest.Manager();
        final CopilotWaitingTest.Controller controller = new CopilotWaitingTest.Controller();
        final Map<Integer, Node> widgets = new HashMap<>();
        final Node contents = node(InterfaceID.GeOffers.CONTENTS, -1),
            overview = node(InterfaceID.GeOffers.INDEX, -1),
            collect = node(InterfaceID.GeOffers.COLLECTALL, -1),
            button = new Node(InterfaceID.GeOffers.COLLECTALL, 0);
        final Backend backend = new Backend();
        final GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
        final PluginManager plugins;
        final Map<String, String> preferences;
        Item[] items = {new Item(995, 100)};
        boolean onClientThread, menuOpen, promptOpen;
        int offerReads;
        final ClientThread inline = new ClientThread() {
            @Override public <T> Optional<T> runOnClientThreadOptional(Callable<T> call) {
                boolean wasClientThread = onClientThread;
                onClientThread = true;
                try { return Optional.ofNullable(call.call()); }
                catch (Exception failure) { throw new AssertionError(failure); }
                finally { onClientThread = wasClientThread; }
            }
        };

        @SuppressWarnings("unchecked")
        Fixture(Path temporary, boolean varying) throws Exception {
            EventBus bus = new EventBus();
            ScheduledExecutorService inert = (ScheduledExecutorService) Proxy.newProxyInstance(
                ScheduledExecutorService.class.getClassLoader(), new Class<?>[]{ScheduledExecutorService.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
            Constructor<?> configConstructor = ConfigManager.class.getDeclaredConstructors()[0];
            configConstructor.setAccessible(true);
            ConfigManager config = (ConfigManager) configConstructor.newInstance(null, inert, bus, null,
                null, null, null, null);
            Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            Object data = dataConstructor.newInstance(temporary.resolve("synthetic-wait.properties").toFile());
            field(ConfigManager.class, "configProfile").set(config, data);
            preferences = (Map<String, String>) field(dataType, "properties").get(data);
            preferences.put("runelite.copilot", "true");
            preferences.put("flippingcopilot.slotActionSwap", "false");
            preferences.put("microbot.enableAutoRunOn", "false");
            Constructor<?> pluginConstructor = PluginManager.class.getDeclaredConstructors()[0];
            pluginConstructor.setAccessible(true);
            Object[] arguments = new Object[pluginConstructor.getParameterCount()];
            Class<?>[] types = pluginConstructor.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == boolean.class) arguments[i] = false;
                if (types[i] == EventBus.class) arguments[i] = bus;
                if (types[i] == ConfigManager.class) arguments[i] = config;
                if (types[i] == Scheduler.class) {
                    Constructor<?> sc = Scheduler.class.getDeclaredConstructors()[0];
                    sc.setAccessible(true);
                    arguments[i] = sc.getParameterCount() == 0 ? sc.newInstance() : sc.newInstance(inert);
                }
            }
            plugins = (PluginManager) pluginConstructor.newInstance(arguments);
            ((List<Plugin>) field(PluginManager.class, "activePlugins").get(plugins)).add(copilot);
            collect.child = button;
            button.actions = new String[]{"Collect to inventory"};
            Arrays.setAll(offers, index -> offer(index == 0 ? GrandExchangeOfferState.SOLD : GrandExchangeOfferState.EMPTY));
            ItemContainer inventory = (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
                new Class<?>[]{ItemContainer.class}, (proxy, method, args) -> {
                    assertTrue(onClientThread);
                    if (method.getName().equals("getItems")) return items;
                    throw new AssertionError("Unexpected inventory read: " + method.getName());
                });
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getGameState")) return GameState.LOGGED_IN;
                    assertTrue(onClientThread, "Client UI read outside client thread: " + method.getName());
                    switch (method.getName()) {
                        case "getWidget": {
                            int id = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                            Node node = widgets.get(id);
                            return node == null ? null : node.widget;
                        }
                        case "getCanvasWidth": case "getCanvasHeight": return 100;
                        case "getMouseCanvasPosition": return backend.position;
                        case "isMenuOpen": return menuOpen;
                        case "getGrandExchangeOffers": offerReads++; return offers;
                        case "getItemContainer": return inventory;
                        case "getVarcIntValue": return promptOpen ? 1 : 0;
                        default: throw new AssertionError("Unexpected client read: " + method.getName());
                    }
                });
            clientField.set(null, client);
            threadField.set(null, inline);
            pluginField.set(null, plugins);
            mouseField.set(null, new NoInputMouse());
            Microbot.pauseAllScripts.set(false);
            Microbot.targetMenu = null;
            InputArbiter.resetForTest();
            copilot.suggestionController = controller;
            SwingUtilities.invokeAndWait(() -> copilot.mainPanel = new MainPanel(manager));
            field(FlipperScript.class, "flippingCopilot").set(script, copilot);
            field(FlipperScript.class, "suggestionManager").set(script, manager);
            field(FlipperScript.class, "config").set(script, new FlipperConfig() {
                @Override public boolean randomizeMouseSpeed() { return varying; }
            });
            field(FlipperScript.class, "mouseMotion").set(script, new GeflipperMouseMotion(backend,
                (minimum, maximum) -> 100, new Random(2),
                (nature, random, x, y) -> nature.getSystemCalls().setMousePosition(x, y)));
            script.state = State.MONITORING_COPILOT;
        }

        Rectangle bounds(Object currentManager, Object currentController) {
            return inline.runOnClientThreadOptional(() -> script.completedWaitCollectBounds(currentManager,
                currentController, manager.suggestion)).orElse(null);
        }

        SuggestionPanel suggestionPanel() {
            assertTrue(SwingUtilities.isEventDispatchThread());
            return copilot.mainPanel.copilotPanel.suggestionPanel;
        }

        void panelText(String text) throws Exception {
            SwingUtilities.invokeAndWait(() -> suggestionPanel().suggestionText.setText(text));
        }

        @SuppressWarnings("unchecked")
        void block(String condition) throws Exception {
            switch (condition) {
                case "paused": controller.paused.paused = true; break;
                case "error": manager.error = new Object(); break;
                case "notWait": manager.suggestion.wait = false; break;
                case "menu": menuOpen = true; break;
                case "prompt": promptOpen = true; break;
                case "popup": node(InterfaceID.GeOffers.POPUP, -1).text = "Are you sure?"; break;
                case "fullInventory": items = new Item[28]; Arrays.fill(items, new Item(1234, 1)); break;
                case "disabledCopilot": preferences.put("runelite.copilot", "false"); break;
                case "inactiveCopilot": ((List<Plugin>) field(PluginManager.class, "activePlugins").get(plugins)).clear(); break;
                case "stopped": script.running = false; break;
                case "finish": assertTrue(script.requestFinish(() -> { })); break;
                case "pausedScripts": Microbot.pauseAllScripts.set(true); break;
                case "closedOverview": overview.visible = false; break;
                case "setup": node(InterfaceID.GeOffers.SETUP, -1); break;
                case "bank": node((12 << 16) | 1, -1); break;
                case "blockedSlot": field(FlipperScript.class, "blockedSlotActionKey").set(script, "synthetic"); break;
                case "missingButton": collect.child = null; break;
                case "activeOnly": offers[0] = offer(GrandExchangeOfferState.SELLING); break;
                case "missingOffer": offers[7] = null; break;
                case "unknownOffer": offers[7] = offer(null); break;
                case "ordinaryWait": panelText("Waiting for a profitable flip"); break;
                case "panelManager": SwingUtilities.invokeAndWait(() ->
                    suggestionPanel().suggestionManager = new CopilotWaitingTest.Manager()); break;
                case "detachedLabel": SwingUtilities.invokeAndWait(() -> {
                    SuggestionPanel panel = suggestionPanel();
                    panel.suggestionTextContainer.remove(panel.suggestionText);
                }); break;
                case "hiddenContainer": SwingUtilities.invokeAndWait(() ->
                    suggestionPanel().suggestionTextContainer.setVisible(false)); break;
                case "preferencesPanel": SwingUtilities.invokeAndWait(() ->
                    suggestionPanel().isPreferencesPanelVisible = true); break;
                case "changedSuggestion": manager.suggestion = new CopilotWaitingTest.Suggestion(); break;
                default: throw new AssertionError(condition);
            }
        }

        Node node(int id, int index) { Node node = new Node(id, index); widgets.put(id, node); return node; }
        GrandExchangeOffer offer(GrandExchangeOfferState state) {
            return (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
                new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                    assertTrue(onClientThread);
                    if (method.getName().equals("getState")) return state;
                    throw new AssertionError("Unexpected offer read: " + method.getName());
                });
        }

        @Override public void close() throws Exception {
            ((FinishSession) field(FlipperScript.class, "finishSession").get(script)).close();
            InputArbiter.resetForTest();
            Microbot.pauseAllScripts.set(previousPause);
            Microbot.targetMenu = previousPending;
            clientField.set(null, previousClient);
            threadField.set(null, previousThread);
            pluginField.set(null, previousPlugins);
            mouseField.set(null, previousMouse);
            ((ScheduledExecutorService) field(ClientThread.class, "scheduledExecutorService").get(inline)).shutdownNow();
        }

        final class Node {
            boolean visible = true;
            String text = "";
            String[] actions;
            Node child;
            final Rectangle bounds = new Rectangle(20, 30, 35, 25);
            final Widget widget;
            Node(int id, int index) {
                widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                    (proxy, method, args) -> {
                        assertTrue(onClientThread, "Widget read outside client thread: " + method.getName());
                        switch (method.getName()) {
                            case "getId": return id;
                            case "getIndex": return index;
                            case "getChild": return child == null ? null : child.widget;
                            case "isHidden": return !visible;
                            case "getBounds": return bounds;
                            case "getActions": return actions;
                            case "getText": return text;
                            case "getChildren": case "getDynamicChildren": case "getStaticChildren":
                            case "getNestedChildren": return null;
                            default: throw new AssertionError("Unexpected widget read: " + method.getName());
                        }
                    });
            }
        }
    }

    private static final class Backend implements GeflipperMouseMotion.Backend {
        final MouseMotionNature nature = new MouseMotionNature();
        final List<String> events = new ArrayList<>();
        Point position = new Point(4, 4);
        MenuEntry pending;
        int moves;
        Runnable afterMove = () -> { };
        Backend() {
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
                public void press(int x, int y) { events.add("press"); }
                public void release(int x, int y) { events.add("release"); }
                public void click(int x, int y) { events.add("click"); }
            });
            return true;
        }
        public void delegate(GeflipperMouseMotion.Action action, Point point, NewMenuEntry entry) {
            assertEquals(GeflipperMouseMotion.Action.MOVE, action, "Only movement may use the SDK fallback");
            position = point;
            moves++;
            afterMove.run();
        }
        public MenuEntry pendingMenu() { return pending; }
        public void pendingMenu(MenuEntry entry) { pending = entry; }
        public void clicked(Point point) { }
        public long millis() { return 0; }
        public void sleep(long duration) { }
    }

    private static final class NoInputMouse extends Mouse {
        @Override public java.awt.Point getMousePosition() { return new java.awt.Point(4, 4); }
        @Override public Point getLastClick() { return new Point(4, 4); }
        @Override public void setLastClick(Point point) { }
        private Mouse input() { throw new AssertionError("Live input is forbidden in recovery tests"); }
        @Override public Mouse click(int x, int y) { return input(); }
        @Override public Mouse click(double x, double y) { return input(); }
        @Override public Mouse click(Rectangle bounds) { return input(); }
        @Override public Mouse click(int x, int y, boolean right) { return input(); }
        @Override public Mouse click(Point point) { return input(); }
        @Override public Mouse click(Point point, boolean right) { return input(); }
        @Override public Mouse click(Point point, NewMenuEntry entry) { return input(); }
        @Override public Mouse click() { return input(); }
        @Override public Mouse move(Point point) { return input(); }
        @Override public Mouse move(Rectangle bounds) { return input(); }
        @Override public Mouse move(int x, int y) { return input(); }
        @Override public Mouse move(double x, double y) { return input(); }
        @Override public Mouse move(java.awt.Polygon polygon) { return input(); }
        @Override public Mouse scrollDown(Point point) { return input(); }
        @Override public Mouse scrollUp(Point point) { return input(); }
        @Override public Mouse drag(Point from, Point to) { return input(); }
    }
}
