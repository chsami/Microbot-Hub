package net.runelite.client.plugins.microbot.firemakingplus;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "<html>[<font color=#BB86FC>P</font>] " + "Auto Firemaking Plus",
        description = "Firemaking trainer: add logs to a Forester's Campfire or light a line of fires, with stop conditions, target level, and overlay/pause.",
        tags = {"firemaking", "campfire", "skilling", "microbot", "plus"},
        authors = {"pjmarz"},
        version = AutoFiremakingPlusPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoFiremakingPlusPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoFiremakingPlusPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoFiremakingPlusPlugin extends Plugin {
    public static final String version = "0.2.2";

    @Inject
    private AutoFiremakingPlusConfig config;

    @Provides
    AutoFiremakingPlusConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoFiremakingPlusConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoFiremakingPlusOverlay overlay;

    @Inject
    AutoFiremakingPlusScript script;

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(overlay);
            overlay.pauseButton.hookMouseListener();
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        script.shutdown();
        if (overlay != null) {
            overlay.pauseButton.unhookMouseListener();
        }
        overlayManager.remove(overlay);
    }

    public AutoFiremakingPlusScript getScript() {
        return script;
    }
}
