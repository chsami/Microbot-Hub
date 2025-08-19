package net.runelite.client.plugins.microbot.autoessencemining;

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
        name = PluginConstants.BGA + "Auto Essence Mining",
        description = "Mines Rune/Pure Essence...",
        tags = {"mining", "essence", "skilling"},
        author = "bga",
        version = AutoEssenceMiningPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoEssenceMiningPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoEssenceMiningPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoEssenceMiningPlugin extends Plugin {
    static final String version = "1.0.0";
    @Inject
    private AutoEssenceMiningConfig config;
    
    @Provides
    AutoEssenceMiningConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoEssenceMiningConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    
    @Inject
    private AutoEssenceMiningOverlay autoEssenceMiningOverlay;

    @Inject
    AutoAutoEssenceMiningScript autoEssenceMiningScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(autoEssenceMiningOverlay);
        }
        autoEssenceMiningOverlay.resetStartTime();
        autoEssenceMiningScript.run(config);
    }

    protected void shutDown() {
        autoEssenceMiningScript.shutdown();
        overlayManager.remove(autoEssenceMiningOverlay);
    }
}