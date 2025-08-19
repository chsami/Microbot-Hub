package net.runelite.client.plugins.microbot.barbarianvillagefisher;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginDescriptor.StickToTheScript + "Barb Village Fisher",
    authors = {"StickToTheScript"},
	version = "1.0.0",
	description = "Fly and bait fish in Barbarian Village.",
	tags = {"fish", "fishing", "fly", "bait", "barb", "barbarian", "village", "sticktothescript", "stts"},
	cardUrl = "https://oldschool.runescape.wiki/images/thumb/Barbarian_Village_%282007%29.png/591px-Barbarian_Village_%282007%29.png?27aac",
	enabledByDefault = false,
	isExternal = true,
	minClientVersion = "1.9.6"
)

@Slf4j
public class BarbarianVillageFisherPlugin extends Plugin {
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
