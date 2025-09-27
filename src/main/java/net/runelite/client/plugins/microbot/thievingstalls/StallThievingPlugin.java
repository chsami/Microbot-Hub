package net.runelite.client.plugins.microbot.thievingstalls;

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
        name = PluginDescriptor.Basm + "Stall Thieving",
        description = "Stall Thieving",
        tags = {"thieving", "microbot"},
        version = StallThievingPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class StallThievingPlugin extends Plugin {

    public final static String version = "1.0.0";
    @Inject
    private StallThievingConfig config;
    @Provides
    StallThievingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(StallThievingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private StallThievingOverlay overlay;

    @Inject
    StallThievingScript script;


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
