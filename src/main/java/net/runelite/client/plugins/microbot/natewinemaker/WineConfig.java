package net.runelite.client.plugins.microbot.natewinemaker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;


@ConfigGroup("WineMaker")
public interface WineConfig extends Config {

    @ConfigItem(
            keyName = "stopBeforeMax",
            name = "Stop before 99",
            description = "Log out and stop once 99 Cooking is within one batch (14 wines), leaving the final batch for you to craft",
            position = 0
    )
    default boolean stopBeforeMax() {
        return false;
    }
}
