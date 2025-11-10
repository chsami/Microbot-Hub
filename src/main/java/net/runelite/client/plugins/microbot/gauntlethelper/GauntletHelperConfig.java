package net.runelite.client.plugins.microbot.gauntlethelper;

import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.*;

@ConfigGroup("GauntletHelper")
@ConfigInformation("Gauntlet Helper V 1.0 <br><br> by Jam, Inspired by LifedMango <br><br>Switches Prayers & Weapons <br><br> Auto Eats + Drinks <br><br> Work in Progress "
)

public interface GauntletHelperConfig extends Config {

    @ConfigSection(
            name = "General Settings",
            description = "General settings for the script",
            position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
            name = "Debug Section",
            description = "Debugging Section",
            position = 1
    )
    String debugSection = "debugSection";

    @ConfigItem(
            keyName = "TitanPrayers",
            name = "Use Deadeye & Mystic Vigour",
            description = "Will use deadeye and mystic vigour",
            position = 0,
            section = "generalSection"
    )
    default boolean TitansPrayers() {
        return false;
    }

    @ConfigItem(
            keyName = "HigherPrayers",
            name = "Use Augory & Rigour",
            description = "Turn on to use augory and Rigour",
            position = 1,
            section = "generalSection"
    )
    default boolean HigherPrayers() {
        return false;
    }


    @ConfigItem(
            keyName = "ppotvalue",
            name = "Drink Potion at:",
            description = "Below this prayer amount, potion will auto drink",
            position = 2,
            section = "generalSection"

    )
    default int ppotvalue() {
        return 45;
    }

    @ConfigItem(
            keyName = "eatFood",
            name = "Eat (Always) at HP:",
            description = "Script will always eat at this value, for emergencies",
            position = 3,
            section = "generalSection"
    )
    default int emergencyeatvalue() {
        return 25;
    }

    @ConfigItem(
            keyName = "eatFood",
            name = "Eat if Moving",
            description = "Eat food autoamtically if Tornados are active and player is moving, wont interupt movement",
            position = 4,
            section = "generalSection"
    )
    default boolean eatFood() {
        return true;
    }

    @ConfigItem(
            keyName = "eatFood",
            name = "->Only if Tornados Active",
            description = "Eat food automatically if Tornados are active and player is moving",
            position = 5,
            section = "generalSection"
    )
    default boolean TornadoCheck() {
        return false;
    }

    @ConfigItem(
            keyName = "autoAttack",
            name = "Attack after WeaponSwap",
            description = "Script attempts to attack after swapping weapons",
            position = 6,
            section = "generalSection"
    )
    default boolean autoattack() {
        return true;
    }

    @ConfigItem(
            keyName = "enOverlay",
            name = "Enable Overlay",
            description = "",
            position = 9
    )
    default boolean enOverlay() {
        return false;
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
