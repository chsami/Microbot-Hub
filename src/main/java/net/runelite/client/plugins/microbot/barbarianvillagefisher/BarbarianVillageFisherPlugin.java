package net.runelite.client.plugins.microbot.barbarianvillagefisher;

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
	name = PluginConstants.STICKTOTHESCRIPT + "Barb Village Fisher",
    authors = {"StickToTheScript"},
	version = BarbarianVillageFisherPlugin.version,
	description = "Fly and bait fish in Barbarian Village.",
	tags = {"fish", "fishing", "fly", "bait", "barb", "barbarian", "village"},
	cardUrl = "https://chsami.github.io/Microbot-Hub/BarbarianVillageFisherPlugin/assets/card.png",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL,
	minClientVersion = "1.9.6"
)

@Slf4j
public class BarbarianVillageFisherPlugin extends Plugin {
	static final String version = "1.0.0";
    @Inject
    private BarbarianVillageFisherConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BarbarianVillageFisherOverlay overlay;

    @Provides
    BarbarianVillageFisherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BarbarianVillageFisherConfig.class);
    }

    @Inject
    BarbarianVillageFisherScript script;


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
