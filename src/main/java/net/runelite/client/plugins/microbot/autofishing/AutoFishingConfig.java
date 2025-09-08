package net.runelite.client.plugins.microbot.autofishing;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.autofishing.dependencies.FishingSpotLocation;

@ConfigGroup("AutoFishing")
public interface AutoFishingConfig extends Config {
    
    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String GENERAL_SECTION = "general";

    // GENERAL SECTION
    @ConfigItem(
            keyName = "fishToCatch",
            name = "Fish to catch",
            description = "Choose the fish type to catch",
            position = 0,
            section = GENERAL_SECTION
    )
    default Fish fishToCatch() {
        return Fish.SHRIMP_AND_ANCHOVIES;
    }

    @ConfigItem(
            keyName = "useBank",
            name = "Use bank",
            description = "Use bank and walk back to fishing location",
            position = 1,
            section = GENERAL_SECTION
    )
    default boolean useBank() {
        return false;
    }

    @ConfigItem(
            keyName = "cookFish",
            name = "Cook fish",
            description = "Cook fish after fishing if a fire/range is nearby",
            position = 2,
            section = GENERAL_SECTION
    )
    default boolean cookFish() {
        return false;
    }

    @ConfigItem(
            keyName = "harpoonSpec",
            name = "Harpoon spec",
            description = "Choose the harpoon type for special attacks",
            position = 3,
            section = GENERAL_SECTION
    )
    default HarpoonType harpoonSpec() {
        return HarpoonType.NONE;
    }
}