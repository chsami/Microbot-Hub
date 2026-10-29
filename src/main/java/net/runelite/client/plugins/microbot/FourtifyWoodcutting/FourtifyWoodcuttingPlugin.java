package net.runelite.client.plugins.microbot.FourtifyWoodcutting;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.fourtify.core.client.PluginName;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginName.PREFIX + "Fourtify Woodcutting",
        description = "Chops the chosen tree at the nearest or a random safe spot, then drops or banks the logs",
        tags = {"woodcutting", "fourtify"},
        authors = {"Fourtifys"},
        version = FourtifyWoodcuttingPlugin.version,
        minClientVersion = "2.6.42",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FourtifyWoodcuttingPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FourtifyWoodcuttingPlugin/assets/card.png",
        enabledByDefault = false,
        isExternal = true
)
public class FourtifyWoodcuttingPlugin extends Plugin {
    /** The Hub build reads this constant for the jar's file name and the plugin list. */
    static final String version = "1.0.0";
    @Inject
    private FourtifyWoodcuttingScript script;

    @Inject
    private FourtifyWoodcuttingConfig config;

    @Provides
    FourtifyWoodcuttingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FourtifyWoodcuttingConfig.class);
    }

    @Override
    protected void startUp() {
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }
}
