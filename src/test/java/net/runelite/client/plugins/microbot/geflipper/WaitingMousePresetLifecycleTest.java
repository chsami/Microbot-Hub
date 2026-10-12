package net.runelite.client.plugins.microbot.geflipper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.microbot.Script;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.field;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic preferences and an inert scheduler keep lifecycle verification away from the client. */
class WaitingMousePresetLifecycleTest {
    private static final String GROUP = "Flipper Config";
    @TempDir Path temporary;

    @Test
    void startupProfileAndConfigReplayPreserveManualAndUnrelatedPreferences() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.preferences.put(key("waitingMousePreset"), "SEMI_AFK");
            Map<String, String> expected = new HashMap<>(f.preferences);
            f.start();
            int initial = f.script.waitingMouseFrequency(f.config);
            assertTrue(initial >= 47 && initial <= 53);
            assertEquals(initial, f.script.waitingMouseFrequency(f.config));

            for (String setting : new String[]{"waitingMouseOffScreen", "waitingMouseChance",
                "waitingMousePreset", "waitingMouseTimeSource", "waitingMouseTime", "slotActionMode", "verboseLogging", "randomizeMouseSpeed",
                "selectedSetup", "saveSetup", "loadSetup", "finish"}) {
                f.replay(GROUP, setting);
            }
            f.replay("MicrobotAntiban", "settings");
            f.plugin.onProfileChanged(new ProfileChanged());
            f.plugin.onProfileChanged(new ProfileChanged());
            int restored = f.script.waitingMouseFrequency(f.config);
            assertTrue(restored >= 47 && restored <= 53);
            assertEquals(restored, f.script.waitingMouseFrequency(f.config));
            f.stop();

