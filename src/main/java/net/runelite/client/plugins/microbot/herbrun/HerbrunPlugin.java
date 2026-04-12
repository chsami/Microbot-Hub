package net.runelite.client.plugins.microbot.herbrun;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Herb runner",
        description = "Herb runner",
        tags = {"herb", "farming", "money making", "skilling"},
        authors = {"Mocrosoft"},
        version = HerbrunPlugin.version,
        minClientVersion = "2.1.0",
        iconUrl = "https://chsami.github.io/Microbot-Hub/HerbrunPlugin/assets/icon.jpg",
        cardUrl = "https://chsami.github.io/Microbot-Hub/HerbrunPlugin/assets/card.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j

public class HerbrunPlugin extends Plugin {
    public static final String version = "1.1.1";
    @Inject
    private HerbrunConfig config;

    @Provides
    HerbrunConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HerbrunConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HerbrunOverlay HerbrunOverlay;

    @Inject
    HerbrunScript herbrunScript;

    static String status;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(HerbrunOverlay);
        }
        herbrunScript.run();
    }

    protected void shutDown() {
        herbrunScript.shutdown();
        overlayManager.remove(HerbrunOverlay);
        status = null; // Reset status on shutdown
    }
}
