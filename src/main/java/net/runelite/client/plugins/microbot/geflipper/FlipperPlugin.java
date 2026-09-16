package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
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
        minClientVersion = "2.1.32",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class FlipperPlugin extends Plugin {
    public static final String version = "1.2.55";
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

    private void ensureCopilotSlotActionSwap() {
        try {
            if (configManager == null) return;
            // This setting has TWO writers: this startup hook and
            // FlipperScript.ensureSlotActionSwapEnabled(). Both must honour the
            // user's toggle, or turning it off has no effect - the startup hook
            // ran first and silently re-enabled the setting.
            FlipperConfig cfg = config;
            if (cfg == null) {
                try {
                    cfg = configManager.getConfig(FlipperConfig.class);
                } catch (Throwable e) {
                    log.info("slotActionSwap auto-enable: could not read the flipper config: {}", e.toString());
                }
            }
            String current = configManager.getConfiguration("flippingcopilot", "slotActionSwap");
            // Logged on every start: this check used to fail silently, so a toggle that
            // did not take effect left no evidence of why.
            log.info("slotActionSwap auto-enable check: toggle={}, current={}",
                cfg == null ? "unreadable" : String.valueOf(cfg.autoEnableSlotSwap()), current);
            if (cfg != null && !cfg.autoEnableSlotSwap()) {
                log.info("slotActionSwap auto-enable is disabled by configuration; leaving it as '{}'.", current);
                return;
            }
            if (!"true".equalsIgnoreCase(current)) {
                log.info("Enabling Flipping Copilot 'slotActionSwap' (was '{}').", current);
                configManager.setConfiguration("flippingcopilot", "slotActionSwap", true);
            }
        } catch (Throwable e) {
            log.info("slotActionSwap auto-enable failed: {}", e.toString());
        }
    }

    private void disableGameChatAppender() {
        try {
            org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            if (slf4jLogger instanceof ch.qos.logback.classic.Logger) {
                ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) slf4jLogger;
                java.util.Iterator<ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent>> it = rootLogger.iteratorForAppenders();
                while (it.hasNext()) {
                    ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender = it.next();
                    if (appender.getClass().getName().contains("GameChatAppender")) {
                        rootLogger.detachAppender(appender);
                        appender.stop();
                    }
                }
            }
            net.runelite.client.plugins.microbot.GameChatAppender.updateConfiguration(false, ch.qos.logback.classic.Level.OFF, false);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void startUp() throws AWTException{
        ensureCopilotSlotActionSwap();
        disableGameChatAppender();
        if (overlayManager != null && overlay != null) {
            overlayManager.add(overlay);
        }
        flipperScript.run(config);
    }

    @Override
    protected void shutDown() {
        if (overlayManager != null && overlay != null) {
            overlayManager.remove(overlay);
        }
        flipperScript.state = State.GOING_TO_GE;
        flipperScript.shutdown();
    }
}