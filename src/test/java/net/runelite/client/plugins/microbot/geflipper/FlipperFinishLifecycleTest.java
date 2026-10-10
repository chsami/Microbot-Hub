package net.runelite.client.plugins.microbot.geflipper;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.task.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

class FlipperFinishLifecycleTest {
    @TempDir Path temporary;

    @Test
    void completionDisablesOnlyThisPluginOnEdt() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            Map<String, String> expected = new HashMap<>(f.preferences);
            expected.put("runelite.flipperplugin", "false");
            assertTrue(f.script.requestFinish(() -> { }));
            f.markComplete();
            f.plugin.stopAfterFinish(3);
            SwingUtilities.invokeAndWait(() -> { });
            assertFalse(f.plugins.isPluginActive(f.plugin));
            assertFalse(f.script.isRunning());
            assertFalse(f.script.isFinishing());
            assertEquals(expected, f.preferences);
            assertTrue(f.stopped.onEdt);
            assertSame(f.plugin, f.stopped.plugin);
        }
    }

    @Test
    void queuedCompletionCannotStopARestartedGenerationOrAnUnfinishedSession() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            Map<String, String> expected = new HashMap<>(f.preferences);
            f.plugin.stopAfterFinish(3);
            SwingUtilities.invokeAndWait(() -> { });
            assertTrue(f.plugins.isPluginActive(f.plugin));
            assertTrue(f.script.requestFinish(() -> { }));
            f.markComplete();
            SwingUtilities.invokeAndWait(() -> {
                f.plugin.stopAfterFinish(3);
                try { field(FlipperPlugin.class, "lifecycleGeneration").setLong(f.plugin, 4); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            SwingUtilities.invokeAndWait(() -> { });
            assertTrue(f.plugins.isPluginActive(f.plugin));
            assertTrue(f.script.isRunning());
            assertNull(f.stopped.plugin);
            assertEquals(expected, f.preferences);
        }
    }

    @Test
    void aChangedSuggestionOrProfileInvalidatesQueuedCompletion() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            assertTrue(f.script.requestFinish(() -> { }));
            FinishSessionTest.Fixture copilot = f.markComplete();
            SwingUtilities.invokeAndWait(() -> {
                f.plugin.stopAfterFinish(3);
                copilot.manager.suggestion = new FinishSessionTest.Suggestion("modify_sell");
            });
            SwingUtilities.invokeAndWait(() -> { });
            assertTrue(f.plugins.isPluginActive(f.plugin));
            assertFalse(f.script.isFinishComplete());
            copilot.manager.suggestion = new FinishSessionTest.Suggestion("wait");
            assertFalse(f.script.isFinishComplete(), "Invalidated completion must be verified again");
            f.markComplete();
            assertTrue(f.script.isFinishComplete());
            f.script.pauseFinishForProfileChange();
            assertFalse(f.script.isFinishComplete());
            assertTrue(f.script.isFinishing(), "A profile switch cannot reopen buying");
            assertTrue(f.preferences.get("runelite.flipperplugin").equals("true"));
        }
    }

    @Test
    void declinedStopAllowsFreshCompletionToBeQueuedAfterCopilotRecovers() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            assertTrue(f.script.requestFinish(() -> { }));
            FinishSessionTest.Fixture copilot = f.markComplete();
            copilot.manager.refreshPending = true;
            f.plugin.stopAfterFinish(3);
            SwingUtilities.invokeAndWait(() -> { });
            assertTrue(f.plugins.isPluginActive(f.plugin));
            copilot.manager.completeRequest();
            assertFalse(f.script.isFinishComplete(), "A skipped stop cannot leave the worker parked forever");
            f.markComplete();
            f.plugin.stopAfterFinish(3);
            SwingUtilities.invokeAndWait(() -> { });
            assertFalse(f.plugins.isPluginActive(f.plugin));
            assertFalse(f.script.isRunning());
        }
    }

    @Test
    void configReplayNeverFinishesAndExplicitRequestIsIdempotentAndClearedOnStop() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            Map<String, String> expected = new HashMap<>(f.preferences);
            ConfigChanged event = new ConfigChanged();
            event.setGroup("Flipper Config");
            event.setKey("finish");
            event.setNewValue("synthetic-replayed-button-value");
            f.plugin.onConfigChanged(event);
            assertFalse(f.script.isFinishing());
            assertTrue(f.script.requestFinish(() -> { }));
            assertFalse(f.script.requestFinish(() -> fail("A repeated request cannot replace the callback")));
            f.script.shutdown();
            assertFalse(f.script.isFinishing());
            assertFalse(f.script.isFinishComplete());
            assertFalse(f.script.requestFinish(() -> { }));
            assertEquals(expected, f.preferences);
        }
    }

    public static final class StopObserver {
        Plugin plugin;
        boolean onEdt;
        @Subscribe public void onPluginChanged(PluginChanged changed) {
            if (!changed.isLoaded()) {
                plugin = changed.getPlugin();
                onEdt = SwingUtilities.isEventDispatchThread();
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final FlipperPlugin plugin = new FlipperPlugin();
        final FlipperScript script = new FlipperScript();
        final StopObserver stopped = new StopObserver();
        final FinishSessionTest.Fixture copilot = new FinishSessionTest.Fixture();
        final PluginManager plugins;
        final Map<String, String> preferences;
        final Object previousProfileName;

        @SuppressWarnings("unchecked")
        Fixture(Path temporary) throws Exception {
            previousProfileName = field(ConfigManager.class, "configProfileName").get(null);
            EventBus bus = new EventBus();
            ScheduledExecutorService scheduler = inertScheduler();
            Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            ConfigManager manager = (ConfigManager) constructor.newInstance(
                null, scheduler, bus, null, null, null, null, null);
            Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            Object data = dataConstructor.newInstance(temporary.resolve("synthetic.properties").toFile());
            field(ConfigManager.class, "configProfile").set(manager, data);
            preferences = (Map<String, String>) field(dataType, "properties").get(data);
            preferences.put("runelite.flipperplugin", "true");
            preferences.put("runelite.unrelatedplugin", "true");
            preferences.put("flippingcopilot.slotActionSwap", "false");
            preferences.put("microbot.enableAutoRunOn", "false");
            Constructor<?> pc = PluginManager.class.getDeclaredConstructors()[0];
            pc.setAccessible(true);
            Object[] args = new Object[pc.getParameterCount()];
            Class<?>[] types = pc.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == boolean.class) args[i] = false;
                if (types[i] == EventBus.class) args[i] = bus;
                if (types[i] == ConfigManager.class) args[i] = manager;
                if (types[i] == Scheduler.class) args[i] = new Scheduler();
            }
            plugins = (PluginManager) pc.newInstance(args);
            ((List<Plugin>) field(PluginManager.class, "activePlugins").get(plugins)).add(plugin);
            field(FlipperPlugin.class, "pluginManager").set(plugin, plugins);
            field(FlipperPlugin.class, "flipperScript").set(plugin, script);
            field(FlipperPlugin.class, "config").set(plugin, new FlipperConfig() { });
            field(FlipperPlugin.class, "lifecycleGeneration").setLong(plugin, 3);
            field(Script.class, "scheduledExecutorService").set(script, scheduler);
            bus.register(stopped);
            script.run(new FlipperConfig() { });
        }

        @Override public void close() throws Exception {
            script.shutdown();
            field(ConfigManager.class, "configProfileName").set(null, previousProfileName);
        }

        FinishSessionTest.Fixture markComplete() throws Exception {
            FinishSession session = (FinishSession) field(FlipperScript.class, "finishSession").get(script);
            assertTrue(session.begin(null, copilot.controller, copilot.manager));
            copilot.manager.completeRequest();
            field(FlipperScript.class, "suggestionManager").set(script, copilot.manager);
            field(FlipperScript.class, "completedFinishGeneration").setLong(script, session.generation());
            field(FlipperScript.class, "finishComplete").setBoolean(script, true);
            return copilot;
        }
    }

    private static ScheduledExecutorService inertScheduler() {
        return (ScheduledExecutorService) Proxy.newProxyInstance(ScheduledExecutorService.class.getClassLoader(),
            new Class<?>[]{ScheduledExecutorService.class}, (proxy, method, args) -> {
                if (method.getName().startsWith("schedule")) {
                    AtomicBoolean cancelled = new AtomicBoolean();
                    return Proxy.newProxyInstance(ScheduledFuture.class.getClassLoader(),
                        new Class<?>[]{ScheduledFuture.class}, (future, action, values) -> {
                            switch (action.getName()) {
                                case "cancel": cancelled.set(true); return true;
                                case "isDone": case "isCancelled": return cancelled.get();
                                case "getDelay": return 0L;
                                case "compareTo": return 0;
                                case "hashCode": return System.identityHashCode(future);
                                case "equals": return future == values[0];
                                default: return null;
                            }
                        });
                }
                return method.getReturnType() == boolean.class ? false : null;
            });
    }
}
