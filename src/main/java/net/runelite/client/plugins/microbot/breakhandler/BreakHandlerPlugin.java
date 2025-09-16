package net.runelite.client.plugins.microbot.breakhandler;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.events.PluginPauseEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "BreakHandler",
        description = "Microbot breakhandler",
        tags = {"break", "microbot", "breakhandler"},
        authors = {"Mocrosoft"},
        version = BreakHandlerPlugin.version,
        minClientVersion = "1.9.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BreakHandlerPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    BreakHandlerScript breakHandlerScript;
    @Inject
    private BreakHandlerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BreakHandlerOverlay breakHandlerOverlay;

    @Provides
    BreakHandlerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BreakHandlerConfig.class);
    }

    private boolean hideOverlay;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(breakHandlerOverlay);
        }
        hideOverlay = config.isHideOverlay();
        toggleOverlay(hideOverlay);
        breakHandlerScript.run(config);
    }

    private void toggleOverlay(boolean hideOverlay) {
        if (overlayManager != null) {
            boolean hasOverlay = overlayManager.anyMatch(ov -> ov.getName().equalsIgnoreCase(BreakHandlerOverlay.class.getSimpleName()));

            if (hideOverlay) {
                if(!hasOverlay) return;

                overlayManager.remove(breakHandlerOverlay);
            } else {
                if (hasOverlay) return;

                overlayManager.add(breakHandlerOverlay);
            }
        }
    }

    protected void shutDown() {        
        breakHandlerScript.shutdown();
        log.debug("\nshutdown: "+
                 "\nbreakDuration: " + BreakHandlerScript.breakDuration + 
                 "\nbreakIn: " + BreakHandlerScript.breakIn + 
                 "\nisLockState: " + BreakHandlerScript.isLockState() + 
                 "\npauseAllScripts: " + Microbot.pauseAllScripts.get() + 
                 "\nPluginPauseEvent.isPaused: " + PluginPauseEvent.isPaused());
        overlayManager.remove(breakHandlerOverlay);

        
    }

    // on settings change
    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {
        if (event.getGroup().equals(BreakHandlerConfig.configGroup)) {
            if (event.getKey().equals("UsePlaySchedule")) {
                breakHandlerScript.reset();
            }
            
            if (event.getKey().equals("breakNow")) {
                boolean breakNowValue = config.breakNow();
                log.debug("Break Now toggled: {}", breakNowValue);
            }

            if (event.getKey().equals("breakEndNow")) {
                boolean breakEndNowValue = config.breakEndNow();
                log.debug("Break End Now toggled: {}", breakEndNowValue);
            }

            if (event.getKey().equals(BreakHandlerConfig.hideOverlay)) {
                hideOverlay = config.isHideOverlay();
                toggleOverlay(hideOverlay);
            }
        }
    }
}