package net.runelite.client.plugins.microbot.barbarianfishing;

import com.google.inject.Provides;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.barbarianfishing.BarbarianFishingConfig;
import net.runelite.client.plugins.microbot.barbarianfishing.BarbarianFishingOverlay;
import net.runelite.client.plugins.microbot.barbarianfishing.BarbarianFishingScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Barbarian Fisher",
        description = "Barbarian Fishing plugin",
        tags = {"Fishing", "barbarian", "skilling"},
        authors = {"See1Duck"},
        version = BarbarianFishingPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/BarbarianFishingPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/BarbarianFishingPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class BarbarianFishingPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    BarbarianFishingScript fishingScript;
    @Inject
    private BarbarianFishingConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BarbarianFishingOverlay fishingOverlay;

    @Provides
    BarbarianFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BarbarianFishingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(fishingOverlay);
        }
        fishingScript.run(config);
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        fishingScript.onGameTick();
    }

    protected void shutDown() {
        fishingScript.shutdown();
        overlayManager.remove(fishingOverlay);
    }
}
