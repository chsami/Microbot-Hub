package net.runelite.client.plugins.microbot.sailing;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.sailing.features.salvaging.SalvagingHighlight;
import net.runelite.client.plugins.microbot.sailing.features.salvaging.SalvagingScript;
import net.runelite.client.plugins.microbot.sailing.features.trials.TrialsScript;
import net.runelite.client.plugins.microbot.sailing.features.trials.debug.BoatPathOverlay;
import net.runelite.client.plugins.microbot.sailing.features.trials.overlay.TrialRouteOverlay;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginConstants.MOCROSOFT + "Sailing",
	description = "Microbot Sailing Plugin",
	tags = {"sailing"},
	authors = { "Mocrosoft" },
	version = MSailingPlugin.version,
	minClientVersion = "2.1.0",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL,
    cardUrl = "https://chsami.github.io/Microbot-Hub/MSailingPlugin/assets/card.jpg",
    iconUrl = "https://chsami.github.io/Microbot-Hub/MSailingPlugin/assets/icon.jpg"
)
@Slf4j
public class MSailingPlugin extends Plugin {

	static final String version = "2.2.56";

    @Inject
    private SailingConfig config;
    @Provides
    SailingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SailingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SailingOverlay sailingOverlay;
    @Inject
    private SalvagingHighlight salvagingHighlight;

    @Inject
    private SalvagingScript salvagingScript;

    @Inject
    private SailingScript sailingScript;
    @Inject
    private TrialsScript trialsScript;
    @Inject
    private BoatPathOverlay boatPathOverlay;
    @Inject
    private TrialRouteOverlay trialRouteOverlay;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(sailingOverlay);
            overlayManager.add(salvagingHighlight);
            overlayManager.add(boatPathOverlay);
            overlayManager.add(trialRouteOverlay);
        }
        salvagingScript.register();
        trialsScript.register();
        sailingScript.run();
    }

    protected void shutDown() {
        sailingScript.shutdown();
        trialsScript.shutdown();
        salvagingScript.unregister();
        trialsScript.unregister();
        overlayManager.remove(sailingOverlay);
        overlayManager.remove(salvagingHighlight);
        overlayManager.remove(boatPathOverlay);
        overlayManager.remove(trialRouteOverlay);
    }
}
