package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("arceuusLibrary")
@ConfigInformation("<div style='font-family: Arial, sans-serif; line-height: 1.5;'>"
        + "<h2>Arceuus Library</h2>"
        + "<p>Automates the Arceuus Library task by reading the upstream "
        + "<strong>Kourend Library</strong> RuneLite plugin's solver state. "
        + "That plugin must be enabled (this plugin will enable it on start).</p>"
        + "<p>Start anywhere inside the library. The script will find a customer, "
        + "fetch their requested book from a bookcase, and deliver it for "
        + "Magic + Runecraft XP.</p>"
        + "</div>")
public interface ArceuusLibraryConfig extends Config
{
    @ConfigItem(
            keyName = "rewardXp",
            name = "Reward skill",
            description = "Which XP to claim from the Book of arcane knowledge (Magic = 15x Magic level, Runecraft = 5x Runecraft level). Auto picks whichever pays more; ties go to Magic.",
            position = 1
    )
    default RewardXp rewardXp() { return RewardXp.AUTO; }

    @ConfigItem(
            keyName = "readRewardBook",
            name = "Read reward book",
            description = "Read a Book of arcane knowledge as soon as it is received",
            position = 2
    )
    default boolean readRewardBook() { return true; }

    @ConfigItem(
            keyName = "readSoulJourney",
            name = "Read Soul Journey",
            description = "If the requested book is Soul Journey, read it before delivery to start the Bear Your Soul miniquest",
            position = 3
    )
    default boolean readSoulJourney() { return true; }

    @ConfigItem(
            keyName = "useStaminaPotions",
            name = "Use stamina/energy potions",
            description = "Drink Extended stamina, Stamina, Super energy or Energy potions (any dose, best first) to keep running",
            position = 4
    )
    default boolean useStaminaPotions() { return true; }

    @ConfigItem(
            keyName = "drinkAtEnergy",
            name = "Drink at run energy %",
            description = "Drink a potion when run energy is at or below this percentage",
            position = 5
    )
    default int drinkAtEnergy() { return 30; }

    @ConfigItem(
            keyName = "potionsToBring",
            name = "Potions to bring",
            description = "How many potions to withdraw from the Arceuus bank when out of them and low on energy (0 disables restocking)",
            position = 6
    )
    default int potionsToBring() { return 2; }
}
