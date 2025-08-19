package net.runelite.client.plugins.microbot.varrockanvil;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginDescriptor.StickToTheScript + "Varrock Anvil",
    authors = {"StickToTheScript"},
	version = "1.0.2",
	description = "Smith items at the anvils in Varrock West.",
	tags = {"smithing", "varrock", "anvil", "sticktothescript", "stts"},
	cardUrl = "https://oldschool.runescape.wiki/images/thumb/Varrock_West_bank.png/320px-Varrock_West_bank.png?18540",
	enabledByDefault = false,
	isExternal = true,
	minClientVersion = "1.9.6"
)

@Slf4j
public class VarrockAnvilPlugin extends Plugin {
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
