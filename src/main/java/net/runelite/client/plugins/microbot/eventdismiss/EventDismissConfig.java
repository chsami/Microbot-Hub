package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.Skill;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("EventDismiss")
public interface EventDismissConfig extends Config {

    @ConfigSection(
            name = "Lamp Events",
            description = "Settings for lamp-giving random events",
            position = 0
    )
    String lampSection = "lampEvents";

    @ConfigItem(
            name = "Genie",
            keyName = "genieAction",
            position = 0,
            section = lampSection,
            description = "Accept lamp or dismiss Genie random event"
    )
    default EventAction genieAction() {
        return EventAction.ACCEPT;
    }

    @ConfigItem(
            name = "Count Check",
            keyName = "countCheckAction",
            position = 1,
            section = lampSection,
            description = "Accept lamp or dismiss Count Check random event"
    )
    default EventAction countCheckAction() {
        return EventAction.ACCEPT;
    }

    @ConfigItem(
            name = "Lamp Skill",
            keyName = "lampSkill",
            position = 2,
            section = lampSection,
            description = "Skill to use experience lamps on"
    )
    default Skill lampSkill() {
        return Skill.HERBLORE;
    }

    @ConfigItem(
            name = "Use Stray Lamps",
            keyName = "checkForLamps",
            position = 3,
            section = lampSection,
            description = "Automatically use lamps found in inventory from any source"
    )
    default boolean checkForLamps() {
        return false;
    }
}
