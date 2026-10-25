package net.runelite.client.plugins.microbot.volcanicashminer;

import lombok.Getter;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Volcanic Ash Miner",
        description = "Start either at the ash mine on Fossil Island or with a digsite pendant in your inventory. Have a pickaxe in your inventory or equipped.",
        authors = { "TaF" },
        version = VolcanicAshMinerPlugin.version,
        minClientVersion = "2.6.43",
        tags = {"volcanic", "ash", "mining", "ironman", "taf", "microbot"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/volcanicashminer/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/volcanicashminer/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class VolcanicAshMinerPlugin extends Plugin  {
    public static final String version = "1.2.1";

    @Inject
    private VolcanicAshMinerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private VolcanicAshMinerOverlay volcanicAshMinerOverlay;
    @Getter
    @Inject
    private VolcanicAshMinerScript volcanicAshMinerScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(volcanicAshMinerOverlay);
        }
        volcanicAshMinerScript.run(config);
    }

    @Override
    protected void shutDown() {
        volcanicAshMinerScript.shutdown();
        overlayManager.remove(volcanicAshMinerOverlay);
    }

    @Provides
    VolcanicAshMinerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VolcanicAshMinerConfig.class);
    }
}