package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

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

    @ConfigSection(
            name = "Section sweep",
            description = "Opportunistic prefetch — after fetching the wanted book, search nearby same-floor bookcases for books we don't already hold so future deliveries can skip the fetch trip entirely",
            position = 10
    )
    String sectionSweepSection = "sectionSweep";

    @ConfigItem(
            keyName = "enableSectionSweep",
            name = "Enable section sweep",
            description = "Pick up books we don't already hold from nearby known bookcases on the same floor",
            position = 11,
            section = sectionSweepSection
    )
    default boolean enableSectionSweep() { return true; }

    @Range(min = 4, max = 30)
    @ConfigItem(
            keyName = "sectionSweepRadius",
            name = "Sweep radius (tiles)",
            description = "Maximum walking distance from the player to a sweep candidate. Bookcases beyond this are ignored.",
            position = 12,
            section = sectionSweepSection
    )
    default int sectionSweepRadius() { return 14; }

    @Range(min = 0, max = 16)
    @ConfigItem(
            keyName = "sectionSweepMaxBookcases",
            name = "Max sweeps per trip",
            description = "Maximum number of extra bookcase searches per fetch trip before delivering. 0 disables sweep entirely.",
            position = 13,
            section = sectionSweepSection
    )
    default int sectionSweepMaxBookcases() { return 6; }
}
