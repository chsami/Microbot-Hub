package net.runelite.client.plugins.microbot.leaguestoolkit;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("LeaguesToolkit")
@ConfigInformation("<h2>Leagues Toolkit</h2>" +
        "<h3>Version: " + LeaguesToolkitPlugin.version + "</h3>" +
        "<p>A grab-bag of Leagues-focused utilities. Start with <strong>Anti-AFK</strong> to keep long, " +
        "auto-banking skilling sessions from getting logged out.</p>")
public interface LeaguesToolkitConfig extends Config {

    @ConfigSection(
            name = "Anti-AFK",
            description = "Prevents the idle-timeout logout during long AFK sessions",
            position = 0
    )
    String antiAfkSection = "antiAfkSection";

    @ConfigItem(
            keyName = "enableAntiAfk",
            name = "Enable anti-AFK",
            description = "Periodically triggers input to reset the idle timer so you never get logged out",
            position = 0,
            section = antiAfkSection
    )
    default boolean enableAntiAfk() {
        return true;
    }

    @ConfigItem(
            keyName = "antiAfkMethod",
            name = "Input method",
            description = "What kind of input to send. Random arrow keys look most natural.",
            position = 1,
            section = antiAfkSection
    )
    default AntiAfkMethod antiAfkMethod() {
        return AntiAfkMethod.RANDOM_ARROW_KEY;
    }

    @Range(min = 50, max = 5000)
    @ConfigItem(
            keyName = "antiAfkBufferMin",
            name = "Trigger buffer min (ticks)",
            description = "Minimum ticks before the client's AFK threshold at which to fire input",
            position = 2,
            section = antiAfkSection
    )
    default int antiAfkBufferMin() {
        return 500;
    }

    @Range(min = 50, max = 5000)
    @ConfigItem(
            keyName = "antiAfkBufferMax",
            name = "Trigger buffer max (ticks)",
            description = "Maximum ticks before the client's AFK threshold at which to fire input",
            position = 3,
            section = antiAfkSection
    )
    default int antiAfkBufferMax() {
        return 1500;
    }

    @ConfigSection(
            name = "Toci's Gem Cutter",
            description = "Buys uncut gems from Toci in Aldarin, cuts them, sells them back",
            position = 1,
            closedByDefault = true
    )
    String gemCutterSection = "gemCutterSection";

    @ConfigItem(
            keyName = "enableGemCutter",
            name = "Enable gem cutter",
            description = "Walks to Toci, buys uncut gems, cuts them, sells cut gems back — repeats",
            position = 0,
            section = gemCutterSection
    )
    default boolean enableGemCutter() {
        return false;
    }

    @ConfigItem(
            keyName = "gemType",
            name = "Gem",
            description = "Which gem to cut (requires chisel + coins + crafting level)",
            position = 1,
            section = gemCutterSection
    )
    default GemType gemType() {
        return GemType.RUBY;
    }

    @Range(min = 1000, max = 1_000_000)
    @ConfigItem(
            keyName = "gemCutterMinCoins",
            name = "Min coins to keep",
            description = "When coins drop below this, withdraw more from the bank",
            position = 2,
            section = gemCutterSection
    )
    default int gemCutterMinCoins() {
        return 10_000;
    }

    @ConfigItem(
            keyName = "gemCutterUseBriefcase",
            name = "Use Bank Heist briefcase",
            description = "Use the banker's briefcase to bank (Leagues relic) instead of walking to a bank",
            position = 3,
            section = gemCutterSection
    )
    default boolean gemCutterUseBriefcase() {
        return false;
    }
}
