package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
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
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
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

/** Real prompt/slot entry points with synthetic client state and recorded output only. */
class FlipperTradingInputTest {
    @TempDir Path temporary;

    @Test void priceAndQuantityStillSubmitAfterSuccessfulMousePopulation() throws Exception {
        for (boolean quantity : new boolean[]{false, true}) try (Fixture f = new Fixture(temporary)) {
            f.script.quantity = quantity;
            assertTrue(f.prompt());
            assertEquals(List.of(KeyEvent.VK_ENTER), f.script.keys);
            assertEquals("123", f.input);
        }
    }

    @Test void staleNumericTitlesCannotAuthorizeInactiveInputOrADifferentSuggestedItem() throws Exception {
        for (String invalid : new String[]{"inactive", "wrongMode", "wrongItem", "noSelectedSlot"}) {
            try (Fixture f = new Fixture(temporary)) {
                if (invalid.equals("inactive")) f.inputMode = 0;
                if (invalid.equals("wrongMode")) f.inputMode = 1;
                if (invalid.equals("wrongItem")) f.itemId++;
                if (invalid.equals("noSelectedSlot")) f.selectedSlot = 0;
                assertNull(f.script.captureKeyboardTarget(f.prompt.widget), invalid);
                assertTrue(f.prompt());
                assertTrue(f.script.keys.isEmpty());
            }
        }
        try (Fixture f = new Fixture(temporary)) {
            f.inputMode = 30;
            assertTrue(f.prompt());
            assertEquals(List.of(KeyEvent.VK_ENTER), f.script.keys, "Both native numeric modes remain supported");
        }
    }

    @Test void changedOwnershipDuringTheFinalDelayCannotSubmitThePrompt() throws Exception {
        for (String changed : new String[]{"stop", "pause", "human", "interrupt", "logout", "generation", "prompt",
            "mode", "item", "slot", "suggestion", "mutatedSuggestion", "copilotPause", "copilotDisabled"}) {
            try (Fixture f = new Fixture(temporary)) {
                f.script.beforeEnter = () -> f.change(changed);
                assertTrue(f.prompt(), changed);
                assertTrue(f.script.keys.isEmpty(), "No Enter after " + changed);
            } finally { Thread.interrupted(); }
        }
    }

