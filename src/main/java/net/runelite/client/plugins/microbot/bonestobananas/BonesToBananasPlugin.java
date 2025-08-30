package net.runelite.client.plugins.microbot.bonestobananas;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.Lumusi + "Bones to Bananas",
        description = "Casts Bones to Bananas, using staves to save runes.",
        tags = {"magic", "skilling", "bones", "bananas", "microbot"},
        authors = { "Lumusi" },
        version = BonesToBananasPlugin.version,
        minClientVersion = "1.9.8",
        cardUrl = "https://chsami.github.io/Microbot-Hub/BonesToBananasPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/BonesToBananasPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BonesToBananasPlugin extends Plugin {
    public static final String version = "1.0.1";

    @Inject
    private BonesToBananasConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BonesToBananasOverlay overlay;
    @Inject
    private BonesToBananasScript script;

    @Provides
    BonesToBananasConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BonesToBananasConfig.class);
    }
    @Override
    protected void startUp() throws Exception {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }
    @Override
    protected void shutDown() throws Exception {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}