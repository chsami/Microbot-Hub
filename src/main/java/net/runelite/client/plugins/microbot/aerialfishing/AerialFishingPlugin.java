package net.runelite.client.plugins.microbot.aerialfishing;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Aerial Fisher",
        description = "Aerial Fishing plugin",
        tags = {"Fishing", "microbot", "Aerial", "skilling"},
        authors = {"See1Duck"},
        version = AerialFishingPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AerialFishingPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AerialFishingPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class AerialFishingPlugin extends Plugin {
    public static final String version = "1.1.1";
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AerialFishingOverlay fishingOverlay;
    @Inject
    private AerialFishingScript fishingScript;
    @Inject
    private AerialFishingConfig config;

    @Provides
    AerialFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AerialFishingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(fishingOverlay);
        }
        fishingScript.run(config);
    }


    protected void shutDown() {
        fishingScript.shutdown();
        overlayManager.remove(fishingOverlay);
    }
}
