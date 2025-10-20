package net.runelite.client.plugins.microbot.goldrush;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Gabulhas + "Gold Rush",
        description = "",
        tags = {"GabulhasGoldRush", "Gabulhas"},
        version = GabulhasGoldRushPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class GabulhasGoldRushPlugin extends Plugin {
    public static final String version = "1.0.7";
    @Inject
    private GabulhasGoldRushConfig config;
    @Provides
    GabulhasGoldRushConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GabulhasGoldRushConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PluginManager pluginManager;
    @Inject
    private GabulhasGoldRushOverlay gabulhasGoldRushOverlay;

    @Inject
    GabulhasGoldRushScript gabulhasGoldRushScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(gabulhasGoldRushOverlay);
        }
        gabulhasGoldRushScript.run(config);
//        botStatus = config.STEP(); for debugging
        GabulhasGoldRushInfo.botStatus = config.STARTINGSTATE();
    }

    protected void shutDown() {
        gabulhasGoldRushScript.shutdown();
        overlayManager.remove(gabulhasGoldRushOverlay);
    }
}
