package net.runelite.client.plugins.microbot.varrockanvil;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.varrockanvil.enums.AnvilItem;
import net.runelite.client.plugins.microbot.varrockanvil.enums.Bars;


@ConfigGroup("VarrockAnvil")
@ConfigInformation("Smiths bars and Sailing ship parts at the Varrock West anvil.<br /><br />Supports standard smithing (bronze–runite) and Sailing keels.<br><br>For bugs or feature requests, contact me through Discord (@StickToTheScript).")
public interface VarrockAnvilConfig extends Config {

    @ConfigSection(
            name = "Smithing",
            description = "Smithing Settings",
            position = 0
    )
    String smithingSection = "Smithing";

    @ConfigItem(
            keyName = "barType",
            name = "Bar Type",
            description = "The type of bar to use on the anvil",
            position = 0,
            section = smithingSection
    )
    default Bars sBarType()
    {
        return Bars.BRONZE;
    }

    @ConfigItem(
            keyName = "smithObject",
            name = "Smith Object",
            description = "The desired object to smith at the anvil. Supports classic items and Sailing ship parts: 'Keel parts' (regular) or 'Large keel parts'. Pick your bar type (e.g. Mithril), then the keel size as the object. Plugin builds the exact name (e.g. 'Mithril keel parts' or 'Large mithril keel parts') and handles the continue dialogue that often appears first for ship parts. Name fallback for robustness.",
            position = 1,
            section = smithingSection
    )
    default AnvilItem sAnvilItem()
    {
        return AnvilItem.SCIMITAR;
    }

    @ConfigItem(
            keyName = "logout",
            name = "Logout On Complete",
            description = "Log out when completed all bars.",
            position = 2,
            section = smithingSection
    )
    default boolean sLogout()
    {
        return true;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug",
            description = "Enable debug information",
            position = 3,
            section = smithingSection
    )
    default boolean sDebug()
    {
        return false;
    }
}
