package net.runelite.client.plugins.microbot.leaguesfiremaking;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.DV + "AI Firemaking",
        description = "Lights fires in lines anywhere — withdraws logs, scans for open tiles, adapts around obstacles",
        tags = {"firemaking", "leagues", "microbot", "skilling"},
        version = LeaguesFiremakingPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class LeaguesFiremakingPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private LeaguesFiremakingConfig config;

    @Inject
    private LeaguesFiremakingScript script;

    @Inject
    private LeaguesFiremakingOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Provides
    LeaguesFiremakingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LeaguesFiremakingConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
