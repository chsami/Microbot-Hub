package net.runelite.client.plugins.microbot.sisyphusinfernalpact;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

/**
 * Sisyphus: Infernal Pact
 *
 * Standalone plugin that automates Yama's stepping-stone circuit.
 * Enable near the first entry stone and it loops until the configured
 * stone count is reached, never touching fire-laden tiles.
 */
@PluginDescriptor(
        name = "<html>[<font color=#ff4c4c>SIS</font>] " + "Sisyphus: Infernal Pact",
        description = "Automates Yama's stepping-stone circuit — avoids fire, loops the route, spam-clicks safe stones.",
        tags = {"leagues", "sisyphus", "yama", "demonic", "pacts", "infernal", "stepping stones"},
        version = SisyphusInfernalPactPlugin.VERSION,
        minClientVersion = "2.0.13",
        isExternal = PluginConstants.IS_EXTERNAL,
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        authors = {"Sisyphus"}
)
@Slf4j
public class SisyphusInfernalPactPlugin extends Plugin {

    public static final String VERSION = "1.0.0";

    @Inject private SisyphusInfernalPactConfig config;
    @Inject private SisyphusInfernalPactScript  script;

    @Provides
    SisyphusInfernalPactConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SisyphusInfernalPactConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("[Sisyphus: Infernal Pact] Starting v{}", VERSION);
        if (!script.isRunning()) {
            script.run(config);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        script.shutdown();
        log.info("[Sisyphus: Infernal Pact] Stopped.");
    }
}
