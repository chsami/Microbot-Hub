package net.runelite.client.plugins.microbot.kebbitmeat;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
	name = "<html><font color='red'>[H]</font> Kebbit Meat</html>",
	description = "Deadfall traps wild kebbits, drops all loot except raw wild kebbit, banks at Auburnvale",
	tags = {"hunter", "kebbit", "deadfall", "trap", "microbot", "automation"},
	enabledByDefault = false,
		minClientVersion = "2.0.13",
		isExternal = PluginConstants.IS_EXTERNAL
)
public class KebbitmeatPlugin extends Plugin {

	@Inject
	private KebbitmeatConfig config;

	@Inject
	private KebbitmeatScript script;

	@Inject
	private KebbitmeatOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	private Instant startTime;

	@Provides
	KebbitmeatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(KebbitmeatConfig.class);
	}

	public Instant getStartTime() {
		return startTime;
	}

	@Override
	protected void startUp() throws AWTException {
		startTime = Instant.now();
		overlayManager.add(overlay);
		script.run(config);
	}

	@Override
	protected void shutDown() {
		script.shutdown();
		overlayManager.remove(overlay);
	}
}
