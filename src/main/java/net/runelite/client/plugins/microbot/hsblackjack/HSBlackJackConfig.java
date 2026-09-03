package net.runelite.client.plugins.microbot.hsblackjack;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.hsblackjack.enums.BlackjackType;

@ConfigGroup(HSBlackJackConfig.configGroup)
public interface HSBlackJackConfig extends Config {
    String configGroup = "hs-blackjack";

    @ConfigSection(name = "Combat", description = "Which blackjack to use", position = 0)
    String combatSection = "combat";

    @ConfigItem(
            keyName = "blackjackType",
            name = "Blackjack",
            description = "Which blackjack is used.",
            position = 0,
            section = combatSection
    )
    default BlackjackType blackjackType() {
        return BlackjackType.WILLOW;
    }
}