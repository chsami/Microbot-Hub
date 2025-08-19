package net.runelite.client.plugins.microbot.gefiremaker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginDescriptor.StickToTheScript + "GE Firemaker",
    authors = {"StickToTheScript"},
	version = "1.0.0",
	description = "Uses logs on campfires around the Grand Exchange.",
	tags = {"firemaking", "campfire", "grand", "exchange", "ge", "sticktothescript", "stts"},
	cardUrl = "https://oldschool.runescape.wiki/images/thumb/Grand_Exchange.png/320px-Grand_Exchange.png?7f06f",
	enabledByDefault = false,
	isExternal = true,
	minClientVersion = "1.9.6"
)

@Slf4j
public class GEFiremakerPlugin extends Plugin {
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
