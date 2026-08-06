package net.runelite.client.plugins.microbot.colosseumprayer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Fortis Colosseum Prayer",
        description = "Prayer helper for Fortis Colosseum waves 1–11: manticore, javelin colossus, serpent shaman, jaguar warrior, shockwave colossus.",
        tags = {"colosseum", "fortis", "prayer", "microbot"},
        authors = {"Microbot Hub"},
        version = ColosseumPrayerPlugin.version,
        minClientVersion = "1.9.8.8",
        cardUrl = "https://chsami.github.io/Microbot-Hub/ColosseumPrayerPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/ColosseumPrayerPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ColosseumPrayerPlugin extends Plugin {

    static final String version = "1.0.3";

    @Provides
    ColosseumPrayerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ColosseumPrayerConfig.class);
    }

    @Inject
    private ColosseumPrayerScript colosseumPrayerScript;

    @Override
    protected void startUp() throws AWTException {
        colosseumPrayerScript.register();
    }

    @Override
    protected void shutDown() {
        colosseumPrayerScript.unregister();
    }
}
