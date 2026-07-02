package net.runelite.client.plugins.microbot.natewinemaker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;


@ConfigGroup("WineMaker")
public interface WineConfig extends Config {

    @ConfigItem(
            keyName = "stopBeforeMax",
            name = "Stop before 99",
            description = "Log out and stop the plugin just before 99 Cooking so you can craft the final wines yourself",
            position = 0
    )
    default boolean stopBeforeMax() {
        return false;
    }

    @Range(min = 1, max = 14)
    @ConfigItem(
            keyName = "winesToLeave",
            name = "Wines to leave",
            description = "How many wines to leave for you to craft manually (each jug of wine is 200 xp)",
            position = 1
    )
    default int winesToLeave() {
        return 2;
    }
}
