package net.runelite.client.plugins.microbot.firemakingplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("FiremakingPlus")
@ConfigInformation("<h2>Auto Firemaking Plus</h2>" +
        "<h3>Version: " + AutoFiremakingPlusPlugin.version + "</h3>" +
        "<p>1. <strong>Method:</strong> <em>Forester's Campfire</em> (stand at a bank, add logs to a " +
        "campfire, creating one if none is nearby - AFK) or <em>Line firemaking</em> (light logs in a " +
        "line stepping west, then bank - higher XP/hr). Stand near a bank (the Grand Exchange is ideal) " +
        "with logs + a tinderbox in the bank.</p>" +
        "<p>2. <strong>Log type:</strong> which logs to burn. <strong>Progressive</strong> auto-picks the " +
        "best logs your level can burn.</p>" +
        "<p>3. <strong>Scan radius</strong> (Line only): how far to search for an open line.</p>" +
        "<p>4. <strong>Stop after / Target level:</strong> auto-shutdown thresholds. Target level banks first.</p>" +
        "<p>5. <strong>League mode:</strong> periodic arrow-key press to defeat the idle-logout.</p>" +
        "<p>6. <strong>Speed mode:</strong> disables Microbot antiban. Throwaway accounts only.</p>")
public interface AutoFiremakingPlusConfig extends Config {

    @ConfigSection(name = "General", description = "General settings", position = 0)
    String generalSection = "general";

    @ConfigItem(
            keyName = "method",
            name = "Method",
            description = "Forester's Campfire (AFK, one spot) or Line firemaking (higher XP/hr, walks a line).",
            position = 0,
            section = generalSection
    )
    default FiremakingMethod method() {
        return FiremakingMethod.CAMPFIRE;
    }

    @ConfigItem(
            keyName = "logType",
            name = "Log type",
            description = "Which logs to burn (ignored when Progressive is on).",
            position = 1,
            section = generalSection
    )
    default Logs logType() {
        return Logs.MAPLE;
    }

    @ConfigItem(
            keyName = "progressiveMode",
            name = "Progressive",
            description = "Automatically burn the best logs your Firemaking level allows.",
            position = 2,
            section = generalSection
    )
    default boolean progressiveMode() {
        return false;
    }

    @ConfigItem(
            keyName = "maximizeLogSpace",
            name = "Maximize log space",
            description = "Campfire method only: when a Forester's Campfire is already nearby, bank the "
                    + "tinderbox and carry one extra log (28 instead of 27). When no campfire is up it still "
                    + "withdraws a tinderbox to light its own. Off = always carry a tinderbox (fewer bank trips).",
            position = 3,
            section = generalSection
    )
    default boolean maximizeLogSpace() {
        return true;
    }

    @Range(min = 10, max = 50)
    @ConfigItem(
            keyName = "scanRadius",
            name = "Scan radius",
            description = "Line firemaking only: how far around your start tile to search for an open line.",
            position = 4,
            section = generalSection
    )
    default int scanRadius() {
        return 25;
    }

    @ConfigItem(
            keyName = "stopAfterMinutes",
            name = "Stop after (minutes)",
            description = "Auto-shutdown after this many minutes of runtime. 0 = no limit.",
            position = 5,
            section = generalSection
    )
    default int stopAfterMinutes() {
        return 0;
    }

    @ConfigItem(
            keyName = "stopAfterXp",
            name = "Stop after (XP gained)",
            description = "Auto-shutdown after gaining this much Firemaking XP. 0 = no limit.",
            position = 6,
            section = generalSection
    )
    default int stopAfterXp() {
        return 0;
    }

    @ConfigItem(
            keyName = "targetLevel",
            name = "Target level",
            description = "Stop when Firemaking reaches this level. Banks the inventory first. 0 = disabled.",
            position = 7,
            section = generalSection
    )
    default int targetLevel() {
        return 0;
    }

    @ConfigItem(
            keyName = "leagueMode",
            name = "League mode (anti-AFK)",
            description = "Periodically presses an arrow key to reset the idle-logout timer.",
            position = 8,
            section = generalSection
    )
    default boolean leagueMode() {
        return false;
    }

    @ConfigItem(
            keyName = "speedMode",
            name = "Speed mode (less antiban)",
            description = "Disables Microbot's antiban. Faster, more pattern-detectable. Throwaway only.",
            position = 9,
            section = generalSection
    )
    default boolean speedMode() {
        return false;
    }
}
