package net.runelite.client.plugins.microbot.autogauntletprayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Auto Gauntlet Prayer")
@ConfigInformation("LiftedMango <br> 0.1.1 <br><br> Does Gauntlet stuff <br><br>Drinks potions automatically"
)
public interface AutoGauntletPrayerConfig extends Config {
    @ConfigItem(
            keyName = "mysticMight?",
            name = "Use lesser prayers?",
            description = "Will use Mystic Might/Eagle Eye/Ultimate Strength",
            position = 1
    )
    default boolean MysticMight() {
        return false;
    }

    @ConfigItem(
            keyName = "DisableWeapon",
            name = "Disable Weapon swaps",
            description = "",
            position = 2
    )
    default boolean DisableWeapon() {
        return false;
    }

    @ConfigItem(
            keyName = "DisablePrayer",
            name = "Disable Prayer swap",
            description = "",
            position = 3
    )
    default boolean DisablePrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "enOverlay",
            name = "Enable Overlay",
            description = "",
            position = 4
    )
    default boolean enOverlay() {
        return true;
    }
}
