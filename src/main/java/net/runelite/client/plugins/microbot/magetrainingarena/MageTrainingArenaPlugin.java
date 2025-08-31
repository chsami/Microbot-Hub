package net.runelite.client.plugins.microbot.magetrainingarena;

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
        name = PluginConstants.BASCHE + "MTA",
        authors = { "Basche" },
        version = MageTrainingArenaPlugin.version,
        minClientVersion = "1.9.6",
        description = "Automates the Mage Training Arena minigame",
        tags = {"mage", "training", "arena", "mta", "magic", "minigame", "microbot"},
        cardUrl = "https://chsami.github.io/Microbot-Hub/MageTrainingArenaPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/MageTrainingArenaPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class MageTrainingArenaPlugin extends Plugin {
    public static final String version = "1.1.4";

    @Inject
    private MageTrainingArenaConfig config;
    
    @Provides
    MageTrainingArenaConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MageTrainingArenaConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MageTrainingArenaOverlay overlay;

    @Inject
    MageTrainingArenaScript script;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }
}
