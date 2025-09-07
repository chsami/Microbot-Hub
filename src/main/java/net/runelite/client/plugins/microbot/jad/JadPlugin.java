package net.runelite.client.plugins.microbot.jad;

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
        name = PluginConstants.MOCROSOFT + "Jad Helper",
        description = "Auto prays jad attacks and attacks healers",
        tags = {"jad", "microbot"},
        authors = { "Mocrosoft" },
        version = JadPlugin.version,
        minClientVersion = "1.9.8.8",
        cardUrl = "https://chsami.github.io/Microbot-Hub/JadPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/JadPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class JadPlugin extends Plugin {
    static final String version = "1.0.7";

    @Inject
    private JadConfig config;
    @Provides
    JadConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(JadConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private JadOverlay jadOverlay;

    @Inject
    JadScript jadScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(jadOverlay);
        }
        jadScript.run(config);
    }

    protected void shutDown() {
        jadScript.shutdown();
        overlayManager.remove(jadOverlay);
    }
}
