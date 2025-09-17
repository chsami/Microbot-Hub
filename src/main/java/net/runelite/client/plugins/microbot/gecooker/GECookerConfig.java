package net.runelite.client.plugins.microbot.gecooker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.gecooker.enums.CookingItem;
import net.runelite.client.plugins.microbot.gecooker.enums.GEWorkLocation;
import net.runelite.client.plugins.microbot.gecooker.enums.LogType;


@ConfigGroup("GECooker")
@ConfigInformation("This plugin cooks the selected cooking item at the Grand Exchange using regular fires and campfires.<br /><br />" + 
        "If a fire does not already exist, it will create it in the desired location that you select.<br /><br />" +
        "For bugs or feature requests, contact me through Discord (@StickToTheScript).")
public interface GECookerConfig extends Config {

    @ConfigSection(
            name = "Cooking",
            description = "Cooking Settings",
            position = 1
    )
    String cookingSection = "Cooking";

    @ConfigItem(
            keyName = "Log Type",
            name = "Log Type",
            description = "The type of logs to use to make the fire",
            position = 0,
            section = cookingSection
    )
    default LogType sLogType()
    {
        return LogType.NORMAL_LOGS;
    }

    @ConfigItem(
            keyName = "Cook Item",
            name = "Cook Item",
            description = "What are we going to cook?",
            position = 1,
            section = cookingSection
    )
    default CookingItem sCookItem()
    {
        return CookingItem.RAW_SHRIMP;
    }

    @ConfigItem(
            keyName = "location",
            name = "Desired Fire Location",
            description = "The desired location to build a fire if a fire does not exist",
            position = 2,
            section = cookingSection
    )
    default GEWorkLocation sLocation()
    {
        return GEWorkLocation.NORTH_EAST;
    }

    @ConfigItem(
            keyName = "Debug",
            name = "Debug",
            description = "Enable debug information",
            position = 3,
            section = cookingSection
    )
    default boolean sDebug()
    {
        return false;
    }
}
