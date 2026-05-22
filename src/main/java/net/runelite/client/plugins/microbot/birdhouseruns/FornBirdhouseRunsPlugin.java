package net.runelite.client.plugins.microbot.birdhouseruns;

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
        name = PluginDescriptor.Forn + "Birdhouse Runner",
        description = "Does a birdhouse run",
        tags = {"FornBirdhouseRuns", "forn"},
        authors = {"Forn"},
        version = FornBirdhouseRunsPlugin.version,
        minClientVersion = "2.1.0",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL,
        iconUrl = "https://chsami.github.io/Microbot-Hub/FornBirdhouseRunsPlugin/assets/icon.jpg",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FornBirdhouseRunsPlugin/assets/card.jpg"
)
@Slf4j
public class FornBirdhouseRunsPlugin extends Plugin {
    final static String version = "1.1.2";
    @Provides
    FornBirdhouseRunsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FornBirdhouseRunsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FornBirdhouseRunsOverlay fornBirdhouseRunsOverlay;
    @Inject
    FornBirdhouseRunsScript fornBirdhouseRunsScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(fornBirdhouseRunsOverlay);
        }
        fornBirdhouseRunsScript.run();
    }

    protected void shutDown() {
        fornBirdhouseRunsScript.shutdown();
        overlayManager.remove(fornBirdhouseRunsOverlay);
    }
}
