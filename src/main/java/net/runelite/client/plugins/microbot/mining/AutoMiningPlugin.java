package net.runelite.client.plugins.microbot.mining;

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
        name = PluginDescriptor.Mocrosoft + "Auto Mining",
        description = "Mines and banks ores",
        tags = {"mining", "microbot", "skilling"},
        version = AutoMiningPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoMiningPlugin extends Plugin {
    public static final String version = "1.0.8";
    @Inject
    private AutoMiningConfig config;
    @Provides
    AutoMiningConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoMiningConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoMiningOverlay autoMiningOverlay;

    @Inject
    AutoMiningScript autoMiningScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(autoMiningOverlay);
        }
        autoMiningScript.run(config);
    }

    protected void shutDown() {
        autoMiningScript.shutdown();
        overlayManager.remove(autoMiningOverlay);
    }
}
