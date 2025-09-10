package net.runelite.client.plugins.microbot.banchecker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
@ConfigGroup("banchecker")
public interface BanCheckerConfig extends Config {
    // enable ban checker
    @ConfigItem(
            keyName = "enabled",
            name = "Enable Ban Checker",
            description = "Checks if account is banned when in login screen."
    )
    default boolean enabled() { return true; }

    @ConfigItem(
            keyName = "loopCheck",
            name = "Loop Check",
            description = "Keeps checking every game tick."
    )
    default boolean loopCheck() { return true; }

    @ConfigItem(
            keyName = "writeConfig",
            name = "Write Config",
            description = "Write the banned status to config."
    )
    default boolean writeConfig()
    {
        return false;
    }

    // hidden storage for the ban flag
    @ConfigItem(
            keyName = "isBanned",
            name = "Ban status",
            description = "Stores whether the account is banned.",
            hidden = true
    )
    default boolean isBanned() { return false; }
}