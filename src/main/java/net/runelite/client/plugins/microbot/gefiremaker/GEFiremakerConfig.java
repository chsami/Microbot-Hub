package net.runelite.client.plugins.microbot.gefiremaker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.gefiremaker.enums.GEWorkLocation;
import net.runelite.client.plugins.microbot.gefiremaker.enums.LogType;


@ConfigGroup("GEFiremaker")
@ConfigInformation("This plugin adds logs to campfires at the Grand Exchange.<br /><br />" + 
        "If the fire does not already exist, it will create it in the desired location that you select." +
        "For bugs or feature requests, contact me through Discord (@StickToTheScript).")
public interface GEFiremakerConfig extends Config {
    @ConfigSection(
            name = "Firemaking",
            description = "Firemaking Settings",
            position = 1
    )
    String firemakingSection = "Firemaking";

    @ConfigItem(
            keyName = "logType",
            name = "Log Type",
            description = "The type of logs to use to make the fire",
            position = 0,
            section = firemakingSection
    )
    default LogType sLogType()
    {
        return LogType.NORMAL_LOGS;
    }

    @ConfigItem(
            keyName = "location",
            name = "Desired Fire Location",
            description = "The desired location to build a fire if a fire does not exist",
            position = 1,
            section = firemakingSection
    )
    default GEWorkLocation sLocation()
    {
        return GEWorkLocation.NORTH_EAST;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug",
            description = "Enable debug information",
            position = 2,
            section = firemakingSection
    )
    default boolean sDebug()
    {
        return false;
    }
}
