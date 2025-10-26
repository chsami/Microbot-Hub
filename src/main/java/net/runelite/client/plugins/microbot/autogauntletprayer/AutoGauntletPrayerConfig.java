package net.runelite.client.plugins.microbot.autogauntletprayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Auto Gauntlet Prayer")
@ConfigInformation("LiftedMango <br> 1.1 <br><br>Auto Prayers + Switches Weapon"
)
public interface AutoGauntletPrayerConfig extends Config {

    @ConfigItem(
            keyName = "HigherPrayers",
            name = "Use Augory & Rigour",
            description = "Turn on to use augory and Rigour",
            position = 0
    )
    default boolean HigherPrayers() {
        return false;
    }

    @ConfigItem(
            keyName = "HigherPrayers",
            name = "Use Deadeye and Mystic Vigour",
            description = "Will use deadeye and mystic vigour",
            position = 1
    )
    default boolean TitansPrayers() {
        return false;
    }


    @ConfigItem(
            keyName = "enOverlay",
            name = "Enable Overlay",
            description = "",
            position = 4
    )
    default boolean enOverlay() {
        return false;
    }

    @ConfigItem(
            keyName = "ppotvalue",
            name = "Drink Potion at:",
            description = "Below this prayer amount, potion will auto drink",
            position = 5
    )
    default int ppotvalue() {
        return 45;
    }
}
