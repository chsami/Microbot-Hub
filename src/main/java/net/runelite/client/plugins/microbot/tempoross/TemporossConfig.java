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



    // Equipment settings
    // boolean if we have Spirit Angler's outfit
    @ConfigItem(
        keyName = "spiritAnglers",
        name = "Spirit Angler's",
        description = "Enable if wearing the Spirit Angler's outfit. Grants automatic tethering during waves, so no rope is needed.",
        position = 1,
        section = equipmentSection
    )
    default boolean spiritAnglers() {
        return false;
    }

    // Harpoon settings
    // Harpoon type to use
    @ConfigItem(
        keyName = "harpoonType",
        name = "Harpoon",
        description = "Which harpoon to use for fishing. Dragon/Infernal/Crystal have special attacks. Infernal auto-cooks some fish. Barehand requires Barbarian Fishing training.",
        position = 1,
        section = harpoonSection
    )
    default HarpoonType harpoonType() {
        return HarpoonType.INFERNAL_HARPOON;
    }

    @ConfigItem(
            keyName = "enableHarpoonSpec",
            name = "Use Harpoon Special",
            description = "Use the harpoon's special attack when harpooning the spirit pool. Boosts fishing speed temporarily. Only works with Dragon, Infernal, or Crystal harpoons.",
            position = 2,
            section = harpoonSection
    )
    default boolean enableHarpoonSpec() {
        return true;
    }
}
