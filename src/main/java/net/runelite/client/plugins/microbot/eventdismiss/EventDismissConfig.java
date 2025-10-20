package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("EventDismiss")
public interface EventDismissConfig extends Config {

    @ConfigItem(
            name = "Count Check Dismiss",
            keyName = "dismissCountCheck",
            position = 3,
            description = "Dismiss Count Check random event"
    )
    default boolean dismissCountCheck() {
        return false;
    }

    @ConfigItem(
            name = "Genie Dismiss",
            keyName = "dismissGenie",
            position = 9,
            description = "Dismiss Genie random event"
    )
    default boolean dismissGenie() {
        return false;
    }
}
