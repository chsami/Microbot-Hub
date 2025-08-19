package net.runelite.client.plugins.microbot.gecooker;

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
	name = PluginConstants.STICKTOTHESCRIPT + "GE Cooker",
	authors = {"StickToTheScript"},
	version = GECookerPlugin.version,
	description = "Cooks items on fires found at the Grand Exchange.",
	tags = {"cooking", "grand", "exchange", "ge", "fish", "raw"},
	cardUrl = "https://chsami.github.io/Microbot-Hub/GECookerPlugin/assets/card.png",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL,
	minClientVersion = "1.9.6"
)

@Slf4j
public class GECookerPlugin extends Plugin {
	static final String version = "1.0.0";
    @Inject
    private GECookerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GECookerOverlay overlay;
    @Inject
    GECookerScript script;

    @Provides
    GECookerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GECookerConfig.class);
    }

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
