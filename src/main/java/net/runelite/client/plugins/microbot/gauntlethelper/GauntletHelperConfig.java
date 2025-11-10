package net.runelite.client.plugins.microbot.gauntlethelper;

import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.*;

@ConfigGroup("GauntletHelper")
@ConfigInformation("Gauntlet Helper V 1.0 <br><br> by Jam, Inspired by LifedMango <br><br>Switches Prayers & Weapons <br><br> Auto Eats + Drinks <br><br> Work in Progress "
)

public interface GauntletHelperConfig extends Config {

    @ConfigItem(
            keyName = "enOverlay",
            name = "Enable Overlay",
            description = "",
            position = 0
    )
    default boolean enOverlay() {return false;}

    @ConfigSection(
            name = "General Settings",
            description = "General settings for the script",
            position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
            name = "Food/Drink Settings",
            description = "General settings for the script",
            position = 1
    )
    String foodSection = "foodSection";

    @ConfigSection(
            name = "Feature Options",
            description = "General settings for the script",
            position = 2
    )
    String featureSection = "featureSection";

    @ConfigSection(
            name = "Debug Section",
            description = "Debugging Section",
            position = 3
    )
    String debugSection = "debugSection";



    @ConfigItem(
            keyName = "TitanPrayers",
            name = "Use Deadeye & Mystic Vigour",
            description = "Will use deadeye and mystic vigour",
            position = 0,
            section = "generalSection"
    )
    default boolean TitansPrayers() {return false;}

    @ConfigItem(
            keyName = "HigherPrayers",
            name = "Use Augory & Rigour",
            description = "Turn on to use augory and Rigour",
            position = 1,
            section = "generalSection"
    )
    default boolean HigherPrayers() {return false;}


    @ConfigItem(
            keyName = "ppotvalue",
            name = "Drink Potion at:",
            description = "Below this prayer amount, potion will auto drink",
            position = 0,
            section = "foodSection"

    )
    @Range(
            min = 1,
            max = 75
    )
    default int ppotvalue(){return 45;}

    @ConfigItem(
            keyName = "eatFood",
            name = "Eat at low HP value:",
            description = "Script will always eat at this value, for emergencies",
            position = 1,
            section = "foodSection"
    )
    @Range(
            min = 20,
            max = 79
    )
    default int lowhpeatvalue(){return 39;}

    @ConfigItem(
            keyName = "overheal",
            name = "Allow Overhealing by:",
            description = "Allow overhealing by this value",
            position = 2,
            section = "foodSection"
    )
    @Range(
            min = 0,
            max = 5
    )
    default int eatoverhealvalue() {return 3;}

    @ConfigItem(
            keyName = "eatFoodchain",
            name = "Always chain eat till healthy",
            description = "Once eating has started, always continue eating till healthy. Will not overeat and waste food.",
            position = 3,
            section = "foodSection"
    )
    default boolean eatFoodChain() {
        return true;
    }


    @ConfigItem(
            keyName = "eatMoving",
            name = "Eat if Moving",
            description = "Eat food autoamtically if Tornados are active and player is moving, wont interupt movement",
            position = 4,
            section = "foodSection"
    )
    default boolean eatFoodMoving() {
        return true;
    }


    @ConfigItem(
            keyName = "Tornadocheck",
            name = "  -> Only if Tornados Active",
            description = "Eat food automatically if Tornados are active and player is moving",
            position = 5,
            section = "foodSection"
    )
    default boolean TornadoCheck() {
        return true;
    }



    @ConfigItem(
            keyName = "enablefood",
            name = "Enable Auto Eat",
            description = "Below this prayer amount, potion will auto drink, this is a safe action so there should be no reason to turn off",
            position = 0,
            section = "featureSection"

    )
    default boolean enablefood() {
        return true;
    }

    @ConfigItem(
            keyName = "enabledrink",
            name = "Enable Auto Drink",
            description = "Below this prayer amount, potion will auto drink",
            position = 1,
            section = "featureSection"

    )
    default boolean enabledrink() {
        return true;
    }

    @ConfigItem(
            keyName = "autoAttack",
            name = "Attack after WeaponSwap",
            description = "Script attempts to attack after swapping weapons",
            position = 2,
            section = "featureSection"
    )
    default boolean autoattack() {
        return true;
    }



    @ConfigItem(
            keyName = "startState",
            name = "Starting State",
            description = "The starting state of the bot. This is only used if override state is enabled.",
            position = 0,
            section = debugSection
    )
    default State startstate() {return State.fighting;}

    @ConfigItem(
            keyName = "debug",
            name = "Verbose logs",
            description = "turns verbose logging on",
            position = 1,
            section = debugSection
    )
    default boolean verboselog() {
        return true;
    }
}
