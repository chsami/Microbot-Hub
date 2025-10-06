package net.runelite.client.plugins.microbot.housethieving;

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
        name = PluginDescriptor.Maxxin + "House Thieving",
        description = "House Thieving",
        tags = {"thieving", "house thieving"},
        authors = {"Maxxin"},
        version = HouseThievingPlugin.version,
        minClientVersion = "2.0.7",
        iconUrl = "https://chsami.github.io/Microbot-Hub/HouseThievingPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/HouseThievingPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class HouseThievingPlugin extends Plugin {
    public final static String version = "1.0.1";
    @Inject
    private HouseThievingConfig config;

    @Provides
    HouseThievingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HouseThievingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HouseThievingOverlay houseThievingOverlay;

    private HouseThievingScript houseThievingScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(houseThievingOverlay);
        }

        houseThievingScript = new HouseThievingScript(this);
        houseThievingScript.run(config);
    }

    protected void shutDown() {
        houseThievingScript.shutdown();
        overlayManager.remove(houseThievingOverlay);
    }
}
