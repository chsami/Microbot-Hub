package net.runelite.client.plugins.microbot.autogauntletprayer;

import net.runelite.client.config.*;

@ConfigGroup("Auto Gauntlet Prayer")
@ConfigInformation("Auto Gauntlet V 1.1 <br><br> by LiftedMango <br><br>Auto Prayers + Switches Weapon"
)
public interface AutoGauntletPrayerConfig extends Config {

    @ConfigSection(
            name = "General Settings",
            description = "General settings for the script",
            position = 0
    )
    String generalSection = "generalSection";

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
        return true;
    }

    @ConfigItem(
            keyName = "autoAttack",
            name = "Attack after WeaponSwap",
            description = "Script attempts to attack after swapping weapons",
            position = 6,
            section = "generalSection"
    )
    default boolean autoattack() {
        return false;
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
            keyName = "debug",
            name = "Debug",
            description = "turns debugging on",
            position = 10
    )
    default boolean debugtoggle() {
        return true;
    }

}
