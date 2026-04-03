package net.runelite.client.plugins.microbot.motherloadmine;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = PluginConstants.MOCROSOFT + "MotherlodeMine",
	description = "A bot that mines paydirt in the motherlode mine",
	tags = {"paydirt", "mine", "motherlode", "mlm"},
	authors = { "Mocrosoft" },
	version = MotherloadMinePlugin.version,
	minClientVersion = "1.9.8",
	iconUrl = "https://chsami.github.io/Microbot-Hub/MotherloadMinePlugin/assets/icon.png",
	cardUrl = "https://chsami.github.io/Microbot-Hub/MotherloadMinePlugin/assets/card.png",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
public class MotherloadMinePlugin extends Plugin {

	static final String version = "1.9.4";

    @Inject
    private MotherloadMineConfig config;
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MotherloadMineOverlay motherloadMineOverlay;
    @Inject
    private MotherloadMineScript motherloadMineScript;

	@Getter
	private List<WorldPoint> blacklistedCrates = new ArrayList<>();

    @Provides
	MotherloadMineConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MotherloadMineConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		log.info("Starting MotherloadMine plugin v{}", version);
        overlayManager.add(motherloadMineOverlay);
        motherloadMineScript.run();
		log.info("MotherloadMine startup complete");
    }

    @Override
    public void shutDown() {
		log.info("Starting MotherloadMine shutdown");
        motherloadMineScript.shutdown();
        overlayManager.remove(motherloadMineOverlay);
		blacklistedCrates.clear();
		log.info("MotherloadMine shutdown complete");
    }
}
