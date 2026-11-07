package net.runelite.client.plugins.microbot.geflipper;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
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
    @SuppressWarnings("unchecked")
    void finishDefersBreaksUntilGeFlipperStopsAndNeverSwitchesBreakHandlerOff() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            BreakHandlerScript.setLockState(false);
            BreakHandlerV2Plugin breakHandler = new BreakHandlerV2Plugin();
            ((List<Plugin>) field(PluginManager.class, "plugins").get(f.plugins)).add(breakHandler);
            ((List<Plugin>) field(PluginManager.class, "activePlugins").get(f.plugins)).add(breakHandler);
            f.preferences.put("runelite.breakhandlerv2plugin", "true");
            f.gameState.set(GameState.LOGGED_IN);

            AtomicBoolean started = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> started.set(f.plugin.requestFinish()));
            assertTrue(started.get());
            assertTrue(BreakHandlerScript.isLockState(), "Breaks wait while Finish lists items");

            f.markComplete();
            f.plugin.stopAfterFinish(3);
            SwingUtilities.invokeAndWait(() -> { });
            assertTrue(f.plugins.isPluginActive(f.plugin), "Still waiting for the game to log out");
            assertFalse(f.script.isRunning(), "No more trading or mouse movement while waiting");
            assertEquals("Waiting for logout", f.script.getActivity());
            assertTrue(f.posted.stream().anyMatch(e -> e instanceof AutoLoginSuppressionRequest),
                "Auto-login is held off while waiting for the idle logout");

            f.gameState.set(GameState.LOGIN_SCREEN);
            SwingUtilities.invokeAndWait(() -> f.tickLogoutHold());
            assertFalse(f.plugins.isPluginActive(f.plugin));
            assertEquals("false", f.preferences.get("runelite.flipperplugin"));
            assertTrue(f.posted.stream().anyMatch(e -> e instanceof AutoLoginSuppressionRequest
                && ((AutoLoginSuppressionRequest) e).isLoggedOut()), "The account stays logged out");
            assertFalse(BreakHandlerScript.isLockState(), "The break lock GE Flipper took is given back");
            assertTrue(f.plugins.isPluginActive(breakHandler), "Break Handler is never switched off");
            assertEquals("true", f.preferences.get("runelite.breakhandlerv2plugin"));
        } finally {
            BreakHandlerScript.setLockState(false);
        }
    }

    @Test
    void aBreakLockSomeoneElseHoldsIsLeftInPlace() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            BreakHandlerScript.setLockState(true);
            AtomicBoolean started = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> started.set(f.plugin.requestFinish()));
            assertTrue(started.get());
            SwingUtilities.invokeAndWait(() -> f.plugin.shutDown());
            assertTrue(BreakHandlerScript.isLockState());
        } finally {
            BreakHandlerScript.setLockState(false);
        }
    }

    @Test
    void aRefusedFinishTakesNoBreakLock() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            BreakHandlerScript.setLockState(false);
            assertTrue(f.script.requestFinish(() -> { }));
            AtomicBoolean started = new AtomicBoolean(true);
            SwingUtilities.invokeAndWait(() -> started.set(f.plugin.requestFinish()));
            assertFalse(started.get(), "A second Finish is refused");
            assertFalse(BreakHandlerScript.isLockState());
        } finally {
            BreakHandlerScript.setLockState(false);
        }
    }

    @Test
    void stoppingGeFlipperWhileWaitingForTheLogoutEndsTheHold() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.gameState.set(GameState.LOGGED_IN);
            assertTrue(f.script.requestFinish(() -> { }));
            f.markComplete();
            f.plugin.stopAfterFinish(3);
            SwingUtilities.invokeAndWait(() -> { });
            assertNotNull(field(FlipperPlugin.class, "logoutHold").get(f.plugin));
            SwingUtilities.invokeAndWait(() -> f.plugin.shutDown());
            assertNull(field(FlipperPlugin.class, "logoutHold").get(f.plugin));
            assertNull(field(FlipperPlugin.class, "logoutHoldTimer").get(f.plugin));
            assertNotEquals("Waiting for logout", f.script.getActivity());
        }
    }

    /** Same simple name as Microbot's real break handler. */
    @net.runelite.client.plugins.PluginDescriptor(name = "Break Handler V2")
    public static final class BreakHandlerV2Plugin extends Plugin {
        @Override protected void startUp() { }
        @Override protected void shutDown() { }
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
            event.setGroup(FlipperConfig.GROUP);
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

    @Test
    void pluginStartupWiresSystemUpdateHandlerAndUpgradesActiveMuleLiquidationIntoFinish() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.plugin.startUp();
            java.util.function.BooleanSupplier handler = (java.util.function.BooleanSupplier) field(FlipperScript.class, "systemUpdateHandler").get(f.script);
            assertNotNull(handler, "startUp must wire systemUpdateHandler");

            // Simulate an active mule sell-down (muleFinish = true, finishSession requested = true)
            FinishSession session = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            assertTrue(session.request(true));
            field(FlipperScript.class, "muleFinish").setBoolean(f.script, true);
            assertFalse(f.script.isFinishing(), "Mule sell-down must not disable user/system-update Finish");

            // Triggering the system update handler adopts the mule sell-down as a real Finish
            assertTrue(handler.getAsBoolean());
            assertTrue(f.script.isFinishing());
            assertFalse(field(FlipperScript.class, "muleFinish").getBoolean(f.script));

            f.plugin.shutDown();
            assertNull(field(FlipperScript.class, "systemUpdateHandler").get(f.script));
        }
    }

    @Test
    void userFinishOnlyNeedsEverySellOfferListedNotFilled() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            assertTrue(f.script.requestFinish(() -> { }));
            FinishSession session = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            assertTrue(session.begin(null, f.copilot.controller, f.copilot.manager));
            f.copilot.manager.completeRequest();
            FinishSession.CompletionSnapshot listedButUnsold =
                new FinishSession.CompletionSnapshot(true, true, true, true, false, true);
            assertFalse(session.completionReady(listedButUnsold, 100));
            assertTrue(session.completionReady(listedButUnsold, 100 + FinishSession.COMPLETE_STABLE_MS),
                "Active sell offers must not keep Finish from stopping the plugin");
            assertTrue(f.script.isFinishing());
        }
    }

    @Test
    void adoptingAMuleSellDownIntoFinishAlsoStopsWaitingForFills() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.plugin.startUp();
            FinishSession session = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            assertTrue(session.request(true), "A mule sell-down waits for sales to fill");
            field(FlipperScript.class, "muleFinish").setBoolean(f.script, true);
            assertTrue(f.script.requestFinish(() -> { }));
            assertTrue(session.begin(null, f.copilot.controller, f.copilot.manager));
            f.copilot.manager.completeRequest();
            FinishSession.CompletionSnapshot listedButUnsold =
                new FinishSession.CompletionSnapshot(true, true, true, true, false, true);
            assertFalse(session.completionReady(listedButUnsold, 100));
            assertTrue(session.completionReady(listedButUnsold, 100 + FinishSession.COMPLETE_STABLE_MS));
        }
    }

    @Test
    void adoptingAMuleSellDownIntoFinishEndsTheMuleLiquidationToo() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            FinishSession session = (FinishSession) field(FlipperScript.class, "finishSession").get(f.script);
            MuleSession mule = (MuleSession) field(FlipperScript.class, "muleSession").get(f.script);
            assertTrue(session.request(true));
            field(FlipperScript.class, "muleFinish").setBoolean(f.script, true);
            field(MuleSession.class, "liquidating").setBoolean(mule, true);
            field(MuleSession.class, "liquidationTargetGp").setInt(mule, 5_000_000);
            assertTrue(mule.liquidationRequested(), "The mule sell-down is under way");

            assertTrue(f.script.requestFinish(() -> { }));
            assertFalse(mule.liquidationRequested(), "From now on the user's Finish owns the sell-down");
            assertEquals(0, mule.liquidationTargetGp());
        }
    }

    @Test
    void completedFinishDisablesOnlyThisPluginAfterTheQueuedEdtStop() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            Map<String, String> expected = new HashMap<>(f.preferences);
            expected.put("runelite.flipperplugin", "false");
            assertTrue(f.script.requestFinish(() -> { }));
            f.markComplete();
            CountDownLatch swingHeld = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                swingHeld.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(swingHeld.await(10, TimeUnit.SECONDS));
            try {
                f.plugin.stopAfterFinish(3);
                assertTrue(f.plugins.isPluginActive(f.plugin));
                assertTrue(f.script.isRunning());
            } finally { release.countDown(); }
            SwingUtilities.invokeAndWait(() -> { });
            assertFalse(f.plugins.isPluginActive(f.plugin));
            assertFalse(f.script.isRunning());
            assertEquals(expected, f.preferences, "Finish changes only GEFlipper's enabled state");
            assertSame(f.plugin, f.stopped.plugin);
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

    static final class Fixture implements AutoCloseable {
        final FlipperPlugin plugin;
        final FlipperScript script = new FlipperScript();
        final StopObserver stopped = new StopObserver();
        final FinishSessionTest.Fixture copilot = new FinishSessionTest.Fixture();
        final PluginManager plugins;
        final Map<String, String> preferences;
        final Object previousProfileName;
        /** The game state the plugin sees; logged out by default so a completed Finish stops at once. */
        final AtomicReference<GameState> gameState = new AtomicReference<>(GameState.LOGIN_SCREEN);
        final List<Object> posted = new CopyOnWriteArrayList<>();

        Fixture(Path temporary) throws Exception {
            this(temporary, new FlipperPlugin());
        }

        @SuppressWarnings("unchecked")
        Fixture(Path temporary, FlipperPlugin plugin) throws Exception {
            this.plugin = plugin;
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
                if (types[i] == Scheduler.class) {
                    Constructor<?> sc = Scheduler.class.getDeclaredConstructors()[0];
                    sc.setAccessible(true);
                    args[i] = sc.getParameterCount() == 0 ? sc.newInstance() : sc.newInstance(scheduler);
                }
            }
            plugins = (PluginManager) pc.newInstance(args);
            ((List<Plugin>) field(PluginManager.class, "activePlugins").get(plugins)).add(plugin);
            field(FlipperPlugin.class, "pluginManager").set(plugin, plugins);
            field(FlipperPlugin.class, "flipperScript").set(plugin, script);
            field(FlipperPlugin.class, "config").set(plugin, new FlipperConfig() { });
            field(FlipperPlugin.class, "lifecycleGeneration").setLong(plugin, 3);
            field(FlipperPlugin.class, "client").set(plugin, Proxy.newProxyInstance(Client.class.getClassLoader(),
                new Class<?>[]{Client.class}, (proxy, method, args1) -> {
                    if ("getGameState".equals(method.getName())) return gameState.get();
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                }));
            field(FlipperPlugin.class, "eventBus").set(plugin, new EventBus() {
                @Override public void post(Object event) { posted.add(event); }
            });
            field(Script.class, "scheduledExecutorService").set(script, scheduler);
            bus.register(stopped);
            script.run(new FlipperConfig() { });
        }

        @Override public void close() throws Exception {
            script.shutdown();
            field(ConfigManager.class, "configProfileName").set(null, previousProfileName);
        }

        void tickLogoutHold() {
            try {
                FinishLogoutHold hold = (FinishLogoutHold) field(FlipperPlugin.class, "logoutHold").get(plugin);
                if (hold != null) hold.tick(System.nanoTime() / 1_000_000L);
            } catch (Exception error) {
                throw new AssertionError(error);
            }
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
