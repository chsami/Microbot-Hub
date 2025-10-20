package net.runelite.client.plugins.microbot.construction;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.construction.ConstructionConfig;
import net.runelite.client.plugins.microbot.construction.ConstructionOverlay;
import net.runelite.client.plugins.microbot.construction.ConstructionScript;
import net.runelite.client.plugins.microbot.construction.enums.ConstructionState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Geoff + "Construction 2",
        description = "Geoff's Microbot construction plugin with added new bits.",
        tags = {"skilling", "microbot", "construction"},
        version = ConstructionPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ConstructionPlugin extends Plugin {
    public static final String version = "1.3.1";

    @Inject
    private net.runelite.client.plugins.microbot.construction.ConstructionConfig config;

    @Provides
    net.runelite.client.plugins.microbot.construction.ConstructionConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ConstructionConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ConstructionOverlay ConstructionOverlay;

    private final net.runelite.client.plugins.microbot.construction.ConstructionScript ConstructionScript = new ConstructionScript();

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(ConstructionOverlay);
        }
        ConstructionScript.run(config);
    }

    @Override
    protected void shutDown() {
        ConstructionScript.shutdown();
        overlayManager.remove(ConstructionOverlay);
    }

    public ConstructionState getState() {
        return ConstructionScript.getState();
    }
}
