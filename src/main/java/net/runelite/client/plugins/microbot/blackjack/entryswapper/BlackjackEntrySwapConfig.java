package net.runelite.client.plugins.microbot.blackjack.entryswapper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BlackjackEntrySwapConfig.GROUP)
public interface BlackjackEntrySwapConfig extends Config
{
    String GROUP = "blackjackEntrySwap";

    @ConfigItem(
            keyName = "requirements",
            name = "Required pairing",
            description = "How this helper is used",
            position = 0
    )
    default String requirements()
    {
        return "Both this plugin AND [JOJ] Blackjack must be enabled for automated blackjacking. " +
                "This helper only changes menu priority: Knock-Out while a supported NPC stands or attacks, Pickpocket while unconscious. " +
                "[JOJ] Blackjack performs the clicks, healing, movement and wine runs. No shift binding or manual menu swap is required.";
    }
}
