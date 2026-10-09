package net.runelite.client.plugins.microbot.geflipper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static net.runelite.client.plugins.microbot.geflipper.FlipperOverlayPrivacyTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual plugin/script startup, with synthetic preferences and a scheduler that never runs tasks. */
public class FlipperSettingsLifecycleTest {
    @TempDir Path temporary;

    @Test
    @SuppressWarnings("unchecked")
    public void startupConfigEventsAndShutdownPreservePreferencesAndSharedSettings() throws Exception {
        Logger own = (Logger) LoggerFactory.getLogger("net.runelite.client.plugins.microbot.geflipper");
        Level previousLevel = own.getLevel();
        boolean previousNatural = Rs2AntibanSettings.naturalMouse;
        boolean previousDynamic = Rs2AntibanSettings.dynamicIntensity;
        Field intensity = field(Rs2Antiban.class, "activityIntensity");
        Object previousIntensity = intensity.get(null);
        boolean previousPause = Microbot.pauseAllScripts.get();
        Field profileName = field(ConfigManager.class, "configProfileName");
        Object previousProfileName = profileName.get(null);
        FlipperPlugin plugin = new FlipperPlugin();
        FlipperScript script = new FlipperScript();
        try {
            ScheduledExecutorService scheduler = inertScheduler();
            Constructor<?> managerConstructor = ConfigManager.class.getDeclaredConstructors()[0];
            managerConstructor.setAccessible(true);
            // The inert scheduler never sends config; no real profile, client, or network connector is supplied.
            ConfigManager manager = (ConfigManager) managerConstructor.newInstance(
                null, scheduler, new EventBus(), null, null, null, null, null);
            Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            Object data = dataConstructor.newInstance(temporary.resolve("synthetic.properties").toFile());
            field(ConfigManager.class, "configProfile").set(manager, data);
            Map<String, String> preferences = (Map<String, String>) field(dataType, "properties").get(data);
            preferences.put("Flipper Config.slotActionMode", "MENU_OPTION");
            preferences.put("Flipper Config.slotActionStyle", "CLICK_INTO_ITEM");
            preferences.put("flippingcopilot.slotActionSwap", "false");
            preferences.put("microbot.enableAutoRunOn", "false");
            preferences.put("microbot.useStaminaPotsIfNeeded", "false");
            preferences.put("Flipper Config.waitingMouseOffScreen", "true");
            preferences.put("Flipper Config.waitingMouseChance", "77");
            Map<String, String> expected = new HashMap<>(preferences);
            FlipperConfig config = new FlipperConfig() {
                @Override public boolean waitingMouseOffScreen() { return true; }
            };
            FlipperOverlay overlay = new FlipperOverlay(plugin, config);
            field(FlipperPlugin.class, "config").set(plugin, config);
            field(FlipperPlugin.class, "configManager").set(plugin, manager);
            field(FlipperPlugin.class, "flipperScript").set(plugin, script);
            field(FlipperPlugin.class, "overlay").set(plugin, overlay);
            field(Script.class, "scheduledExecutorService").set(script, scheduler);
            ScheduledFuture<?> secondary = inertFuture();
            field(Script.class, "scheduledFuture").set(script, secondary);
            Rs2AntibanSettings.naturalMouse = false;
            Rs2AntibanSettings.dynamicIntensity = true;
            intensity.set(null, ActivityIntensity.HIGH);
            Microbot.pauseAllScripts.set(true);

            overlay.publishStats(0, syntheticStats());
            plugin.startUp();
            assertEmpty(overlay);
            ScheduledFuture<?> main = (ScheduledFuture<?>) field(Script.class, "mainScheduledFuture").get(script);
            assertFalse(main.isCancelled());
            for (String key : new String[]{"slotActionMode", "verboseLogging", "waitingMouseOffScreen", "waitingMouseChance"}) {
                ConfigChanged changed = new ConfigChanged();
                changed.setGroup("Flipper Config");
                changed.setKey(key);
                plugin.onConfigChanged(changed);
            }
            long generation = field(FlipperOverlay.class, "statsGeneration").getLong(overlay);
            overlay.publishStats(generation, syntheticStats());
            plugin.shutDown();

            assertEquals(expected, preferences, "Lifecycle must not migrate or change any preference");
            assertTrue(((Map<?, ?>) field(dataType, "patchChanges").get(data)).isEmpty(), "No config patch may be queued");
            assertFalse(Rs2AntibanSettings.naturalMouse, "Natural mouse is a user/shared setting");
            assertTrue(Rs2AntibanSettings.dynamicIntensity, "Dynamic intensity must remain unchanged");
            assertSame(ActivityIntensity.HIGH, intensity.get(null));
            assertTrue(Microbot.pauseAllScripts.get(), "Shutdown must not unpause other scripts");
            assertTrue(main.isCancelled(), "Our main schedule must stop");
            assertTrue(secondary.isCancelled(), "Our secondary schedule must stop");
            assertEmpty(overlay);
            assertFalse(overlay.publishStats(generation, syntheticStats()), "Shutdown must invalidate queued stats");
        } finally {
            script.shutdown();
            own.setLevel(previousLevel);
            Rs2AntibanSettings.naturalMouse = previousNatural;
            Rs2AntibanSettings.dynamicIntensity = previousDynamic;
            intensity.set(null, previousIntensity);
            Microbot.pauseAllScripts.set(previousPause);
            profileName.set(null, previousProfileName);
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
