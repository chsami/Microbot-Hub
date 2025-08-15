package net.runelite.client.plugins.microbot.barbarianvillagefisher;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.barbarianvillagefisher.enums.BarbarianFishingFunctions;
import net.runelite.client.plugins.microbot.barbarianvillagefisher.enums.BarbarianFishingType;

@ConfigGroup("BarbarianVillageFisher")

@ConfigInformation("This plugin fishes and cooks at the fishing spot in Barbarian Village, with support for banking the raw or cooked food in Edgeville.<br /><br />" +
        "If you have any desired features or bugs, please contact me through Discord (@StickToTheScript).")

public interface BarbarianVillageFisherConfig extends Config {

    @ConfigSection(
            name = "Fishing",
            description = "Fishing Settings",
            position = 1
    )
    String fishingSection = "Fishing";

    @ConfigItem(
            keyName = "fishType",
            name = "Fish Type",
            description = "What type of fishing are we going to do?",
            position = 0,
            section = fishingSection
    )
    default BarbarianFishingType sFishingType()
    {
        return BarbarianFishingType.FLY_FISHING;
    }

    @ConfigItem(
            keyName = "function",
            name = "Function",
            description = "Bank or drop items",
            position = 1,
            section = fishingSection
    )
    default BarbarianFishingFunctions sFunction()
    {
        return BarbarianFishingFunctions.DROP_RAW;
    }
    
    @ConfigItem(
            keyName = "debug",
            name = "Debug",
            description = "Enable debug information",
            position = 2,
            section = fishingSection
    )
    default boolean sDebug()
    {
        return false;
    }
}
