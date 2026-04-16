package net.runelite.client.plugins.microbot.leaguesfiremaking;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("LeaguesFiremaking")
@ConfigInformation("<h2>AI Firemaking</h2>" +
        "<h3>Version: " + LeaguesFiremakingPlugin.version + "</h3>" +
        "<p>Withdraws logs from the bank, finds open space, and lights fires in lines.</p>" +
        "<p>Scans surrounding tiles to pick the best row, adapts around existing fires and obstacles.</p>" +
        "<p>Supports Bank Heist briefcase for instant banking.</p>")
public interface LeaguesFiremakingConfig extends Config {

    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Banking",
            description = "Banking settings",
            position = 1
    )
    String bankingSection = "banking";

    @ConfigItem(
            keyName = "logType",
            name = "Log type",
            description = "Which logs to burn",
            position = 0,
            section = generalSection
    )
    default LogType logType() {
        return LogType.LOGS;
    }

    @ConfigItem(
            keyName = "progressiveMode",
            name = "Progressive mode",
            description = "Automatically pick the best log for your Firemaking level",
            position = 1,
            section = generalSection
    )
    default boolean progressiveMode() {
        return false;
    }

    @Range(min = 10, max = 50)
    @ConfigItem(
            keyName = "scanRadius",
            name = "Scan radius",
            description = "How many tiles to scan around your starting position for open space",
            position = 2,
            section = generalSection
    )
    default int scanRadius() {
        return 25;
    }

    @ConfigItem(
            keyName = "useBriefcase",
            name = "Use Bank Heist briefcase",
            description = "Use the banker's briefcase to teleport to a bank instead of walking (Leagues relic)",
            position = 0,
            section = bankingSection
    )
    default boolean useBriefcase() {
        return false;
    }
}
