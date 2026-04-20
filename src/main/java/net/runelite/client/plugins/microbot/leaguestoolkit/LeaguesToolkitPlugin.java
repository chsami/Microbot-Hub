package net.runelite.client.plugins.microbot.leaguestoolkit;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.DV + "Leagues Toolkit",
        description = "Quality-of-life utilities for Leagues (anti-AFK, and more to come)",
        tags = {"leagues", "microbot", "utility", "afk"},
        version = LeaguesToolkitPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class LeaguesToolkitPlugin extends Plugin {
    public static final String version = "1.2.0";

    @Inject
    private LeaguesToolkitConfig config;

    @Inject
    private LeaguesToolkitScript leaguesToolkitScript;

    @Provides
    LeaguesToolkitConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LeaguesToolkitConfig.class);
    }

    @Override
    protected void startUp() {
        leaguesToolkitScript.run(config);
    }

    @Override
    protected void shutDown() {
        leaguesToolkitScript.shutdown();
    }
}
