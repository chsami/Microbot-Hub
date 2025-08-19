package net.runelite.client.plugins.microbot.gefiremaker;

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
	name = PluginConstants.STICKTOTHESCRIPT + "GE Firemaker",
    authors = {"StickToTheScript"},
	version = GEFiremakerPlugin.version,
	description = "Uses logs on campfires around the Grand Exchange.",
	tags = {"firemaking", "campfire", "grand", "exchange", "ge"},
	cardUrl = "https://chsami.github.io/Microbot-Hub/GEFiremakerPlugin/assets/card.png",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL,
	minClientVersion = "1.9.6"
)

@Slf4j
public class GEFiremakerPlugin extends Plugin {
	static final String version = "1.0.0";
    @Inject
    private GEFiremakerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GEFiremakerOverlay overlay;

    @Inject
    GEFiremakerScript script;

    @Provides
    GEFiremakerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GEFiremakerConfig.class);
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
