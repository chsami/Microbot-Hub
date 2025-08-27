package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

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
        name = PluginConstants.Cardew + " Creature Creation",
        description = "Automates the creature creation process within the Tower of Life basement.",
        tags = {"Tower of life", "creature", "creature creation", "creation", "tol", "cc", "cd", "cardew"},
        authors = "Cardew",
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/TowerOfLifeCCPlugin/assets/card.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/TowerOfLifeCCPlugin/assets/card.png",
        version = TowerOfLifeCCPlugin.version,
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TowerOfLifeCCPlugin extends Plugin {
    @Inject
    private TowerOfLifeCCConfig config;
    @Provides
    TowerOfLifeCCConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TowerOfLifeCCConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TowerOfLifeCCOverlay towerOfLifeCCOverlayOverlay;

    @Inject
    TowerOfLifeCCScript towerOfLifeCCScript;
    static final String version = "1.1.1";

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(towerOfLifeCCOverlayOverlay);
        }
        towerOfLifeCCScript.run(config);
    }

    protected void shutDown() {
        towerOfLifeCCScript.shutdown();
        overlayManager.remove(towerOfLifeCCOverlayOverlay);
    }
}
