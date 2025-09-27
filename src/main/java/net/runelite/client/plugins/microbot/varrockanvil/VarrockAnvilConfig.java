package net.runelite.client.plugins.microbot.varrockanvil;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.varrockanvil.enums.AnvilItem;
import net.runelite.client.plugins.microbot.varrockanvil.enums.Bars;


@ConfigGroup("VarrockAnvil")
@ConfigInformation("This plugin smiths bars at the Varrock anvil.<br /><br />" + 
    "For bugs or feature requests, contact me through Discord (@StickToTheScript).")
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
            description = "The desired object to smith at the anvil",
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
