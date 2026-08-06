package net.runelite.client.plugins.microbot.kraken;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("kraken")
public interface KrakenConfig extends Config {

    @ConfigItem(
            keyName = "listOfItemsToLoot",
            name = "Extra loot names",
            description = "Comma-separated item names to loot in addition to Kraken uniques (which are always looted).",
            position = 0
    )
    default String listOfItemsToLoot() {
        return "dragon trident";
    }

    @ConfigItem(
            keyName = "toggleLootByValue",
            name = "Also loot by GE value",
            description = "If on, also loot any item above the min price below. If off, only uniques + extra names are looted.",
            position = 1
    )
    default boolean toggleLootByValue() {
        return true;
    }

    @ConfigItem(
            keyName = "minPriceOfItemsToLoot",
            name = "Min GE value",
            description = "Min GE value (price × qty) to loot. Only used when 'Also loot by GE value' is on.",
            position = 2
    )
    default int minPriceOfItemsToLoot() {
        return 5000;
    }

    @ConfigItem(
            keyName = "stopWhenOutOfFood",
            name = "Stop when out of food",
            description = "If on, stop the plugin when the inventory has no edible food. Only checked between kills (during IDLE) so a kill is never aborted mid-fight.",
            position = 3
    )
    default boolean stopWhenOutOfFood() {
        return true;
    }
}
