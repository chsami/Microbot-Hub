package net.runelite.client.plugins.microbot.fishingtrawler;

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
        name = "Fishing Trawler",
        description = "Chops Tentacles in fishing trawler minigame. start the script either on the dock outside the minigame or on the upstairs ship level where the tentacles spawn. make sure you have an axe on you",
        tags = {"fishing", "fishing trawler", "microbot"},
        authors = {"Unknown"},
        version = FishingTrawlerPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FishingTrawlerPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FishingTrawlerPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class FishingTrawlerPlugin extends Plugin {
    public static final String version = "1.0.1";
    @Inject
    private FishingTrawlerConfig config;
    @Provides
    FishingTrawlerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FishingTrawlerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FishingTrawlerOverlay fishingTrawlerOverlay;

    @Inject
    FishingTrawlerScript fishingTrawlerScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(fishingTrawlerOverlay);
        }
        fishingTrawlerScript.run(config);
    }

    protected void shutDown() {
        fishingTrawlerScript.shutdown();
        overlayManager.remove(fishingTrawlerOverlay);
    }
  

}
