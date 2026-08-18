package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.tempoross.enums.HarpoonType;

@ConfigGroup("microbot-tempoross")
@ConfigInformation("<h2>S-1D Tempoross</h2>\n" +
        "<h3>Version: " + TemporossPlugin.version + "</h3>\n" +
        "<p>1. <strong>Start the bot outside of the minigame area</strong> to ensure proper functionality.</p>\n" +
        "<p></p>\n" +
        "<p>2. <strong>Solo Mode:</strong> If selecting solo mode, an <em>Infernal Harpoon</em> is REQUIRED. You also need a <strong>MINIMUM</strong> of <em>19</em> free inv slots</p>\n")
public interface TemporossConfig extends Config {
    //sections
    // General
    // Equipment
    // Tools

    @ConfigSection(
        name = "General",
        description = "General settings",
        position = 1,
        closedByDefault = true
    )
    String generalSection = "General";

    @ConfigSection(
        name = "Equipment",
        description = "Equipment settings",
        position = 2,
        closedByDefault = true
    )
    String equipmentSection = "Equipment";

    @ConfigSection(
        name = "Harpoon",
        description = "Harpoon settings",
        position = 3,
        closedByDefault = true
    )
    String harpoonSection = "Harpoon";

    // General settings
    // number of buckets to bring (default 6)
    @ConfigItem(
        keyName = "buckets",
        name = "Buckets",
        description = "Buckets of water to douse fires and cool cannons. More buckets = more fire coverage but fewer inventory slots for fish.",
        position = 1,
        section = generalSection
    )
    default int buckets() {
        return 6;
    }


    // boolean to bring a hammer
    @ConfigItem(
        keyName = "hammer",
        name = "Hammer",
        description = "Bring a hammer to repair the mast and totem pole when damaged by waves. Earns Construction XP and prevents storm intensity from rising.",
        position = 2,
        section = generalSection
    )
    default boolean hammer() {
        return true;
    }


    // boolean to bring a rope
    @ConfigItem(
        keyName = "rope",
        name = "Rope",
        description = "Bring a rope to tether to the mast or totem pole before waves hit. Without a rope, waves knock you back and deal damage. Not needed with Spirit Angler's outfit.",
        position = 3,
        section = generalSection
    )
    default boolean rope() {
        return true;
    }
    // boolean to play solo
    @ConfigItem(
        keyName = "solo",
        name = "Solo",
        description = "Solo mode starts a private instance. Requires Infernal Harpoon and at least 19 free inventory slots. Mass mode joins the public boat with other players.",
        position = 4,
        section = generalSection
    )
    default boolean solo() {
        return false;
    }

    @ConfigItem(
        keyName = "world",
        name = "World",
        description = "Hop to this world when the script starts, if not already inside the minigame. 0 disables.",
        position = 5,
        section = generalSection
    )
    default int world() {
        return 422;
    }

    @ConfigItem(
        keyName = "autoEquip",
        name = "Auto-equip best gear",
        description = "Once per start, equip the best Tempoross gear you own from the bank: Spirit Angler or Angler pieces per slot, an Imcando hammer (off-hand), and your configured harpoon. Angler pieces raise points, and points are permits.",
        position = 9,
        section = equipmentSection
    )
    default boolean autoEquip() {
        return true;
    }

    @ConfigItem(
        keyName = "collectRewards",
        name = "Collect rewards",
        description = "Spend reward permits at the reward pool between games, once the thresholds below are met.",
        position = 6,
        section = generalSection
    )
    default boolean collectRewards() {
        return true;
    }

    @ConfigItem(
        keyName = "permitThreshold",
        name = "Permits before collecting",
        description = "Bank up this many reward permits before spending them. Up to 8000 rolls can be stored, so there is no rush.",
        position = 7,
        section = generalSection
    )
    default int permitThreshold() {
        return 50;
    }

    @ConfigItem(
        keyName = "minFishingLevel",
        name = "Min Fishing level to collect",
        description = "Hold permits until BASE Fishing reaches this. Rewards are rolled from your Fishing level at the moment of collection, not when the permits were earned, so saving them until a higher level is strictly better. Boosts do not count. Set 1 to collect regardless.",
        position = 8,
        section = generalSection
    )
    default int minFishingLevel() {
        return 1;
    }




    // Equipment settings
    // boolean if we have Spirit Angler's outfit

    // Harpoon settings
    @ConfigItem(
        keyName = "barehanded",
        name = "Fish bare-handed",
        description = "Catch harpoonfish with your hands (requires Barbarian Fishing training). When off, auto-equip supplies the best harpoon you own — infernal first for max permits.",
        position = 1,
        section = harpoonSection
    )
    default boolean barehanded() {
        return false;
    }

    @ConfigItem(
            keyName = "enableHarpoonSpec",
            name = "Use Harpoon Special",
            description = "Fire the harpoon special attack (+3 Fishing) while catching at the fish spots. Dragon, Infernal, or Crystal only, and only when the harpoon is wielded.",
            position = 2,
            section = harpoonSection
    )
    default boolean enableHarpoonSpec() {
        return true;
    }
}
