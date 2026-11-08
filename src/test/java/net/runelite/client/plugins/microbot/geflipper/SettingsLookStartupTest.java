package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Canvas;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.microbot.ui.MicrobotTopLevelConfigPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Microbot starts the game and loads plugins before it applies its dark theme. Building its settings panel that
 * early leaves the whole Microbot side panel white, so the panel may only be asked for once Microbot's window is
 * on screen with its own theme, whatever state the game is in.
 */
class SettingsLookStartupTest {
    private final AtomicBoolean windowUp = new AtomicBoolean(false);
    private final AtomicInteger panelRequests = new AtomicInteger();
    private final List<Object> registered = new ArrayList<>();
    private final EventBus bus = new EventBus() {
        @Override public synchronized void register(Object subscriber) { registered.add(subscriber); }
        @Override public synchronized void unregister(Object subscriber) { registered.remove(subscriber); }
    };

    private FlipperPlugin plugin() throws Exception {
        FlipperPlugin plugin = new FlipperPlugin();
        // The game has already reached the login screen while Microbot is still starting.
        set(plugin, "client", client(GameState.LOGIN_SCREEN, null));
        plugin.clientUiReady = windowUp::get;
        Provider<MicrobotTopLevelConfigPanel> panel = () -> {
            panelRequests.incrementAndGet();
            throw new IllegalStateException("No settings panel in this test");
        };
        set(plugin, "settingsPanelProvider", panel);
        set(plugin, "config", new FlipperConfig() { });
        return plugin;
    }

    @Test
    void theSettingsPanelIsLeftAloneUntilMicrobotsWindowIsUp() throws Exception {
        FlipperPlugin plugin = plugin();
        plugin.installSettingsLook(bus);
        Thread.sleep(2500);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(0, panelRequests.get(), "Nothing may build Microbot's panel during client start-up");
        assertEquals(1, registered.size());

        // Switched on during start-up: still left alone.
        SwingUtilities.invokeAndWait(plugin::startSettingsLook);
        assertEquals(0, panelRequests.get());

        windowUp.set(true);
        long deadline = System.currentTimeMillis() + 5000;
        while (panelRequests.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(50);
        Thread.sleep(2500);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, panelRequests.get(), "The look starts once the window is up, and is tried only once");
        assertTrue(registered.isEmpty(), "The wait is over");
    }

    @Test
    void switchingThePluginOnInARunningClientStartsTheLookStraightAway() throws Exception {
        windowUp.set(true);
        FlipperPlugin plugin = plugin();
        plugin.installSettingsLook(bus);
        SwingUtilities.invokeAndWait(plugin::startSettingsLook);
        assertEquals(1, panelRequests.get());
        assertTrue(registered.isEmpty());
    }

    @Test
    void unloadingThePluginEndsTheWait() throws Exception {
        FlipperPlugin plugin = plugin();
        plugin.installSettingsLook(bus);
        Object watch = registered.get(0);
        watch.getClass().getMethod("onPluginChanged", PluginChanged.class).invoke(watch, new PluginChanged(plugin, false));
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue(registered.isEmpty());
        windowUp.set(true);
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(0, panelRequests.get());
    }

    @Test
    void theRealCheckWaitsForTheGameWindowToBeOnScreen() {
        assertFalse(FlipperPlugin.clientUiReady(null));
        assertFalse(FlipperPlugin.clientUiReady(client(GameState.LOGGED_IN, null)), "No game window yet");
        assertFalse(FlipperPlugin.clientUiReady(client(GameState.LOGGED_IN, new Canvas())), "Window not on screen yet");
    }

    private static Client client(GameState state, Canvas canvas) {
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> "getGameState".equals(method.getName()) ? state
                : "getCanvas".equals(method.getName()) ? canvas : null);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
