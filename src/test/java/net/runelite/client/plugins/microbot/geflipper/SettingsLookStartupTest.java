package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.ui.MicrobotTopLevelConfigPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Microbot loads plugins before it applies its dark theme. Building its settings panel that early left the whole
 * Microbot side panel white, so the panel may only be asked for once the client has started.
 */
class SettingsLookStartupTest {
    private final AtomicReference<GameState> state = new AtomicReference<>(GameState.STARTING);
    private final AtomicInteger panelRequests = new AtomicInteger();
    private final List<Object> registered = new ArrayList<>();
    private final EventBus bus = new EventBus() {
        @Override public void register(Object subscriber) { registered.add(subscriber); }
        @Override public void unregister(Object subscriber) { registered.remove(subscriber); }
    };

    private FlipperPlugin plugin() throws Exception {
        FlipperPlugin plugin = new FlipperPlugin();
        set(plugin, "client", Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> "getGameState".equals(method.getName()) ? state.get() : null));
        Provider<MicrobotTopLevelConfigPanel> panel = () -> {
            panelRequests.incrementAndGet();
            throw new IllegalStateException("No settings panel in this test");
        };
        set(plugin, "settingsPanelProvider", panel);
        set(plugin, "config", new FlipperConfig() { });
        return plugin;
    }

    @Test
    void theSettingsPanelIsLeftAloneUntilTheGameReachesTheLoginScreen() throws Exception {
        FlipperPlugin plugin = plugin();
        plugin.installSettingsLook(bus);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(0, panelRequests.get(), "Nothing may build Microbot's panel during client start-up");
        assertEquals(1, registered.size());

        Object waiter = registered.get(0);
        GameStateChanged loading = new GameStateChanged();
        loading.setGameState(GameState.STARTING);
        waiter.getClass().getMethod("onGameStateChanged", GameStateChanged.class).invoke(waiter, loading);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(0, panelRequests.get());

        GameStateChanged login = new GameStateChanged();
        login.setGameState(GameState.LOGIN_SCREEN);
        waiter.getClass().getMethod("onGameStateChanged", GameStateChanged.class).invoke(waiter, login);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, panelRequests.get(), "The look starts once the client is up");
        assertTrue(registered.isEmpty(), "The one-off listener is removed");
    }

    @Test
    void aPluginLoadedIntoARunningClientStartsTheLookStraightAway() throws Exception {
        state.set(GameState.LOGGED_IN);
        FlipperPlugin plugin = plugin();
        plugin.installSettingsLook(bus);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, panelRequests.get());
        assertTrue(registered.isEmpty());
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
