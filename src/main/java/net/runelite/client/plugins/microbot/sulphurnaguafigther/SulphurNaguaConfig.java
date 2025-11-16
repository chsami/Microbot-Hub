package net.runelite.client.plugins.microbot.sulphurnaguafigther;

import net.runelite.client.config.*;
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
            description = "If the number of potions falls below this value, new ones will be made.",
            position = 3
    )
    default int moonlightPotionsMinimum() {
        return 2;
    }

    @ConfigItem(
            keyName = "useOffensivePrayers",
            name = "Use Offensive Prayers?",
            description = "When enabled, the bot will use the best offensive prayer (Piety, Rigour, or Augury) depending on combat style.",
            position = 4
    )
    default boolean useOffensivePrayers() {
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
    @ConfigItem(
            keyName = "changeInSulphurousEssence",
            name = "Change Sulphurous Essence?",
            description = "When enabled, the bot will change in your Sulphurous Essence for Runecrafting xp .",
            position = 6
    )
    default boolean changeInSulphurousEssence() {
        return false;
    }
    @ConfigSection(
            name = "Loot Settings",
            description = "Toggle which specific stackable items to loot",
            position = 7,
            closedByDefault = true
    )
    String lootSection = "lootSection";

    @ConfigItem(
            keyName = "lootFireRunes",
            name = "Loot Fire runes",
            description = "Loot Fire runes (554)",
            position = 0,
            section = lootSection
    )
    default boolean lootFireRunes() {
        return true;
    }

    @ConfigItem(
            keyName = "lootChaosRunes",
            name = "Loot Chaos runes",
            description = "Loot Chaos runes (562)",
            position = 1,
            section = lootSection
    )
    default boolean lootChaosRunes() {
        return true;
    }

    @ConfigItem(
            keyName = "lootNatureRunes",
            name = "Loot Nature runes",
            description = "Loot Nature runes (561)",
            position = 2,
            section = lootSection
    )
    default boolean lootNatureRunes() {
        return true;
    }

    @ConfigItem(
            keyName = "lootDeathRunes",
            name = "Loot Death runes",
            description = "Loot Death runes (560)",
            position = 3,
            section = lootSection
    )
    default boolean lootDeathRunes() {
        return true;
    }

    @ConfigItem(
            keyName = "lootIronOre",
            name = "Loot Iron ore (noted)",
            description = "Loot noted Iron ore (441)",
            position = 4,
            section = lootSection
    )
    default boolean lootIronOre() {
        return true;
    }

    @ConfigItem(
            keyName = "lootCoal",
            name = "Loot Coal (noted)",
            description = "Loot noted Coal (454)",
            position = 5,
            section = lootSection
    )
    default boolean lootCoal() {
        return true;
    }

    @ConfigItem(
            keyName = "lootCopperOre",
            name = "Loot Copper ore (noted)",
            description = "Loot noted Copper ore (437)",
            position = 6,
            section = lootSection
    )
    default boolean lootCopperOre() {
        return true;
    }

    @ConfigItem(
            keyName = "lootTinOre",
            name = "Loot Tin ore (noted)",
            description = "Loot noted Tin ore (439)",
            position = 7,
            section = lootSection
    )
    default boolean lootTinOre() {
        return true;
    }

    @ConfigItem(
            keyName = "lootMithrilOre",
            name = "Loot Mithril ore (noted)",
            description = "Loot noted Mithril ore (448)",
            position = 8,
            section = lootSection
    )
    default boolean lootMithrilOre() {
        return true;
    }

    @ConfigItem(
            keyName = "lootSilverOre",
            name = "Loot Silver ore (noted)",
            description = "Loot noted Silver ore (443)",
            position = 9,
            section = lootSection
    )
    default boolean lootSilverOre() {
        return true;
    }

    @ConfigItem(
            keyName = "lootSulphurousEssence",
            name = "Loot SulphurousEssence",
            description = "Loot Sulphurous Essence (29087)",
            position = 10,
            section = lootSection
    )
    default boolean lootSulphurousEssence() {
        return true;
    }
    @ConfigItem(
            keyName = "lootSulphurBlades",
            name = "Loot Sulphur Blades",
            description = "Loot Sulphur Blades (29084)",
            position = 11,
            section = lootSection
    )
    default boolean lootSulphurousBlades() {
        return true;
    }




}