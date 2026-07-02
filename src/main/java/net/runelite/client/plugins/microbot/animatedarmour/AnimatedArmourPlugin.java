package net.runelite.client.plugins.microbot.animatedarmour;

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
        name = PluginDescriptor.See1Duck + "Animated Armour Killer & ",
        description = "Builds, Kills, and loots animated armour and warrior guild tokens.",
        tags = {"animated armour", "microbot", "warrior guild"},
        authors = {"See1Duck"},
        version = AnimatedArmourPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AnimatedArmourPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AnimatedArmourPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AnimatedArmourPlugin extends Plugin {

    static final String version = "1.0.2";
    @Inject
    private AnimatedArmourConfig config;

    @Provides
    AnimatedArmourConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AnimatedArmourConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AnimatedArmourOverlay animatedArmourOverlay;

    @Inject
    AnimatedArmourScript animatedArmourScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(animatedArmourOverlay);
        }
        animatedArmourScript.run(config);
    }

    protected void shutDown() {
        animatedArmourScript.shutdown();
        overlayManager.remove(animatedArmourOverlay);
    }

}
