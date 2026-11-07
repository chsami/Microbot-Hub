package net.runelite.client.plugins.microbot.geflipper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.task.Scheduler;
import net.runelite.client.ui.ClientUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

class ManualMouseGuardTest {
    @TempDir Path temporary;
    @ParameterizedTest
    @ValueSource(ints = {MouseEvent.MOUSE_CLICKED, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
        MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_EXITED, MouseEvent.MOUSE_DRAGGED,
        MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_WHEEL})
    void physicalCanvasEventsAreBlockedButSyntheticEventsPass(int id) throws Exception {
        MouseManager manager = manager();
        Canvas canvas = new Canvas();
        try (ManualMouseGuard guard = new ManualMouseGuard(manager, () -> canvas, () -> true)) {
            guard.start();
            MouseEvent real = event(canvas, id);
            assertSame(real, dispatch(manager, real));
            assertTrue(real.isConsumed());
            BotEventGuard.begin();
            try {
                MouseEvent synthetic = event(canvas, id);
                assertSame(synthetic, dispatch(manager, synthetic));
                assertFalse(synthetic.isConsumed());
            } finally {
                BotEventGuard.end();
            }
        }
    }

    @Test
    void enabledSettingAndRunningStateAreObservedForEveryEvent() throws Exception {
        MouseManager manager = manager();
        Canvas canvas = new Canvas();
        AtomicBoolean enabled = new AtomicBoolean();
        AtomicBoolean running = new AtomicBoolean(true);
        try (ManualMouseGuard guard = new ManualMouseGuard(manager, () -> canvas,
                () -> enabled.get() && running.get())) {
            guard.start();
            assertFalse(dispatch(manager, event(canvas, MouseEvent.MOUSE_PRESSED)).isConsumed());
            enabled.set(true);
            assertTrue(dispatch(manager, event(canvas, MouseEvent.MOUSE_PRESSED)).isConsumed());
            running.set(false);
            assertFalse(dispatch(manager, event(canvas, MouseEvent.MOUSE_PRESSED)).isConsumed());
            running.set(true);
            enabled.set(false);
            assertFalse(dispatch(manager, event(canvas, MouseEvent.MOUSE_PRESSED)).isConsumed());
        }
    }

    @Test
    void sidebarEventsAndReplacedCanvasAreNotBlocked() throws Exception {
        MouseManager manager = manager();
        Canvas oldCanvas = new Canvas();
        Canvas replacement = new Canvas();
        AtomicReference<Canvas> current = new AtomicReference<>(oldCanvas);
        try (ManualMouseGuard guard = new ManualMouseGuard(manager, current::get, () -> true)) {
            guard.start();
            assertFalse(dispatch(manager, event(new javax.swing.JPanel(), MouseEvent.MOUSE_PRESSED)).isConsumed());
            current.set(replacement);
            assertFalse(dispatch(manager, event(oldCanvas, MouseEvent.MOUSE_PRESSED)).isConsumed());
            assertTrue(dispatch(manager, event(replacement, MouseEvent.MOUSE_PRESSED)).isConsumed());
            current.set(null);
            assertFalse(dispatch(manager, event(replacement, MouseEvent.MOUSE_PRESSED)).isConsumed());
        }
    }

    @Test
    void guardRunsBeforeExistingListenersAndUnregistersOnlyItself() throws Exception {
        MouseManager manager = manager();
        Canvas canvas = new Canvas();
        AtomicInteger downstreamMouse = new AtomicInteger();
        AtomicInteger downstreamWheel = new AtomicInteger();
        manager.registerMouseListener(new MouseAdapter() {
            @Override public MouseEvent mousePressed(MouseEvent event) {
                downstreamMouse.incrementAndGet();
                return event;
            }
        });
        manager.registerMouseWheelListener(event -> {
            downstreamWheel.incrementAndGet();
            return event;
        });
        ManualMouseGuard guard = new ManualMouseGuard(manager, () -> canvas, () -> true);
        guard.start();
        guard.start();
        dispatch(manager, event(canvas, MouseEvent.MOUSE_PRESSED));
        dispatch(manager, event(canvas, MouseEvent.MOUSE_WHEEL));
        assertEquals(0, downstreamMouse.get());
        assertEquals(0, downstreamWheel.get());
        guard.close();
        guard.close();
        assertFalse(dispatch(manager, event(canvas, MouseEvent.MOUSE_PRESSED)).isConsumed());
        assertFalse(dispatch(manager, event(canvas, MouseEvent.MOUSE_WHEEL)).isConsumed());
        assertEquals(1, downstreamMouse.get());
        assertEquals(1, downstreamWheel.get());
        guard.start();
        assertTrue(dispatch(manager, event(canvas, MouseEvent.MOUSE_PRESSED)).isConsumed());
        guard.close();
        assertEquals(1, ((List<?>) field(MouseManager.class, "mouseListeners").get(manager)).size());
        assertEquals(1, ((List<?>) field(MouseManager.class, "mouseWheelListeners").get(manager)).size());
    }

