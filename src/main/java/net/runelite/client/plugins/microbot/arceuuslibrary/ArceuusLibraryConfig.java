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
            name = "Reward XP",
            description = "Which XP to claim from the Book of arcane knowledge reward (Magic = 15× Magic level, Runecraft = 5× Runecraft level)",
            position = 1
    )
    default RewardXp rewardXp() { return RewardXp.MAGIC; }

    @ConfigItem(
            keyName = "readSoulJourney",
            name = "Read Soul Journey",
            description = "If the requested book is Soul Journey, read it before delivery to start the Bear Your Soul miniquest",
            position = 2
    )
    default boolean readSoulJourney() { return true; }
}
