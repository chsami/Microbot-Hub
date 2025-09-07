package net.runelite.client.plugins.microbot.SulphurNaguaFigther;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup("SulphurNaguaAIO")
@ConfigInformation("<html>"
        + "<h2 style='color: #6d9eeb;'>Sulphur Nagua script by VIP</h2>"
        + "<p>This plugin automates fighting Sulphur Nagua in the Cam Torum region of Varlamore.</p>\n"
        + "<p>Requirements:</p>\n"
        + "<ol>\n"
        + "    <li>Access to Varlamore and the Cam Torum area</li>\n"
        + "    <li>Completion of the 'Perilous Moons' quest</li>\n"
        + "    <li>Minimum 43 Prayer</li>\n"
        + "    <li>Minimum 38 Herblore</li>\n"
        + "    <li>Pestle and Mortar to make Potions</li>\n"
        + "</ol>\n"
        + "<p>This script will automatically fight Sulphur Nagua, drink Moonlight Potions, and brew new ones when supplies run out.</p>\n"
        + "</html>")

/**
 * Configuration interface for the Sulphur Nagua script.
 * Defines the user-configurable settings in the plugin side panel.
 */
public interface SulphurNaguaConfig extends Config {
    @ConfigItem(
            keyName = "useInventorySetup",
            name = "Use Inventory Setup?",
            description = "When enabled, the bot will use the specified inventory setup for banking and supplies",
            position = 1
    )
    default boolean useInventorySetup() {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "The inventory setup to use for banking and supplies",
            position = 2
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = "moonlightPotionsMinimum",
            name = "Minimum Moonlight Potions",
            description = "If the number of potions falls below this value, new ones will be made. Set to 0 to always fill the entire free inventory.",
            position = 3
    )
    default int moonlightPotionsMinimum() {
        return 2;
    }

    @ConfigItem(
            keyName = "usePiety",
            name = "Use Piety?",
            description = "When enabled, the bot will also use the Piety prayer during combat.",
            position = 4
    )
    default boolean usePiety() {
        return false;
    }

    @ConfigItem(
            keyName = "naguaLocation",
            name = "Nagua Location",
            description = "Select the Nagua fighting location",
            position = 5
    )
    default SulphurNaguaScript.NaguaLocation naguaLocation() {
        return SulphurNaguaScript.NaguaLocation.CIVITAS_ILLA_FORTIS_WEST;
    }
}