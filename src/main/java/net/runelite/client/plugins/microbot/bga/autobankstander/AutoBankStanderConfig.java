package net.runelite.client.plugins.microbot.autobankstander;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("AutoBankStander")
@ConfigInformation(
    "AIO Bank Standing plugin for various processing activities.<br>" +
    "Click 'Open Configuration' below to set up your processing method and options."
)
public interface AutoBankStanderConfig extends Config {
    
    @ConfigItem(
        keyName = "openConfiguration",
        name = "Open Configuration",
        description = "Click to open the detailed configuration window where you can select skills, methods, and specific options"
    )
    default boolean openConfiguration() {
        return false;
    }
    
    // Hidden storage for configuration data
    @ConfigItem(
        keyName = "configurationData",
        name = "",
        description = "",
        hidden = true
    )
    default String configurationData() {
        return ""; // JSON string containing all configuration
    }
    
    // Hidden item to track if configuration has been set up
    @ConfigItem(
        keyName = "isConfigured", 
        name = "",
        description = "",
        hidden = true
    )
    default boolean isConfigured() {
        return false;
    }
}