package net.runelite.client.plugins.microbot.volcanicashminer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigInformation("VolcanicAshMiner")
@ConfigGroup("VolcanicAshMiner")
public interface VolcanicAshMinerConfig extends Config {
    @ConfigItem(
            keyName = "maxPlayersInArea",
            name = "Max players in area",
            description = "Hop worlds when more than this many other players are mining ash within 15 tiles of you. 0 = off",
            position = 1
    )
    default int maxPlayersInArea() {
        return 0;
    }

    @ConfigItem(
            keyName = "bankAsh",
            name = "Bank Ash",
            description = "Whether the ash should be banked.",
            position = 2
    )
    default boolean bankAsh() {
        return false;
    }
}