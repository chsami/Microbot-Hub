package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PluginLifecycleTest
{
    @Test
    public void enablingLoggedOutWaitsForLogin() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.thread.runNext();
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlays.anyMatch(overlay -> true));
    }

    @Test
    public void disableRejectsQueuedStartup() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.disable();
        fixture.thread.runNext();
        assertEquals("Stopped", fixture.plugin.getStatus());
        fixture.thread.runNext();
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlays.anyMatch(overlay -> true));
    }

    @Test
    public void rapidReenableRejectsOldCleanup() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.thread.runNext();
        fixture.disable();
        fixture.enable();
        fixture.plugin.setStatus("New enable");
        fixture.thread.runNext();
        assertEquals("New enable", fixture.plugin.getStatus());
        fixture.thread.runNext();
        assertEquals("Waiting for login", fixture.plugin.getStatus());
    }

    @Test
    public void disableImmediatelyPreventsQueuedActionsAndTickStartup() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.thread.runNext();
        inject(fixture.plugin, "started", true);
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = player();
        AtomicInteger actions = new AtomicInteger();
        fixture.actions.enqueue(actions::incrementAndGet);
        fixture.disable();
        fixture.plugin.onClientTick(new ClientTick());
        fixture.plugin.onGameTick(new GameTick());
        assertEquals(0, actions.get());
        fixture.thread.runNext();
        fixture.actions.dispatch();
        assertEquals(0, actions.get());
        assertEquals("Stopped", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
    }

    @Test
    public void loggedInWithoutLocalPlayerStillWaits() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
    }

    @Test
    public void disablingDuringStartupCannotContinueIntoUninitializedTasks() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = player();
        fixture.disableOnStateRead = true;
        fixture.plugin.onGameTick(new GameTick());
        assertEquals("Stopped", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
    }

    @Test
    public void pausedScriptsStopTheLapUntilResumed() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.thread.runNext();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = player();
        Microbot.pauseAllScripts.set(true);
        try
        {
            fixture.plugin.onGameTick(new GameTick());
            assertEquals("Paused", fixture.plugin.getStatus());
            assertNull(fixture.plugin.getStartTime());
        }
        finally
        {
            Microbot.pauseAllScripts.set(false);
        }
    }

    private static final class Fixture
    {
        final FourtifysAgilityPlugin plugin = new FourtifysAgilityPlugin();
        final QueuedClientThread thread = new QueuedClientThread();
        final AgilityActions actions = new AgilityActions();
        final OverlayManager overlays;
        GameState gameState = GameState.LOGIN_SCREEN;
        Player player;
        boolean disableOnStateRead;

        Fixture() throws Exception
        {
            Constructor<?> constructor = OverlayManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            overlays = (OverlayManager) constructor.newInstance(new Object[constructor.getParameterCount()]);
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                    (proxy, method, args) -> {
                        switch (method.getName())
                        {
                            case "getGameState":
                                if (disableOnStateRead)
                                {
                                    disableOnStateRead = false;
                                    disable();
                                }
                                return gameState;
                            case "getLocalPlayer":
                                return player;
                            case "getGameCycle":
                                return 0;
                            default:
                                return null;
                        }
                    });
            inject(plugin, "client", client);
            inject(plugin, "clientThread", thread);
            inject(plugin, "config", new AgilityConfig() { });
            inject(plugin, "actions", actions);
            inject(plugin, "overlays", overlays);
        }

        void enable() throws Exception
        {
            lifecycle("startUp");
        }
        void disable() throws Exception
        {
            lifecycle("shutDown");
        }

        private void lifecycle(String name) throws Exception
        {
            java.lang.reflect.Method method = FourtifysAgilityPlugin.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(plugin);
        }
    }

    private static final class QueuedClientThread extends ClientThread
    {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        @Override
        public void invokeLater(Runnable action)
        {
            tasks.addLast(action);
        }
        void runNext()
        {
            tasks.removeFirst().run();
        }
    }

    private static Player player()
    {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> null);
    }

    private static void inject(Object instance, String name, Object value) throws Exception
    {
        if (name.equals("enabled") || name.equals("started"))
        {
            Field lifecycleField = instance.getClass().getDeclaredField("lifecycle");
            lifecycleField.setAccessible(true);
            Object state = lifecycleField.get(instance);
            Field stateField = state.getClass().getDeclaredField(name);
            stateField.setAccessible(true);
            stateField.set(state, value);
            return;
        }
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
