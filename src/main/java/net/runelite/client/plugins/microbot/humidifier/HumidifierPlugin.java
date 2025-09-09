package net.runelite.client.plugins.microbot.humidifier;

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
        name = PluginConstants.NATE +"Humidifier",
        description = "Nate's Humidifier",
        authors = { "Nate" },
        version = HumidifierPlugin.version,
        minClientVersion = "2.0.1",
        tags = {"magic", "nate", "humidifier","moneymaking"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/HumidifierPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/HumidifierPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class HumidifierPlugin extends Plugin {
    public static final String version = "1.6.2";

    @Inject
    private HumidifierConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HumidifierOverlay humidifierOverlay;

    @Inject
    HumidifierScript humidifierScript;

    @Provides
    HumidifierConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HumidifierConfig.class);
    }


    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(humidifierOverlay);
        }

        if (Microbot.getClient() == null)
            shutDown();

        humidifierScript.run(config);
    }

    protected void shutDown() {
        humidifierScript.shutdown();
        overlayManager.remove(humidifierOverlay);
    }
}