    @Test
    void actualSdkEmitterDeliversSyntheticTradingEventsThroughTheGuard() throws Exception {
        MouseManager manager = manager();
        DispatchCanvas canvas = new DispatchCanvas(manager);
        Field clientField = field(Microbot.class, "client");
        Object previousClient = clientField.get(null);
        AtomicLong position = (AtomicLong) field(PointerState.class, "POSITION").get(null);
        AtomicLong botPosition = (AtomicLong) field(PointerState.class, "LAST_BOT_POSITION").get(null);
        long previousPosition = position.get();
        long previousBotPosition = botPosition.get();
        Field outside = field(PointerState.class, "outside");
        boolean previousOutside = outside.getBoolean(null);
        try (ManualMouseGuard guard = new ManualMouseGuard(manager, () -> canvas, () -> true)) {
            clientField.set(null, client(canvas));
            PointerState.setInside(20, 20);
            guard.start();
            AwtEmitter.moved(20, 20);
            AwtEmitter.pressed(20, 20, MouseEvent.BUTTON1);
            AwtEmitter.released(20, 20, MouseEvent.BUTTON1);
            AwtEmitter.clicked(20, 20, MouseEvent.BUTTON1);
            AwtEmitter.wheel(20, 20, 1, 3);
            assertEquals(5, canvas.received.size(), "SDK must actually dispatch each tested event");
            assertTrue(canvas.received.stream().noneMatch(MouseEvent::isConsumed));
            assertFalse(BotEventGuard.isSynthetic(), "SDK synthetic marker must be cleared after dispatch");
            canvas.dispatchEvent(event(canvas, MouseEvent.MOUSE_PRESSED));
            assertTrue(canvas.received.get(5).isConsumed(), "Physical dispatch still blocks after SDK input");
        } finally {
            clientField.set(null, previousClient);
            position.set(previousPosition);
            botPosition.set(previousBotPosition);
            outside.setBoolean(null, previousOutside);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void pluginLifecyclePreservesIndependentToolbarAndCanvasStates(boolean enabled, boolean focusable) throws Exception {
        try (InputFixture f = new InputFixture(enabled, focusable)) {
            assertFalse(new FlipperConfig() {}.blockManualMouseInput(), "Off by default");
            assertTrue(f.option.get(), "Exercise a saved enabled setting from plugin startup");
            assertTrue(f.pressed().isConsumed());
            f.option.set(false);
            assertFalse(f.pressed().isConsumed());
            f.option.set(true);
            assertTrue(f.pressed().isConsumed());
            f.gameState.set(GameState.LOGIN_SCREEN);
            assertFalse(f.pressed().isConsumed(),
                "Manual relogin must remain possible while GE Flipper stays enabled");
            f.gameState.set(GameState.LOGGING_IN);
            assertFalse(f.pressed().isConsumed());
            f.gameState.set(GameState.LOGGED_IN);
            assertTrue(f.pressed().isConsumed());
            f.running.set(false);
            assertFalse(f.pressed().isConsumed());
            f.running.set(true);
            f.assertInputState(enabled, focusable);
            f.plugin.shutDown();
            f.assertStopped(enabled, focusable);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void actualFinishStopRemovesGuardAndPreservesPreviousInputState(boolean enabled, boolean focusable) throws Exception {
        try (InputFixture f = new InputFixture(enabled, focusable)) {
            PluginManager plugins = f.finishManager(temporary);
            Map<String, String> expected = new HashMap<>(f.preferences);
            expected.put("runelite.flipperplugin", "false");
            assertTrue(f.pressed().isConsumed());
            f.finishComplete.set(true);
            f.gameState.set(GameState.LOGIN_SCREEN);
            f.plugin.stopAfterFinish(field(FlipperPlugin.class, "lifecycleGeneration").getLong(f.plugin));
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertFalse(plugins.isPluginActive(f.plugin), "Exercise actual PluginManager shutdown after Finish");
            f.assertStopped(enabled, focusable);
            assertEquals(expected, f.preferences, "Finish changes only this plugin's enabled preference");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void shutdownPreservesLaterUserInputChangesInsteadOfRestoringStaleInitialFlags(boolean enabled, boolean focusable)
            throws Exception {
        try (InputFixture f = new InputFixture(enabled, focusable)) {
            // These are the same two component properties changed by Microbot's existing input toolbar.
            ClientUI.getClient().setEnabled(!enabled);
            f.canvas.setFocusable(!focusable);
            assertTrue(f.pressed().isConsumed(), "GE Flipper's own guard remains independent of the toolbar");
            f.plugin.shutDown();
            f.assertStopped(!enabled, !focusable);
        }
    }

    private static final class InputFixture implements AutoCloseable {
        private final Field uiClient = field(ClientUI.class, "client");
        private final Object previousUiClient = uiClient.get(null);
        private final Field profileName = field(ConfigManager.class, "configProfileName");
        private final Object previousProfileName = profileName.get(null);
        private final Logger own = (Logger) LoggerFactory.getLogger("net.runelite.client.plugins.microbot.geflipper");
        private final Level previousLevel = own.getLevel();
        private final MouseManager manager = manager();
        private final Canvas canvas = new Canvas();
        private final Component parent = new javax.swing.JPanel();
        private final AtomicBoolean option = new AtomicBoolean(true);
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean finishComplete = new AtomicBoolean();
        private final AtomicReference<GameState> gameState = new AtomicReference<>(GameState.LOGGED_IN);
        private final FlipperPlugin plugin = new FlipperPlugin();
        private Map<String, String> preferences;

        InputFixture(boolean enabled, boolean focusable) throws Exception {
            parent.setEnabled(enabled);
            canvas.setFocusable(focusable);
            FlipperConfig config = new FlipperConfig() {
                @Override public boolean blockManualMouseInput() { return option.get(); }
            };
            FlipperScript script = new FlipperScript() {
                @Override public boolean run(FlipperConfig ignored) { running.set(true); return true; }
                @Override public boolean isRunning() { return running.get(); }
                @Override public boolean isFinishComplete() { return finishComplete.get(); }
                @Override public void shutdown() { running.set(false); }
            };
            field(FlipperPlugin.class, "mouseManager").set(plugin, manager);
            field(FlipperPlugin.class, "client").set(plugin, client(canvas, gameState));
            field(FlipperPlugin.class, "config").set(plugin, config);
            field(FlipperPlugin.class, "flipperScript").set(plugin, script);
            uiClient.set(null, parent);
            try {
                plugin.startUp();
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        MouseEvent pressed() { return dispatch(manager, event(canvas, MouseEvent.MOUSE_PRESSED)); }

        void assertInputState(boolean enabled, boolean focusable) {
            assertSame(parent, ClientUI.getClient());
            assertEquals(enabled, ClientUI.getClient().isEnabled(), "Preserve actual toolbar parent enabled flag");
            assertEquals(focusable, canvas.isFocusable(), "Preserve independent game canvas focusability");
        }

        void assertStopped(boolean enabled, boolean focusable) throws Exception {
            assertFalse(running.get());
            assertFalse(pressed().isConsumed());
            assertTrue(((List<?>) field(MouseManager.class, "mouseListeners").get(manager)).isEmpty());
            assertTrue(((List<?>) field(MouseManager.class, "mouseWheelListeners").get(manager)).isEmpty());
            assertNull(field(FlipperPlugin.class, "manualMouseGuard").get(plugin));
            assertInputState(enabled, focusable);
        }

        @SuppressWarnings("unchecked")
        PluginManager finishManager(Path temporary) throws Exception {
            EventBus bus = new EventBus();
            ScheduledExecutorService scheduler = (ScheduledExecutorService) Proxy.newProxyInstance(
                ScheduledExecutorService.class.getClassLoader(), new Class<?>[]{ScheduledExecutorService.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
            Constructor<?> configConstructor = ConfigManager.class.getDeclaredConstructors()[0];
            configConstructor.setAccessible(true);
            ConfigManager configManager = (ConfigManager) configConstructor.newInstance(
                null, scheduler, bus, null, null, null, null, null);
            Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            Object data = dataConstructor.newInstance(temporary.resolve("synthetic-input.properties").toFile());
            field(ConfigManager.class, "configProfile").set(configManager, data);
            preferences = (Map<String, String>) field(dataType, "properties").get(data);
            preferences.put("runelite.flipperplugin", "true");
            preferences.put("runelite.unrelatedplugin", "true");
            preferences.put("flippingcopilot.slotActionSwap", "false");
            Constructor<?> constructor = PluginManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object[] args = new Object[constructor.getParameterCount()];
            Class<?>[] types = constructor.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == boolean.class) args[i] = false;
                if (types[i] == EventBus.class) args[i] = bus;
                if (types[i] == ConfigManager.class) args[i] = configManager;
                if (types[i] == Scheduler.class) {
                    Constructor<?> sc = Scheduler.class.getDeclaredConstructors()[0];
                    sc.setAccessible(true);
                    args[i] = sc.getParameterCount() == 0 ? sc.newInstance() : sc.newInstance(scheduler);
                }
            }
            PluginManager plugins = (PluginManager) constructor.newInstance(args);
            ((List<Plugin>) field(PluginManager.class, "activePlugins").get(plugins)).add(plugin);
            field(FlipperPlugin.class, "pluginManager").set(plugin, plugins);
            return plugins;
        }

        @Override public void close() throws Exception {
            try {
                plugin.shutDown();
            } finally {
                uiClient.set(null, previousUiClient);
                profileName.set(null, previousProfileName);
                own.setLevel(previousLevel);
            }
        }
    }

    private static MouseManager manager() throws Exception {
        Constructor<MouseManager> constructor = MouseManager.class.getDeclaredConstructor(RuneLiteConfig.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new RuneLiteConfig() {});
    }

    private static Client client(Canvas canvas) {
        return client(canvas, new AtomicReference<>(GameState.LOGGED_IN));
    }

    private static Client client(Canvas canvas, AtomicReference<GameState> gameState) {
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getCanvas": return canvas;
                    case "getGameState": return gameState.get();
                    case "getCanvasWidth": case "getCanvasHeight": return 100;
                    case "getRealDimensions": case "getStretchedDimensions": return new Dimension(100, 100);
                    case "isStretchedEnabled": return false;
                    default:
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        return null;
                }
            });
    }

    private static MouseEvent event(Component source, int id) {
        if (id == MouseEvent.MOUSE_WHEEL)
            return new MouseWheelEvent(source, id, 0L, 0, 20, 20, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1);
        int button = id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED
            || id == MouseEvent.MOUSE_CLICKED ? MouseEvent.BUTTON1 : MouseEvent.NOBUTTON;
        return new MouseEvent(source, id, 0L, 0, 20, 20, 1, false, button);
    }

    private static MouseEvent dispatch(MouseManager manager, MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_CLICKED: return manager.processMouseClicked(event);
            case MouseEvent.MOUSE_PRESSED: return manager.processMousePressed(event);
            case MouseEvent.MOUSE_RELEASED: return manager.processMouseReleased(event);
            case MouseEvent.MOUSE_ENTERED: return manager.processMouseEntered(event);
            case MouseEvent.MOUSE_EXITED: return manager.processMouseExited(event);
            case MouseEvent.MOUSE_DRAGGED: return manager.processMouseDragged(event);
            case MouseEvent.MOUSE_MOVED: return manager.processMouseMoved(event);
            case MouseEvent.MOUSE_WHEEL: return manager.processMouseWheelMoved((MouseWheelEvent) event);
            default: throw new AssertionError("Unexpected mouse event");
        }
    }

    private static final class DispatchCanvas extends Canvas {
        private final MouseManager manager;
        private final List<MouseEvent> received = new ArrayList<>();

        DispatchCanvas(MouseManager manager) {
            this.manager = manager;
            setSize(100, 100);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        }

        @Override protected void processMouseEvent(MouseEvent event) { received.add(dispatch(manager, event)); }
        @Override protected void processMouseMotionEvent(MouseEvent event) { received.add(dispatch(manager, event)); }
        @Override protected void processMouseWheelEvent(MouseWheelEvent event) { received.add(dispatch(manager, event)); }
    }
}
