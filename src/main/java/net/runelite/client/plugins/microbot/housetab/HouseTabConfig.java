package net.runelite.client.plugins.microbot.housetab;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.housetab.enums.HouseTablet;

@ConfigGroup(HouseTabConfig.GROUP)
public interface HouseTabConfig extends Config {

    String GROUP = "HouseTab";

    @ConfigItem(
            keyName = "tablet",
            name = "Tablet",
            description = "Choose which spell tablet to make",
            position = 0
    )
    default HouseTablet tablet()
    {
        return HouseTablet.TELEPORT_TO_HOUSE;
    }

    @ConfigItem(
            keyName = "OwnHouse",
            name = "Own house",
            description = "Use your own house",
            position = 1
    )
    default boolean ownHouse()
    {
        return false;
    }

    @ConfigItem(
            keyName = "Player Name",
            name = "Player Name",
            description = "Choose the player name's house",
            position = 2
    )
    default String housePlayerName()
    {
        return "";
    }
}
