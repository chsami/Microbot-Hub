package net.runelite.client.plugins.microbot.butterflycatcher;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

/**
 * =====================================================================
 *  Butterfly Catcher
 *  Author: StonksCode
 * =====================================================================
 *
 *  Automates butterfly and moth catching for Hunter XP training.
 *
 *  Supported species:
 *    - Ruby Harvest       (net lvl 5  / bare lvl 15)
 *    - Sapphire Glacialis (net lvl 25 / bare lvl 35)
 *    - Snowy Knight       (net lvl 35 / bare lvl 45)
 *    - Black Warlock      (net lvl 45 / bare lvl 55)
 *    - Sunlight Moth      (net lvl 65 / bare lvl 75)
 *    - Moonlight Moth     (net lvl 75 / bare lvl 85)
 *
 *  Catch modes:
 *    BAREHANDED    — catch and release for XP; nothing enters inventory.
 *    BUTTERFLY_NET — equip a butterfly net or magic butterfly net before
 *                    starting; allows catching at 10 levels lower than
 *                    the barehanded requirement.
 *
 *  Usage:
 *    1. Stand near a spawn of your chosen species.
 *    2. If using Butterfly Net mode, equip your net first.
 *    3. Select your species and catch mode in the config panel.
 *    4. Start the plugin — it will run indefinitely with no banking.
 * =====================================================================
 */
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

    public static final String version = "1.0.0";

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
