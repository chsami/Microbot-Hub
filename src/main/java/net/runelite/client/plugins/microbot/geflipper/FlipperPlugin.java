package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Choken + "Flipper",
        description = "Flipping copilot automation",
        tags = {"flip", "ge", "grand", "exchange", "automation"},
        authors = {"Choken", "afss0"},
        version = FlipperPlugin.version,
        minClientVersion = "2.6.26",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class FlipperPlugin extends Plugin {
    public static final String version = "1.2.63";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FlipperPlugin.class);
    @Inject
    private Client client;
    @Inject
    private FlipperScript flipperScript;

    public FlipperScript getFlipperScript() {
        return flipperScript;
    }
    @Inject
    private net.runelite.client.plugins.microbot.geflipper.FlipperConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FlipperOverlay overlay;
    @Inject
    private ConfigManager configManager;

    @Provides
    net.runelite.client.plugins.microbot.geflipper.FlipperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FlipperConfig.class);
    }


    /**
     * Keeps this plugin's own log volume in hand by setting the level of THIS package's logger only.
     *
     * An earlier build detached the root logger's GameChatAppender and switched the whole client's
     * chat configuration off on every startup, and shutdown never put it back. Starting and stopping
     * GE Flipper therefore silenced in-game diagnostics for every other Microbot script. Nothing
     * outside this package is touched here.
     */
    private void applyOwnLogLevel() {
        try {
            org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger("net.runelite.client.plugins.microbot.geflipper");
            if (slf4jLogger instanceof ch.qos.logback.classic.Logger) {
                ((ch.qos.logback.classic.Logger) slf4jLogger).setLevel(verboseLoggingEnabled()
                    ? ch.qos.logback.classic.Level.INFO
                    : ch.qos.logback.classic.Level.WARN);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Whether the user asked for a full trace of this plugin's own activity. Read defensively. */
    private boolean verboseLoggingEnabled() {
        try {
            return config != null && config.verboseLogging();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    protected void startUp() throws AWTException{
        warnIfSlotSwapOff();
        applyOwnLogLevel();
        if (overlay != null) {
            overlay.clearStats();
            if (overlayManager != null) overlayManager.add(overlay);
        }
        flipperScript.run(config);
    }

    /**
     * Flipping Copilot's slot action swap belongs to the user. This plugin reads it and reports
     * when it is off, but never writes it - enabling it silently is what made the user's own
     * left-click setting flip back on.
     */
    private void warnIfSlotSwapOff() {
        if (configManager == null || config == null
            || config.slotAction() != FlipperConfig.SlotAction.COPILOT_LEFT_CLICK) return;
        if ("true".equals(configManager.getConfiguration("flippingcopilot", "slotActionSwap"))) return;
        log.warn("GE Flipper's Copilot left-click swap is On, but Flipping Copilot's slot action swap is off. "
            + "Enable it in Copilot's own settings, or set Copilot left-click swap to Off in GE Flipper. "
            + "This plugin never changes that setting for you.");
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"Flipper Config".equals(event.getGroup())) return;
        if ("slotActionMode".equals(event.getKey())) {
            warnIfSlotSwapOff();
        }
        if ("verboseLogging".equals(event.getKey())) applyOwnLogLevel();
        if ("showOverlay".equals(event.getKey()) && overlay != null && !config.showOverlay()) overlay.clearStats();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            if (overlay != null) overlay.clearStats();
            if (flipperScript != null) flipperScript.clearCachedTradingState();
        }
    }

    @Override
    protected void shutDown() {
        if (overlay != null) {
            overlay.clearStats();
            if (overlayManager != null) overlayManager.remove(overlay);
        }
        flipperScript.state = State.GOING_TO_GE;
        flipperScript.shutdown();
    }
}
