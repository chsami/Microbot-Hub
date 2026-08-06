package net.runelite.client.plugins.microbot.butterflycatcher;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.STKS + "Butterfly Catcher",
        description = "Automates butterfly and moth catching for Hunter XP. Stand near a spawn, pick your species and mode, start the plugin.",
        tags = {"hunter", "butterfly", "moth", "sunlight", "moonlight", "net", "microbot", "stonkscode"},
        authors = {"StonksCode"},
        version = ButterflyCatcherPlugin.version,
        minClientVersion = "2.0.8",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ButterflyCatcherPlugin extends Plugin {

    // v1.0.1 — fix: corrected NPC IDs for Ruby Harvest (5556), Sapphire Glacialis (5555),
    //               and Snowy Knight (5554). Original IDs (5525/5526/5527) were wrong,
    //               causing those three species to never find targets and do nothing.
    public static final String version = "1.0.1";

    @Inject
    private ButterflyCatcherConfig config;

    @Inject
    private ButterflyCatcherScript script;

    @Provides
    ButterflyCatcherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ButterflyCatcherConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        script.run(config);
        log.info("[ButterflyCatcher] Started — target: {}, mode: {}",
                config.butterflyType().getDisplayName(), config.catchMode());
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        log.info("[ButterflyCatcher] Stopped.");
    }
}
