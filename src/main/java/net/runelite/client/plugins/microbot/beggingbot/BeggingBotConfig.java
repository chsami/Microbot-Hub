package net.runelite.client.plugins.microbot.beggingbot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("beggingbot")
public interface BeggingBotConfig extends Config
{
    @ConfigItem(
        position = 0,
        keyName = "minChatSeconds",
        name = "Min seconds between messages",
        description = "Shortest gap between two begging messages"
    )
    @Range(min = 3, max = 60)
    default int minChatSeconds() { return 5; }

    @ConfigItem(
        position = 1,
        keyName = "maxChatSeconds",
        name = "Max seconds between messages",
        description = "Longest gap between two begging messages"
    )
    @Range(min = 3, max = 120)
    default int maxChatSeconds() { return 10; }

    @ConfigItem(
        position = 2,
        keyName = "noTradeHopMinutes",
        name = "Hop after minutes without a trade",
        description = "Hop worlds when no player has opened a trade with you for this long"
    )
    @Range(min = 1, max = 120)
    default int noTradeHopMinutes() { return 15; }

    @ConfigItem(
        position = 3,
        keyName = "playerRadius",
        name = "Player radius (tiles)",
        description = "Players within this many tiles count as nearby"
    )
    @Range(min = 3, max = 30)
    default int playerRadius() { return 12; }

    @ConfigItem(
        position = 4,
        keyName = "noPlayersHopSeconds",
        name = "Hop after seconds with nobody nearby",
        description = "Hop worlds when no other player has been within the player radius for this long"
    )
    @Range(min = 5, max = 300)
    default int noPlayersHopSeconds() { return 20; }

    @ConfigItem(
        position = 5,
        keyName = "customMessages",
        name = "Extra messages",
        description = "Additional begging lines, separated by | (added to the built-in ones, 80 characters max each)"
    )
    default String customMessages() { return ""; }
}
