package net.runelite.client.plugins.microbot.pitfallhunter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

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
    public static final String version = "0.1.52";

    @Inject
    private PitfallHunterConfig config;

    @Inject
    private PitfallHunterScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PitfallHunterOverlay overlay;

    @Provides
    PitfallHunterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PitfallHunterConfig.class);
    }

    @Override
    protected void startUp()
    {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
        log.info("[PitfallHunter] Started");
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        log.info("[PitfallHunter] Stopped");
    }
}
