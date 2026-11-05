package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("arceuusLibrary")
@ConfigInformation("<div style='font-family: Arial, sans-serif; line-height: 1.5;'>"
        + "<h2>Arceuus Library</h2>"
        + "<p>Automates the Arceuus Library task by reading the upstream "
        + "<strong>Kourend Library</strong> RuneLite plugin's solver state. "
        + "That plugin must be enabled (this plugin will enable it on start).</p>"
        + "<p>Inside the library, the script finds a customer, fetches their requested "
        + "book from a bookcase, and delivers it for Magic or Runecraft XP.</p>"
        + "<p>You need to be on a members world. Started anywhere else, it travels to the library on its own using the "
        + "web walker's ships, charters and teleports. If walking fails it breaks an "
        + "Arceuus library teleport or Kourend castle teleport tab from your inventory. "
        + "If it still can't get there, it stops and tells you why.</p>"
        + "<p>Stamina/energy potions are off by default. When turned on, the script "
        + "drinks them to keep running and, when it runs out, walks to the Arceuus "
        + "bank to take more, then returns to the library.</p>"
        + "<p>The script keeps run turned on whenever you have more than 10% run energy.</p>"
        + "</div>")
public interface ArceuusLibraryConfig extends Config
{
    @ConfigItem(
            keyName = "rewardXp",
            name = "Reward skill",
            description = "Which XP to claim from the Book of arcane knowledge (Magic = 15x Magic level, Runecraft = 5x Runecraft level). Auto picks whichever pays more; ties go to Magic. Only used when 'Read reward book' is on.",
            position = 2
    )
    default RewardXp rewardXp() { return RewardXp.MAGIC; }

    @ConfigItem(
            keyName = "readRewardBook",
            name = "Read reward book",
            description = "Read a Book of arcane knowledge as soon as it is received. When off, the books stay in your inventory and fill it up; the script needs free slots to carry library books, so bank them now and then.",
            position = 1
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
    default boolean useStaminaPotions() { return false; }

    @ConfigItem(
            keyName = "drinkAtEnergy",
            name = "Drink at run energy %",
            description = "Only used when 'Use stamina/energy potions' is on. Drink a potion when run energy is at or below this percentage",
            position = 5
    )
    @Range(min = 1, max = 99)
    default int drinkAtEnergy() { return 30; }

    @ConfigItem(
            keyName = "potionsToBring",
            name = "Potions to bring",
            description = "Only used when 'Use stamina/energy potions' is on. When you run out of potions and energy is low, the script walks to the Arceuus bank, takes this many, and walks back to the library. If the bank has none, or none reach your inventory, it stops trying and carries on without them. 0 turns restocking off.",
            position = 6
    )
    @Range(min = 0, max = 10)
    default int potionsToBring() { return 2; }
}
