package net.runelite.client.plugins.microbot.AmmoniteCrabs;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.AmmoniteCrabs.enums.AmmoniteCrabLocations;
import net.runelite.client.plugins.microbot.AmmoniteCrabs.enums.OffensivePotions;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup("AmmoniteCrabConfig")
public interface AmmoniteCrabConfig extends Config {

    @ConfigItem(
            keyName = "Use Food",
            name = "Use Food",
            description = "Use Food?",
            position = 0
    )
    default boolean useFood() {
        return false;
    }

    @ConfigItem(
            keyName = "Food",
            name = "Food",
            description = "type of food",
            position = 1
    )
    default Rs2Food food() {
        return Rs2Food.MONKFISH;
    }

    @ConfigItem(
            keyName = "usePotions",
            name = "Use Potions?",
            description = "Use Potions?",
            position = 2
    )
    default boolean usePotions() {
        return false;
    }

    @ConfigItem(
            keyName = "potions",
            name = "Potions",
            description = "type of potion",
            position = 3
    )
    default OffensivePotions potions() {
        return OffensivePotions.COMBAT_POTION;
    }

    @ConfigItem(
            position = 4,
            keyName = "withdrawNumber",
            name = "Number of potions to withdraw",
            description = "Number of potions to withdraw from bank"
    )
    @Range(
            min = 0,
            max = 28
    )
    default int withdrawNumber() {
        return 4;
    }

    @ConfigItem(
            keyName = "crabLocation",
            name = "Crab Location",
            description = "Choose the location of the Ammonite Crabs",
            position = 5
    )
    default AmmoniteCrabLocations crabLocation() {
        return AmmoniteCrabLocations.NORTH_WEST;
    }

    @ConfigItem(
            keyName = "lootSeaweedSpores",
            name = "Loot Seaweed Spores",
            description = "Loot Seaweed Spores?",
            position = 6
    )
    default boolean lootSeaweedSpores() {
        return false;
    }

}