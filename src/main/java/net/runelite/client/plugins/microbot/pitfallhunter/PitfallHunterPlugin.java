package net.runelite.client.plugins.microbot.pitfallhunter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Pitfall Hunter",
        description = "Local Sunlight Antelope pitfall loop. Lure closest NPC, then use closest pit.",
        tags = {"hunter", "pitfall", "sunlight antelope", "local", "mvp"},
        authors = {"Microbot-Hub"},
        version = PitfallHunterPlugin.version,
        minClientVersion = "2.1.0",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class PitfallHunterPlugin extends Plugin
{
    public static final String version = "0.1.12";

    @Inject
    private PitfallHunterConfig config;

    @Inject
    private PitfallHunterScript script;

    @Provides
    PitfallHunterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PitfallHunterConfig.class);
    }

    @Override
    protected void startUp()
    {
        script.run(config);
        log.info("[PitfallHunter] Started");
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        log.info("[PitfallHunter] Stopped");
    }
}
