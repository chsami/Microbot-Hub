package net.runelite.client.plugins.microbot.arceuusrc;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.SEE1DUCK + "Arceuus RC",
        description = "Runecrafting at Arceuus",
        authors = { "See1Duck" },
        version = ArceuusRcPlugin.version,
        minClientVersion = "1.9.9.1",
        tags = {"runecrafting", "blood rune", "soul rune" ,"arceuus", "microbot"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/ArceuusRcPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/ArceuusRcPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class ArceuusRcPlugin extends Plugin {

    public static final String version = "1.0.2";

    @Getter
    @Inject
    private ArceuusRcConfig config;

    @Provides
    ArceuusRcConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ArceuusRcConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ArceuusRcOverlay arceuusRcOverlay;
    @Getter
    @Inject
    ArceuusRcScript arceuusRcScript;

    @Override
    protected void startUp() {
        if (overlayManager != null) {
            overlayManager.add(arceuusRcOverlay);
        }
        arceuusRcScript.run(config);
    }

    protected void shutDown() {
        arceuusRcScript.shutdown();
        overlayManager.remove(arceuusRcOverlay);
    }
}
