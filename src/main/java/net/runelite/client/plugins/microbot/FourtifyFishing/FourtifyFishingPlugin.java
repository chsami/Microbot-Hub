package net.runelite.client.plugins.microbot.FourtifyFishing;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.fourtify.core.client.PluginName;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginName.PREFIX + "Fourtify Fishing",
        description = "Fishes the chosen fish, or the best one the player's level allows, at a safe spot, then drops or banks the catch",
        tags = {"fishing", "fourtify"},
        authors = {"Fourtifys"},
        version = FourtifyFishingPlugin.version,
        minClientVersion = "2.6.42",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FourtifyFishingPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FourtifyFishingPlugin/assets/card.png",
        enabledByDefault = false,
        isExternal = true
)
public class FourtifyFishingPlugin extends Plugin {
    /** The Hub build reads this constant for the jar's file name and the plugin list. */
    static final String version = "1.0.0";
    @Inject
    private FourtifyFishingScript script;

    @Inject
    private FourtifyFishingConfig config;

    @Provides
    FourtifyFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FourtifyFishingConfig.class);
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
