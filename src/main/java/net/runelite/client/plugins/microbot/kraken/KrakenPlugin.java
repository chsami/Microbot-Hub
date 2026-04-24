package net.runelite.client.plugins.microbot.kraken;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.PERT + "Kraken",
        description = "AFK Kraken boss: disturbs whirlpools, auto-fights, loots.",
        tags = {"kraken", "boss", "slayer", "afk"},
        authors = {"Pert"},
        version = KrakenPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KrakenPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private KrakenConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private KrakenOverlay overlay;
    @Inject
    private KrakenScript script;

    @Provides
    KrakenConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KrakenConfig.class);
    }

    @Override
    protected void startUp() {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }
}
