package net.runelite.client.plugins.microbot.tithefarming;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmLanes;

@ConfigGroup(TitheFarmingConfig.GROUP)
@ConfigInformation("<b>-- GUIDE --</b><br/><br/>" +
"• Start at the entrance near the table to get seeds.<br />" +
"• Have these items in your inventory:<br/>" +
"&nbsp;&nbsp;8x Watering Can(8) or the Gricoller's Can.<br/>" +
"&nbsp;&nbsp;Seed dibber.<br />" +
"&nbsp;&nbsp;Spade.<br />" +
"&nbsp;&nbsp;Stamina potions (optional)<br /><br />" +
"• Make sure to have the tithe farm plugin from runelite enabled.<br />" +
"<b>• At least level 34 farming required.</b><br />" +
"• Happy botting!<br />")
public interface TitheFarmingConfig extends Config {

    String GROUP = "Farming";

    @ConfigSection(
            name = "Script Settings",
            description = "General",
            position = 0,
            closedByDefault = false
    )
    String scriptSettings = "Script Settings";

    @ConfigItem(
            keyName = "storing",
            name = "Store fruit threshold",
            description = "Amount of fruits to have in your inventory before storing them in the sack",
            position = 0,
            section = scriptSettings
    )
    default int storeFruitThreshold() {
        return 100;
    }

    @ConfigItem(
            keyName = "Lanes",
            name = "Tithe farm lanes",
            description = "Choose a lane starting from the entrance",
            position = 1,
            section = scriptSettings
    )
    default TitheFarmLanes Lanes() {
        return TitheFarmLanes.LANE_1_2;
    }

    @ConfigItem(
            keyName = "Gricoller's can refill threshold",
            name = "Gricoller's can refill threshold",
            description = "Percentage before refilling the gricoller's can",
            position = 2,
            section = scriptSettings
    )
    default int gricollerCanRefillThreshold() {
        return 30;
    }

    @ConfigItem(
            keyName = "Sleep after planting seed",
            name = "Sleep after planting seed",
            description = "Sleep after planting seed - changing this value might result in unexpected behavior",
            position = 3,
            section = scriptSettings
    )
    default int sleepAfterPlantingSeed() {
        return 2000;
    }

    @ConfigItem(
            keyName = "Sleep after watering seed",
            name = "Sleep after watering seed",
            description = "Sleep after watering seed - changing this value might result in unexpected behavior",
            position = 4,
            section = scriptSettings
    )
    default int sleepAfterWateringSeed() {
        return 2000;
    }

    @ConfigItem(
            keyName = "Sleep after harvesting seed",
            name = "Sleep after harvesting seed",
            description = "Sleep after harvesting seed - changing this value might result in unexpected behavior",
            position = 5,
            section = scriptSettings
    )
    default int sleepAfterHarvestingSeed() {
        return 2000;
    }

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable Antiban Features",
            description = "Will enable features like natural mouse movements and such.",
            position = 6,
            section = scriptSettings
    )
    default boolean enableAntiban() {
        return false;
    }


    @ConfigSection(
            name = "Debug Settings",
            description = "General",
            position = 1,
            closedByDefault = false
    )
    String debugSettings = "Debug Settings";

    @ConfigItem(
            keyName = "Enable Debug",
            name = "Enable Debug",
            description = "Enable debugger",
            position = 0,
            section = debugSettings
    )
    default boolean enableDebugging() {
        return false;
    }

    @ConfigItem(
            keyName = "Enable Overlay",
            name = "Enable Overlay",
            description = "Enable Overlay",
            position = 1,
            section = debugSettings
    )
    default boolean enableOverlay() {
        return false;
    }
}

