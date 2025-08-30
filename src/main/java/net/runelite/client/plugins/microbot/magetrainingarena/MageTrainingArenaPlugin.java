package net.runelite.client.plugins.microbot.magetrainingarena;

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
        name = PluginConstants.BASCHE + "Mage Training Arena",
        description = "Basche's Mage Training Arena plugin",
        authors = { "Basche" },
        version = MageTrainingArenaPlugin.version,
        minClientVersion = "1.9.8.8",
        tags = {"basche", "mta", "moneymaking", "magic"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/MageTrainingArenaPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/MageTrainingArenaPlugin/assets/card.png",
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

    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
