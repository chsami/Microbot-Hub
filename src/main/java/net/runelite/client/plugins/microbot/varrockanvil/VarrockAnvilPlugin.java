package net.runelite.client.plugins.microbot.varrockanvil;

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
	name = PluginConstants.STICKTOTHESCRIPT + "Varrock Anvil",
    authors = {"StickToTheScript"},
	version = VarrockAnvilPlugin.version,
	description = "Smith items at the anvils in Varrock West.",
	tags = {"smithing", "varrock", "anvil"},
	cardUrl = "https://chsami.github.io/Microbot-Hub/VarrockAnvilPlugin/assets/card.png",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL,
	minClientVersion = "1.9.6"
)

@Slf4j
public class VarrockAnvilPlugin extends Plugin {
	static final String version = "1.0.2";
    @Inject
    private VarrockAnvilConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private VarrockAnvilOverlay overlay;

    @Inject
    VarrockAnvilScript script;

    @Provides
    VarrockAnvilConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(VarrockAnvilConfig.class);
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
