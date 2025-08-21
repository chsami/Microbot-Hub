package net.runelite.client.plugins.microbot.autoboltenchanter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
    name = PluginConstants.BGA + "Auto Bolt Enchanter",
    description = "Automatically enchants crossbow bolts at the bank",
    tags = {"magic"},
    authors = {"bga"},
    version = AutoBoltEnchanterPlugin.version,
    minClientVersion = "1.9.8",
    iconUrl = "https://chsami.github.io/Microbot-Hub/AutoBoltEnchanterPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/AutoBoltEnchanterPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoBoltEnchanterPlugin extends Plugin {
    static final String version = "1.0.0";
    
    @Inject
    private AutoBoltEnchanterConfig config;
    
    @Inject
    private AutoBoltEnchanterScript script;

    @Provides
    AutoBoltEnchanterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoBoltEnchanterConfig.class); // give the config to dependency injection system
    }

    @Override
    protected void startUp() throws AWTException {
        script.run(config); // start running the main script with our config
    }

    @Override
    protected void shutDown() {
        script.shutdown(); // tell the script to stop running
    }
}