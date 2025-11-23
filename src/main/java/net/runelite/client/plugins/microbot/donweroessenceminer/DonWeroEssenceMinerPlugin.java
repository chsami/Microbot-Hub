package net.runelite.client.plugins.microbot.donweroessenceminer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Don Wero's Essence Miner",
        description = "Mines Rune/Pure Essence at Aubury's Rune Shop. Clean, simple, and reliable.",
        tags = {"mining", "essence", "skilling", "don wero"},
        authors = {"kyle@ked.dev"},
        version = DonWeroEssenceMinerPlugin.version,
        minClientVersion = "1.9.8",
        enabledByDefault = false
)
@Slf4j
public class DonWeroEssenceMinerPlugin extends Plugin {
    static final String version = "1.0.0";

    @Inject
    private DonWeroEssenceMinerConfig config;

    @Provides
    DonWeroEssenceMinerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DonWeroEssenceMinerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DonWeroEssenceMinerOverlay overlay;

    @Inject
    DonWeroEssenceMinerScript script;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        overlay.resetStartTime();
        script.run(config);
    }

    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
