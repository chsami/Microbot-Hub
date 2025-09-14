package net.runelite.client.plugins.microbot.aiocamdozaal;

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
        name = PluginDescriptor.Cardew + "AIO Camdozaal",
        description = "Cardews AIO Camdozaal plugin",
        tags = {"aio", "microbot", "camdozaal", "cd", "cardew"},
        authors = {"Cardews"},
        version = AIOCamdozPlugin.version,
        minClientVersion = "2.0.7",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL,
        iconUrl = "https://chsami.github.io/Microbot-Hub/AIOCamdozPlugin/assets/icon.jpg",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AIOCamdozPlugin/assets/card.jpg"
)
@Slf4j
public class AIOCamdozPlugin extends Plugin {
    public final static String version = "1.0.0";

    @Inject
    private AIOCamdozConfig config;

    @Provides
    AIOCamdozConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AIOCamdozConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AIOCamdozOverlay aioCamdozOverlay;

    @Inject
    AIOCamdozScript aioCamdozScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(aioCamdozOverlay);
        }
        aioCamdozScript.run(config);
    }

    protected void shutDown() {
        aioCamdozScript.shutdown();
        overlayManager.remove(aioCamdozOverlay);
    }
}
