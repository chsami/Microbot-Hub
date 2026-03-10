package net.runelite.client.plugins.microbot.accountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("accountbuilder")
public interface AccountBuilderConfig extends Config {

    @ConfigItem(
            keyName = "trainingStyle",
            name = "Combat Training Style",
            description = "Preferred attack style during melee combat training",
            position = 1
    )
    default CombatStyle trainingStyle() {
        return CombatStyle.ATTACK;
    }

    @ConfigItem(
            keyName = "enableBreaks",
            name = "Enable AFK Breaks",
            description = "Take randomized AFK breaks between tasks",
            position = 2
    )
    default boolean enableBreaks() {
        return true;
    }

    @ConfigItem(
            keyName = "logLevel",
            name = "Log Level",
            description = "How much to log to in-game chat",
            position = 3
    )
    default LogLevel logLevel() {
        return LogLevel.INFO;
    }
}
