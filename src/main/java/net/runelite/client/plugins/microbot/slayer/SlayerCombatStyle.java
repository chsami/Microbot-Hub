package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Combat styles for slayer tasks.
 * Determines how the bot approaches combat for a given task.
 */
@Getter
@RequiredArgsConstructor
public enum SlayerCombatStyle {
    MELEE("Melee", "Standard melee combat"),
    RANGED("Ranged", "Standard ranged combat"),
    MAGIC("Magic", "Standard magic combat (single target)"),
    BURST("Burst", "Multi-target burst spells with goading"),
    BARRAGE("Barrage", "Multi-target barrage spells with goading");

    private final String displayName;
    private final String description;

    /**
     * Parses a string to a combat style.
     * Supports various aliases for convenience.
     */
    public static SlayerCombatStyle fromString(String style) {
        if (style == null || style.isEmpty()) {
            return MELEE; // Default
        }

        String normalized = style.toUpperCase().trim();

        switch (normalized) {
            case "MELEE":
            case "M":
                return MELEE;
            case "RANGED":
            case "RANGE":
            case "R":
                return RANGED;
            case "MAGIC":
            case "MAGE":
                return MAGIC;
            case "BURST":
            case "B":
            case "ICE BURST":
            case "ICEBURST":
                return BURST;
            case "BARRAGE":
            case "BB":
            case "ICE BARRAGE":
            case "ICEBARRAGE":
                return BARRAGE;
            default:
                return MELEE;
        }
    }

    /**
     * Checks if this style is a multi-target AoE style (burst or barrage)
     */
    public boolean isAoeStyle() {
        return this == BURST || this == BARRAGE;
    }
}
