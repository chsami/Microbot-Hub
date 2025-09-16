package net.runelite.client.plugins.microbot.eelfishing;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.eelfishing.EelFishingConfig;
import net.runelite.client.plugins.microbot.eelfishing.EelFishingOverlay;
import net.runelite.client.plugins.microbot.eelfishing.EelFishingScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Eel Fishing",
        description = "Automates fishing for Infernal and Sacred eels",
        tags = {"fishing", "eel", "microbot"},
        authors = {"See1Duck"},
        version = EelFishingPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/EelFishingPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/EelFishingPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class EelFishingPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    private EelFishingConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private EelFishingScript eelFishingScript;

    @Inject
    private EelFishingOverlay eelFishingOverlay;

    @Provides
    EelFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EelFishingConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("Eel Fishing Plugin started!");
        overlayManager.add(eelFishingOverlay);
        eelFishingScript.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Eel Fishing Plugin stopped!");
        eelFishingScript.shutdown();
        overlayManager.remove(eelFishingOverlay);
    }
}