    @Test void fallbackTypingStopsBetweenCharactersAndCannotFallThroughToEnterOrEscape() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.script.populateOnClick = false;
            f.script.afterCharacter = () -> f.script.running = false;
            assertTrue(f.prompt());
            assertEquals("1", f.script.typed.toString());
            assertTrue(f.script.keys.isEmpty());
        }
    }

    @Test void deferredPopulationAndQuickSetRecheckTheOriginalPromptAndRunningGeneration() throws Exception {
        for (boolean quickSet : new boolean[]{false, true}) {
            for (String changed : new String[]{"none", "stop", "pause", "generation", "prompt", "mode",
                "item", "slot", "suggestion", "mutatedSuggestion", "copilotPause", "copilotDisabled"}) {
                try (Fixture f = new Fixture(temporary)) {
                    FlipperScript.KeyboardTarget target = f.script.captureKeyboardTarget(f.prompt.widget);
                    assertNotNull(target);
                    Method callback = quickSet
                        ? FlipperScript.class.getDeclaredMethod("triggerCopilotQuickSet", FlipperScript.KeyboardTarget.class)
                        : FlipperScript.class.getDeclaredMethod("setCopilotChatboxValueDirectly",
                            long.class, FlipperScript.KeyboardTarget.class);
                    callback.setAccessible(true);
                    if (quickSet) callback.invoke(f.script, target);
                    else callback.invoke(f.script, 123L, target);
                    assertEquals(1, f.deferred.size());
                    if (!changed.equals("none")) f.change(changed);
                    f.drain();
                    int writes = quickSet ? f.copilot.keybindHandler.keyListener.writes
                        : f.copilot.keybindHandler.offerHandler.writes;
                    assertEquals(changed.equals("none") ? 1 : 0, writes, changed + ", quickSet=" + quickSet);
                }
            }
        }
    }

    @Test void itemSelectionKeyboardFallbackRetainsItsNonnumericSearchOwnership() throws Exception {
        for (String changed : new String[]{"none", "stop", "pause", "generation", "prompt"}) {
            try (Fixture f = new Fixture(temporary)) {
                f.script.itemSelection = true;
                f.prompt.text = "Copilot item:";
                f.inputMode = 0;
                f.itemId = -1;
                f.script.afterWait = () -> { if (!changed.equals("none")) f.change(changed); };
                assertTrue(f.prompt());
                assertEquals(changed.equals("none") ? List.of(KeyEvent.VK_ENTER) : List.of(), f.script.keys, changed);
            }
        }
    }

    @Test void failedPopulationEscapeAlsoRequiresTheOriginalLivePrompt() throws Exception {
        for (String changed : new String[]{"none", "stop", "pause", "generation", "prompt"}) {
            try (Fixture f = new Fixture(temporary)) {
                f.script.populateOnClick = false;
                f.button.text = "No suggested number is available";
                f.manager.suggestion.price = 0;
                f.script.afterWait = () -> { if (!changed.equals("none")) f.change(changed); };
                assertTrue(f.prompt());
                assertEquals(changed.equals("none") ? List.of(KeyEvent.VK_ESCAPE) : List.of(), f.script.keys, changed);
                assertEquals("", f.script.typed.toString());
            }
        }
    }

    @Test void pausedOrDisabledCopilotCannotActOnItsRetainedNativeSlotRecommendation() throws Exception {
        for (FlipperConfig.SlotAction mode : FlipperConfig.SlotAction.values()) {
            for (String changed : new String[]{"copilotPause", "copilotDisabled", "copilotInactive", "error"}) {
                try (Fixture f = new Fixture(temporary)) {
                    f.prepareSlot(mode);
                    f.change(changed);
                    assertTrue(f.slot());
                    assertEquals(0, f.backend.moves, mode + ": " + changed);
                    assertTrue(f.backend.events.isEmpty());
                }
            }
        }
    }

    @Test void copilotPauseDuringMovementCancelsBothNativeSlotDispatchModes() throws Exception {
        for (FlipperConfig.SlotAction mode : FlipperConfig.SlotAction.values()) {
            try (Fixture f = new Fixture(temporary)) {
                f.prepareSlot(mode);
                f.backend.afterMove = () -> f.controller.paused.paused = true;
                assertTrue(f.slot());
                assertEquals(1, f.backend.moves);
                assertTrue(f.backend.events.isEmpty(), mode.name());
            }
        }
    }

    @Test void healthyExplicitSlotActionStillDispatchesTheVerifiedAbort() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.prepareSlot(FlipperConfig.SlotAction.MENU_OPTION);
            assertTrue(f.slot());
            assertEquals(List.of("press", "release", "click"), f.backend.events);
            assertEquals("Abort offer", f.backend.pending.getOption());
            assertEquals(2, f.backend.pending.getIdentifier());
        }
    }

    @Test void finishDuringAnOwnedAfkLogoutResumesThatLoginAndDoesNotStartAnotherSnooze() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            int[] logins = {0};
            SecondScreenAfk afk = new SecondScreenAfk(new SecondScreenAfk.Port() {
                public boolean loggedIn() { return f.gameState == GameState.LOGGED_IN; }
                public int currentWorld() { return 302; }
                public boolean exchangeOrBankOpen() { return false; }
                public void closeInterfaces() { fail("Finish cannot close interfaces through AFK"); }
                public void moveMouseOffScreen() { fail("Finish cannot start another AFK snooze"); }
                public void logout() { fail("Finish must not log the account out"); }
                public void login(int world) { assertEquals(302, world); logins[0]++; f.gameState = GameState.LOGGED_IN; }
            }, (min, max) -> min);
            FlipperConfig.SecondScreenAfkMode mode = FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS;
            afk.onSettledWaitTick(mode, 60, false, true, 10_000);
            afk.onSettledWaitTick(mode, 60, false, true, 10_000 + SecondScreenAfk.SETTLE_MIN_MS);
            assertEquals(SecondScreenAfk.Phase.SNOOZING, afk.phase());
            field(FlipperScript.class, "secondScreenAfk").set(f.script, afk);
            field(FlipperScript.class, "config").set(f.script, new FlipperConfig() {
                @Override public SecondScreenAfkMode secondScreenAfkMode() { return mode; }
            });
            f.gameState = GameState.LOGIN_SCREEN;
            assertTrue(f.script.requestFinish(() -> { }));
            assertEquals(SecondScreenAfk.Phase.LOGGING_IN, afk.phase());
            assertTrue((Boolean) call(f.script, "tickSecondScreenAfkLoggedOut"));
            assertEquals(1, logins[0], "The accepted Finish must not erase the AFK-owned login");
            assertTrue(f.script.isFinishing());
            assertFalse((Boolean) call(f.script, "tickSecondScreenAfkBeforeTrading"));
            assertEquals(SecondScreenAfk.Phase.WORKING, afk.phase());
        }
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = FlipperScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    @PluginDescriptor(name = "Synthetic input Copilot", enabledByDefault = true)
    public static final class Copilot extends Plugin {
        private Object suggestionController;
        private final KeybindHandler keybindHandler = new KeybindHandler();
    }
    public static final class KeybindHandler {
        private final OfferHandler offerHandler = new OfferHandler();
        private final QuickSet keyListener = new QuickSet();
    }
    public static final class QuickSet {
        int writes;
        private void handleKeybind(boolean set, boolean skip, boolean graph) { writes++; }
    }
    public static final class OfferHandler { int writes; public void setChatboxValue(long value) { writes++; } }
    public static final class Manager {
        private Suggestion suggestion = new Suggestion();
        Object error;
        public Object getSuggestionError() { return error; }
    }
    public static final class Suggestion {
        int itemId = 100;
        long price = 123L;
        boolean abort;
        public String getType() { return abort ? "abort" : "sell"; }
        public int getBoxId() { return 0; }
        public int getItemId() { return itemId; }
        public String getName() { return "Synthetic item"; }
        public long getPrice() { return price; }
        public int getQuantity() { return 123; }
        public boolean isAbortSuggestion() { return abort; }
        public boolean isModifySuggestion() { return false; }
    }

    private static final class TestScript extends FlipperScript {
        Fixture fixture;
        boolean running = true, quantity, itemSelection, populateOnClick = true;
        Runnable beforeEnter = () -> { }, afterCharacter = () -> { }, afterWait = () -> { };
        final List<Integer> keys = new ArrayList<>();
        final StringBuilder typed = new StringBuilder();
        @Override public boolean isRunning() { return running; }
        @Override boolean isMuleActive() { return false; }
        @Override public boolean isSlotActionSwapEnabled() { return true; }
        @Override Widget findSuggestedItemWidget() { return itemSelection ? fixture.prompt.widget : null; }
        @Override boolean isUiWidgetVisible(int id) { return true; }
        @Override Widget findUiWidget(String text, List<Widget> children, boolean exact) {
            if (text.equals("Set a price for each item:")) return quantity ? null : fixture.prompt.widget;
            if (text.equals("How many do you wish to ")) return quantity ? fixture.prompt.widget : null;
            if (text.equals("to set to Copilot")) return fixture.button.widget;
            throw new AssertionError(text);
        }
        @Override boolean clickTradingWidget(Widget widget) {
            if (populateOnClick) fixture.input = "123";
            return true;
        }
        @Override boolean waitForUi(BooleanSupplier condition, int timeoutMs) {
            boolean result = condition.getAsBoolean();
            afterWait.run();
            return result;
        }
        @Override void dispatchTradingKey(int key) { keys.add(key); }
        @Override void dispatchTradingCharacter(char key) { typed.append(key); fixture.input += key; afterCharacter.run(); }
        @Override void pauseBeforeTradingKey() { beforeEnter.run(); }
    }

    private static final class Fixture implements AutoCloseable {
        final Field clientField = field(Microbot.class, "client"), threadField = field(Microbot.class, "clientThread"),
            pluginField = field(Microbot.class, "pluginManager");
        final Object previousClient = clientField.get(null), previousThread = threadField.get(null),
            previousPlugins = pluginField.get(null);
        final boolean previousPause = Microbot.pauseAllScripts.get(), previousDisabled = InputArbiter.isDisabled();
        final MenuEntry previousMenu = Microbot.targetMenu;
        final AtomicLong pointer = (AtomicLong) field(PointerState.class, "POSITION").get(null);
        final AtomicLong botPointer = (AtomicLong) field(PointerState.class, "LAST_BOT_POSITION").get(null);
        final long previousPointer = pointer.get(), previousBotPointer = botPointer.get();
        final Field outsideField = field(PointerState.class, "outside");
        final boolean previousOutside = outsideField.getBoolean(null);
        final TestScript script = new TestScript();
        final Copilot copilot = new Copilot();
        final Manager manager = new Manager();
        final CopilotWaitingTest.Controller controller = new CopilotWaitingTest.Controller();
        final Map<Integer, Node> nodes = new HashMap<>();
        final List<Runnable> deferred = new ArrayList<>();
        final Canvas canvas = new Canvas();
        final Node prompt = node(1001), button = node(1002), inputNode = node((162 << 16) | 44);
        final Backend backend = new Backend();
        final PluginManager plugins;
        final Map<String, String> preferences;
        GameState gameState = GameState.LOGGED_IN;
        String input = "";
        int inputMode = 7, selectedSlot = 1, itemId = 100;
        boolean onClientThread;
        GrandExchangeOffer[] offers;
        final ClientThread inline = new ClientThread() {
            @Override public <T> Optional<T> runOnClientThreadOptional(Callable<T> task) {
                boolean previous = onClientThread;
                onClientThread = true;
                try { return Optional.ofNullable(task.call()); }
                catch (Exception failure) { throw new AssertionError(failure); }
                finally { onClientThread = previous; }
            }
            @Override public void invokeLater(Runnable task) { deferred.add(task); }
        };

        @SuppressWarnings("unchecked") Fixture(Path temporary) throws Exception {
            EventBus bus = new EventBus();
            ScheduledExecutorService inert = (ScheduledExecutorService) Proxy.newProxyInstance(
                ScheduledExecutorService.class.getClassLoader(), new Class<?>[]{ScheduledExecutorService.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
            Constructor<?> configConstructor = ConfigManager.class.getDeclaredConstructors()[0];
            configConstructor.setAccessible(true);
            ConfigManager config = (ConfigManager) configConstructor.newInstance(null, inert, bus, null, null, null, null, null);
            Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            Object data = dataConstructor.newInstance(temporary.resolve("synthetic-input.properties").toFile());
            field(ConfigManager.class, "configProfile").set(config, data);
            preferences = (Map<String, String>) field(dataType, "properties").get(data);
            preferences.put("runelite.copilot", "true");
            Constructor<?> pluginConstructor = PluginManager.class.getDeclaredConstructors()[0];
            pluginConstructor.setAccessible(true);
            Object[] arguments = new Object[pluginConstructor.getParameterCount()];
            Class<?>[] types = pluginConstructor.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == boolean.class) arguments[i] = false;
                if (types[i] == EventBus.class) arguments[i] = bus;
                if (types[i] == ConfigManager.class) arguments[i] = config;
                if (types[i] == Scheduler.class) {
                    Constructor<?> scheduler = Scheduler.class.getDeclaredConstructors()[0];
                    scheduler.setAccessible(true);
                    arguments[i] = scheduler.getParameterCount() == 0 ? scheduler.newInstance() : scheduler.newInstance(inert);
                }
            }
            plugins = (PluginManager) pluginConstructor.newInstance(arguments);
            ((List<Plugin>) field(PluginManager.class, "activePlugins").get(plugins)).add(copilot);
            node(InterfaceID.GeOffers.CONTENTS);
            node(InterfaceID.GeOffers.SETUP);
            prompt.text = "Set a price for each item:";
            button.text = "Press [E] to set to Copilot price: 123 gp";
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getGameState")) return gameState;
                    if (method.getName().equals("getCanvas")) return canvas;
                    if (method.getName().equals("isClientThread")) return onClientThread;
                    assertTrue(onClientThread, "Client read outside client thread: " + method.getName());
                    switch (method.getName()) {
                        case "getVarcIntValue": return inputMode;
                        case "getVarcStrValue": return input;
                        case "getVarpValue": return itemId;
                        case "getVarbitValue": return selectedSlot;
                        case "getCanvasWidth": case "getCanvasHeight": return 100;
                        case "getMouseCanvasPosition": return backend.position;
                        case "isMenuOpen": return false;
                        case "getGrandExchangeOffers": return offers;
                        case "getWidget": {
                            int id = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                            Node node = nodes.get(id);
                            return node == null ? null : node.widget;
                        }
                        default: throw new AssertionError("Unexpected client read: " + method.getName());
                    }
                });
            clientField.set(null, client);
            threadField.set(null, inline);
            pluginField.set(null, plugins);
            Microbot.pauseAllScripts.set(false);
            Microbot.targetMenu = null;
            InputArbiter.setDisabled(true);
            script.fixture = this;
            copilot.suggestionController = controller;
            field(FlipperScript.class, "flippingCopilot").set(script, copilot);
            field(FlipperScript.class, "suggestionManager").set(script, manager);
            field(FlipperScript.class, "highlightController").set(script, new Object());
            field(FlipperScript.class, "config").set(script, new FlipperConfig() {
                @Override public SelectionMethod selectionMethod() { return SelectionMethod.MOUSE; }
            });
            field(FlipperScript.class, "mouseMotion").set(script, new GeflipperMouseMotion(backend,
                (min, max) -> 100, new Random(3),
                (nature, random, x, y) -> nature.getSystemCalls().setMousePosition(x, y)));
        }

        boolean prompt() throws Exception { return (Boolean) call(script, "checkAndPressCopilotKeybind"); }
        boolean slot() throws Exception { return (Boolean) call(script, "checkAndAbortOrModifyIfNeeded"); }
        void drain() { for (Runnable task : new ArrayList<>(deferred)) inline.runOnClientThreadOptional(() -> { task.run(); return true; }); }
        void prepareSlot(FlipperConfig.SlotAction mode) throws Exception {
            nodes.remove(InterfaceID.GeOffers.SETUP);
            node(InterfaceID.GeOffers.INDEX);
            Node slot = node(InterfaceID.GeOffers.INDEX_0);
            slot.child = new Node(slot.id, 2);
            slot.child.parent = slot;
            slot.child.actions = new String[]{"View offer", "Abort offer", "Modify offer"};
            manager.suggestion.abort = true;
            offers = new GrandExchangeOffer[8];
            offers[0] = (GrandExchangeOffer) Proxy.newProxyInstance(GrandExchangeOffer.class.getClassLoader(),
                new Class<?>[]{GrandExchangeOffer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getItemId")) return itemId;
                    if (method.getName().equals("getState")) return GrandExchangeOfferState.BUYING;
                    throw new AssertionError(method.getName());
                });
            field(FlipperScript.class, "config").set(script, new FlipperConfig() {
                @Override public SlotAction slotAction() { return mode; }
            });
        }

        @SuppressWarnings("unchecked") void change(String what) {
            try {
                switch (what) {
                    case "stop": script.shutdown(); script.running = false; break;
                    case "pause": Microbot.pauseAllScripts.set(true); break;
                    case "human": InputArbiter.setDisabled(false); InputArbiter.onRealKeyPressed(KeyEvent.VK_SHIFT); break;
                    case "interrupt": Thread.currentThread().interrupt(); break;
                    case "logout": gameState = GameState.LOGIN_SCREEN; break;
                    case "generation": script.invalidateMouseMovement(); break;
                    case "prompt": nodes.put(prompt.id, new Node(prompt.id, -1)); break;
                    case "mode": inputMode++; break;
                    case "item": itemId++; break;
                    case "slot": selectedSlot++; break;
                    case "suggestion": manager.suggestion = new Suggestion(); break;
                    case "mutatedSuggestion": manager.suggestion.itemId++; break;
                    case "copilotPause": controller.paused.paused = true; break;
                    case "copilotDisabled": preferences.put("runelite.copilot", "false"); break;
                    case "copilotInactive": ((List<Plugin>) field(PluginManager.class, "activePlugins").get(plugins)).clear(); break;
                    case "error": manager.error = new Object(); break;
                    default: throw new AssertionError(what);
                }
            } catch (Exception failure) { throw new AssertionError(failure); }
        }

        Node node(int id) { Node node = new Node(id, -1); nodes.put(id, node); return node; }
        @Override public void close() throws Exception {
            ((FinishSession) field(FlipperScript.class, "finishSession").get(script)).close();
            Microbot.pauseAllScripts.set(previousPause);
            Microbot.targetMenu = previousMenu;
            InputArbiter.resetForTest();
            InputArbiter.setDisabled(previousDisabled);
            pointer.set(previousPointer);
            botPointer.set(previousBotPointer);
            outsideField.setBoolean(null, previousOutside);
            clientField.set(null, previousClient);
            threadField.set(null, previousThread);
            pluginField.set(null, previousPlugins);
            ((ScheduledExecutorService) field(ClientThread.class, "scheduledExecutorService").get(inline)).shutdownNow();
        }

        final class Node {
            final int id, index;
            String text = "";
            String[] actions;
            Node child, parent;
            final Rectangle bounds = new Rectangle(20, 30, 35, 25);
            final Widget widget;
            Node(int id, int index) {
                this.id = id; this.index = index;
                widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getId": return id;
                            case "getIndex": return index;
                            case "getParent": return parent == null ? null : parent.widget;
                            case "getChild": return child == null ? null : child.widget;
                            case "isHidden": return false;
                            case "getBounds": return bounds;
                            case "getActions": return actions;
                            case "getText": return this == inputNode ? input : text;
                            case "getChildren": case "getDynamicChildren": case "getStaticChildren": case "getNestedChildren": return null;
                            default: throw new AssertionError(method.getName());
                        }
                    });
            }
        }
    }

    private static final class Backend implements GeflipperMouseMotion.Backend {
        final MouseMotionNature nature = new MouseMotionNature();
        final List<String> events = new ArrayList<>();
        Point position = new Point(1, 1);
        MenuEntry pending;
        int moves;
        Runnable afterMove = () -> { };
        Backend() {
            nature.setTimeToStepsDivider(8); nature.setMinSteps(10); nature.setEffectFadeSteps(15);
            nature.setReactionTimeBaseMs(0); nature.setReactionTimeVariationMs(0);
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
                public void move(int x, int y) { position = new Point(x, y); PointerState.setInside(x, y); moves++; afterMove.run(); }
                public void press(int x, int y) { events.add("press"); }
                public void release(int x, int y) { events.add("release"); }
                public void click(int x, int y) { events.add("click"); }
            }); return true;
        }
        public void delegate(GeflipperMouseMotion.Action action, Point point, NewMenuEntry entry) { fail("No live input"); }
        public MenuEntry pendingMenu() { return pending; }
        public void pendingMenu(MenuEntry entry) { pending = entry; }
        public void clicked(Point point) { }
        public long millis() { return 0; }
        public void sleep(long duration) { }
    }
}
