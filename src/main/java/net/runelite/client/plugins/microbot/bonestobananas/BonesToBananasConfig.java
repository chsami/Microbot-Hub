package net.runelite.client.plugins.microbot.bonestobananas;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bonestobananas")
@ConfigInformation(
        "▪ A simple script that casts the Bones to Bananas spell.<br /><br />" +
                "▪ The script will withdraw bones, cast the spell, and deposit the bananas.<br /><br />" +
                "▪ Ensure your bank contains:<br />" +
                "  - Bones.<br />" +
                "  - Nature runes.<br />" +
                "  - Earth and Water runes OR a staff that provides them (e.g., Mud Battlestaff).<br /><br />" +
                "▪ No configuration is needed, just enable the plugin!"
)
public interface BonesToBananasConfig extends Config {

    @ConfigItem(
            keyName = "useNaturalMouse",
            name = "Use Natural Mouse",
            description = "Uses human-like mouse movements instead of direct clicks.",
            position = 1
    )
    default boolean useNaturalMouse() {
        return true; // Default to on for safety
    }
}