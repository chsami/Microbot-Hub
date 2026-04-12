package net.runelite.client.plugins.microbot.GiantSeaweedFarmer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Giant Seaweed",
        description = "Farms giant seaweed.",
        tags = {"GiantSeaweedFarmer", "seaweed", "farming", "ironman", "taf", "microbot"},
        version = GiantSeaweedFarmerPlugin.version,
        minClientVersion = "2.1.0",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class GiantSeaweedFarmerPlugin extends Plugin {
    public final static String version = "1.2.1";
    private Instant scriptStartTime;
    @Inject
    private GiantSeaweedFarmerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GiantSeaweedFarmerOverlay giantSeaweedFarmerOverlay;
    @Inject
    private GiantSeaweedFarmerScript giantSeaweedFarmerScript;
    @Inject
    private GiantSeaweedSporeScript giantSeaweedSporeScript;

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        giantSeaweedFarmerScript.GSF_Running = true;
        if (overlayManager != null) {
            overlayManager.add(giantSeaweedFarmerOverlay);
        }
        giantSeaweedFarmerScript.run(config);
        // Start the spore looting script if configured
        if (config.lootSeaweedSpores()) {
            giantSeaweedSporeScript.run(config);
        }
    }

    @Override
    protected void shutDown() {
        if (giantSeaweedFarmerScript != null && giantSeaweedFarmerScript.isRunning()) {
            giantSeaweedFarmerScript.shutdown();
        }
        if (giantSeaweedSporeScript != null && giantSeaweedSporeScript.isRunning()) {
            giantSeaweedSporeScript.shutdown();
        }
        overlayManager.remove(giantSeaweedFarmerOverlay);
    }

    @Provides
    GiantSeaweedFarmerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GiantSeaweedFarmerConfig.class);
    }
}