            assertEquals(expected, f.preferences,
                "Lifecycle replay must not replace the manual value, master switch or other preferences");
            assertTrue(f.patchChanges.isEmpty(), "Lifecycle must not queue any preference writes");
        }
    }

    @Test
    void sessionPresetsUseTheirEffectiveValuesWithoutSavingOverTheManualSlider() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.preferences.put(key("waitingMouseOffScreen"), "false");
            f.start();
            FlipperConfig.RandomizationPreset[] presets = {
                FlipperConfig.RandomizationPreset.AFK,
                FlipperConfig.RandomizationPreset.SEMI_AFK,
                FlipperConfig.RandomizationPreset.ATTENTIVE_HUMAN,
                FlipperConfig.RandomizationPreset.CUSTOM
            };
            int[] effective = {25, 50, 75, 77};
            for (int i = 0; i < presets.length; i++) {
                // Synthetic profile contents change without invoking any configuration writer.
                f.preferences.put(key("waitingMousePreset"), presets[i].name());
                Map<String, String> expected = new HashMap<>(f.preferences);
                f.replay(GROUP, "waitingMousePreset");
                f.plugin.onProfileChanged(new ProfileChanged());
                int actual = f.script.waitingMouseFrequency(f.config);
                if (presets[i] == FlipperConfig.RandomizationPreset.CUSTOM) {
                    assertEquals(effective[i], actual);
                } else {
                    assertTrue(actual >= effective[i] - 3 && actual <= effective[i] + 3);
                }
                assertEquals(actual, f.script.waitingMouseFrequency(f.config));
                assertEquals(expected, f.preferences);
                assertEquals("77", f.preferences.get(key("waitingMouseChance")));
                assertFalse(f.config.waitingMouseOffScreen(), "A preset cannot enable the master switch");
                assertTrue(f.patchChanges.isEmpty(), "Resolving a preset must not persist its effective value");
            }
        }
    }

    @Test
    void explicitSliderEditSelectsCustomAndWritesOnlyItsTwoOwnedKeys() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.preferences.put(key("waitingMousePreset"), "DAY_FATIGUE");
            f.preferences.put(key("waitingMouseOffScreen"), "false");
            f.start();
            Map<String, String> expected = new HashMap<>(f.preferences);
            int[] requested = {-10, 63, 110};
            int[] saved = {0, 63, 100};
            for (int i = 0; i < requested.length; i++) {
                f.plugin.saveWaitingMouseChance(requested[i]);
                expected.put(key("waitingMousePreset"), "CUSTOM");
                expected.put(key("waitingMouseChance"), Integer.toString(saved[i]));
                assertEquals(expected, f.preferences);
                assertEquals(Set.of(key("waitingMousePreset"), key("waitingMouseChance")),
                    f.patchChanges.keySet(), "Only an explicit edit may write these owned settings");
                assertEquals(saved[i], f.script.waitingMouseFrequency(f.config));
                f.replay(GROUP, "waitingMousePreset");
                f.replay(GROUP, "waitingMouseChance");
                assertEquals(expected, f.preferences, "Config replay cannot undo an explicit edit");
            }
        }
    }

    @Test
    void hiddenPresetTransitionsRestartDayClockButMasterTogglePreservesIt() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.preferences.put(key("randomizeMouseSpeed"), "true");
            Instant start = Instant.parse("2026-10-09T02:00:00Z");
            Instant[] now = {start};
            Clock clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now[0], zone); }
                @Override public Instant instant() { return now[0]; }
            };
            field(FlipperScript.class, "waitingMousePresets").set(f.script,
                new WaitingMousePresets(clock, (low, high) -> low == -5 ? 0 : high));
            f.start();
            Map<String, String> expected = new HashMap<>(f.preferences);
            f.preferences.put(key("waitingMousePreset"), "DAY_FATIGUE");
            expected.put(key("waitingMousePreset"), "DAY_FATIGUE");
            f.replay(GROUP, "waitingMousePreset");
            assertTrue(f.script.waitingMouseDescription(f.config).contains("start 13:45; virtual time 13:45"));

            now[0] = start.plusSeconds(3600);
            assertTrue(f.script.waitingMouseDescription(f.config).contains("start 13:45; virtual time 14:45"));
            f.preferences.put(key("waitingMouseOffScreen"), "false");
            f.replay(GROUP, "waitingMouseOffScreen");
            assertTrue(f.script.waitingMouseDescription(f.config).contains("virtual time 14:45"),
                "Turning the master switch off cannot restart the virtual day");
            f.preferences.put(key("waitingMouseOffScreen"), "true");
            f.replay(GROUP, "waitingMouseOffScreen");
            assertTrue(f.script.waitingMouseDescription(f.config).contains("virtual time 14:45"));

            // No worker or panel read occurs between these two configuration events.
            f.preferences.put(key("waitingMousePreset"), "AFK");
            f.replay(GROUP, "waitingMousePreset");
            f.preferences.put(key("waitingMousePreset"), "DAY_FATIGUE");
            f.replay(GROUP, "waitingMousePreset");
            assertTrue(f.script.waitingMouseDescription(f.config).contains("start 13:45; virtual time 13:45"),
                "Leaving and re-entering Day Fatigue must restart even when settings are hidden");
            assertEquals(expected, f.preferences);
            assertTrue(f.patchChanges.isEmpty(), "Observing hidden transitions must remain read-only");
        }
    }

    @Test
    void hiddenSpeedOffAndOnEventsStopAndRestartDayClockWithoutSavingPreferences() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            Instant start = Instant.parse("2026-10-09T02:00:00Z");
            Instant[] now = {start};
            Clock clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now[0], zone); }
                @Override public Instant instant() { return now[0]; }
            };
            field(FlipperScript.class, "waitingMousePresets").set(f.script,
                new WaitingMousePresets(clock, (low, high) -> low == -5 ? 0 : high));
            f.preferences.put(key("waitingMousePreset"), "DAY_FATIGUE");
            f.preferences.put(key("randomizeMouseSpeed"), "true");
            f.start();
            assertTrue(f.script.waitingMouseDescription(f.config).contains("start 13:45; virtual time 13:45"));
            now[0] = start.plusSeconds(3600);
            assertTrue(f.script.waitingMouseDescription(f.config).contains("virtual time 14:45"));

            Map<String, String> expected = new HashMap<>(f.preferences);
            // The configuration events must observe both transitions even with no panel or worker read.
            f.preferences.put(key("randomizeMouseSpeed"), "false");
            f.replay(GROUP, "randomizeMouseSpeed");
            now[0] = start.plusSeconds(2 * 60 * 60);
            f.preferences.put(key("randomizeMouseSpeed"), "true");
            f.replay(GROUP, "randomizeMouseSpeed");
            assertTrue(f.script.waitingMouseDescription(f.config).contains("start 13:45; virtual time 13:45"),
                "Re-enabling after an unobserved off interval must establish a fresh anchor");
            assertEquals(expected, f.preferences);
            assertTrue(f.patchChanges.isEmpty());

            f.preferences.put(key("randomizeMouseSpeed"), "false");
            expected.put(key("randomizeMouseSpeed"), "false");
            f.replay(GROUP, "randomizeMouseSpeed");
            assertEquals(77, f.script.waitingMouseFrequency(f.config));
            assertTrue(f.script.waitingMouseDescription(f.config).contains("clock disabled"));
            f.plugin.onProfileChanged(new ProfileChanged());
            assertEquals(77, f.script.waitingMouseFrequency(f.config));
            assertEquals(expected, f.preferences);
            assertTrue(f.patchChanges.isEmpty(), "Speed and profile replay cannot rewrite time or manual values");
        }
    }

    @Test
    void localClockSurvivesLifecycleReplayWithoutMigratingSavedTimeOrWritingAnyPreferences() throws Exception {
        try (Fixture f = new Fixture(temporary)) {
            f.preferences.put(key("waitingMousePreset"), "DAY_FATIGUE");
            f.preferences.put(key("waitingMouseTimeSource"), "LOCAL_TIME");
            f.preferences.put(key("waitingMouseTime"), "invalid saved manual time");
            f.preferences.put(key("randomizeMouseSpeed"), "true");
            field(FlipperScript.class, "waitingMousePresets").set(f.script,
                new WaitingMousePresets(Clock.fixed(Instant.parse("2026-10-10T02:15:00Z"), ZoneOffset.ofHours(10)),
                    (low, high) -> low == -5 ? 0 : high));
            Map<String, String> expected = new HashMap<>(f.preferences);
            f.start();
            int initial = f.script.waitingMouseFrequency(f.config);
            assertTrue(initial >= 20 && initial <= 80);
            assertTrue(f.script.waitingMouseDescription(f.config).contains("12:15"));
            for (String setting : new String[]{"waitingMouseTimeSource", "waitingMouseTime",
                "randomizeMouseSpeed", "waitingMousePreset", "waitingMouseChance"}) f.replay(GROUP, setting);
            f.plugin.onProfileChanged(new ProfileChanged());
            assertEquals(initial, f.script.waitingMouseFrequency(f.config));
            assertTrue(f.script.waitingMouseDescription(f.config).contains("12:15"));
            f.stop();
            assertEquals(expected, f.preferences);
            assertTrue(f.patchChanges.isEmpty(), "Local time is runtime state, never a stored migration");
        }
    }

    private static String key(String item) { return GROUP + "." + item; }

    private static final class Fixture implements AutoCloseable {
        final FlipperPlugin plugin = new FlipperPlugin();
        final FlipperScript script = new FlipperScript();
        final Map<String, String> preferences;
        final Map<String, String> patchChanges;
        final FlipperConfig config;
        final Logger ownLogger = (Logger) LoggerFactory.getLogger("net.runelite.client.plugins.microbot.geflipper");
        final Level previousLogLevel = ownLogger.getLevel();
        final Object previousProfileName;
        boolean started;

        @SuppressWarnings("unchecked")
        Fixture(Path temporary) throws Exception {
            previousProfileName = field(ConfigManager.class, "configProfileName").get(null);
            ScheduledExecutorService scheduler = inertScheduler();
            Constructor<?> managerConstructor = ConfigManager.class.getDeclaredConstructors()[0];
            managerConstructor.setAccessible(true);
            ConfigManager manager = (ConfigManager) managerConstructor.newInstance(
                null, scheduler, new EventBus(), null, null, null, null, null);
            Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            Object data = dataConstructor.newInstance(temporary.resolve("synthetic-preset.properties").toFile());
            field(ConfigManager.class, "configProfile").set(manager, data);
            preferences = (Map<String, String>) field(dataType, "properties").get(data);
            patchChanges = (Map<String, String>) field(dataType, "patchChanges").get(data);
            preferences.put(key("waitingMouseOffScreen"), "true");
            preferences.put(key("waitingMouseChance"), "77");
            preferences.put(key("waitingMousePreset"), "CUSTOM");
            preferences.put(key("waitingMouseTimeSource"), "CUSTOM_TIME");
            preferences.put(key("waitingMouseTime"), "13:45");
            preferences.put(key("randomizeMouseSpeed"), "false");
            preferences.put(key("slotActionMode"), "MENU_OPTION");
            preferences.put(key("slotActionStyle"), "CLICK_INTO_ITEM");
            preferences.put(key("selectionMethod"), "MOUSE");
            preferences.put(key("showOverlay"), "false");
            preferences.put(key("verboseLogging"), "false");
            preferences.put("flippingcopilot.slotActionSwap", "false");
            preferences.put("microbot.enableAutoRunOn", "false");
            preferences.put("microbot.useStaminaPotsIfNeeded", "false");
            preferences.put("MicrobotAntiban.settings", "synthetic-unrelated-settings");
            config = new FlipperConfig() {
                @Override public boolean waitingMouseOffScreen() {
                    return Boolean.parseBoolean(preferences.get(key("waitingMouseOffScreen")));
                }
                @Override public int waitingMouseChance() {
                    return Integer.parseInt(preferences.get(key("waitingMouseChance")));
                }
                @Override public RandomizationPreset waitingMousePreset() {
                    return RandomizationPreset.valueOf(preferences.get(key("waitingMousePreset")));
                }
                @Override public TimeOfDaySource waitingMouseTimeSource() {
                    return TimeOfDaySource.valueOf(preferences.get(key("waitingMouseTimeSource")));
                }
                @Override public String waitingMouseTime() { return preferences.get(key("waitingMouseTime")); }
                @Override public boolean randomizeMouseSpeed() {
                    return Boolean.parseBoolean(preferences.get(key("randomizeMouseSpeed")));
                }
                @Override public SlotAction slotAction() { return SlotAction.MENU_OPTION; }
                @Override public boolean showOverlay() { return false; }
            };
            field(FlipperPlugin.class, "config").set(plugin, config);
            field(FlipperPlugin.class, "configManager").set(plugin, manager);
            field(FlipperPlugin.class, "flipperScript").set(plugin, script);
            field(Script.class, "scheduledExecutorService").set(script, scheduler);
        }

        void start() throws Exception {
            started = true;
            plugin.startUp();
        }

        void stop() {
            if (started) plugin.shutDown();
            started = false;
        }

        void replay(String group, String item) {
            ConfigChanged changed = new ConfigChanged();
            changed.setGroup(group);
            changed.setKey(item);
            changed.setNewValue("synthetic-replayed-value");
            plugin.onConfigChanged(changed);
        }

        @Override public void close() throws Exception {
            try {
                stop();
                script.shutdown();
            } finally {
                ownLogger.setLevel(previousLogLevel);
                field(ConfigManager.class, "configProfileName").set(null, previousProfileName);
            }
        }
    }

    private static ScheduledExecutorService inertScheduler() {
        return (ScheduledExecutorService) Proxy.newProxyInstance(ScheduledExecutorService.class.getClassLoader(),
            new Class<?>[]{ScheduledExecutorService.class}, (proxy, method, args) -> {
                if (method.getName().startsWith("schedule") || method.getName().equals("submit")) return inertFuture();
                if (method.getReturnType() == boolean.class) return false;
                if (method.getName().equals("shutdownNow")) return java.util.Collections.emptyList();
                return null;
            });
    }

    private static ScheduledFuture<?> inertFuture() {
        AtomicBoolean cancelled = new AtomicBoolean();
        return (ScheduledFuture<?>) Proxy.newProxyInstance(ScheduledFuture.class.getClassLoader(),
            new Class<?>[]{ScheduledFuture.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "cancel": cancelled.set(true); return true;
                    case "isCancelled": case "isDone": return cancelled.get();
                    case "getDelay": return 0L;
                    case "compareTo": return 0;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: return null;
                }
            });
    }
}
