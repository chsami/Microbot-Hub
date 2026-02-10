package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Prayer options for slayer task profiles.
 * Defines which protection prayer (if any) to use during combat.
 */
@Getter
@RequiredArgsConstructor
public enum SlayerPrayer {
    NONE("None", null),
    PROTECT_MELEE("Protect from Melee", Rs2PrayerEnum.PROTECT_MELEE),
    PROTECT_MAGIC("Protect from Magic", Rs2PrayerEnum.PROTECT_MAGIC),
    PROTECT_RANGED("Protect from Missiles", Rs2PrayerEnum.PROTECT_RANGE);

    private final String displayName;
    private final Rs2PrayerEnum prayer;

    /**
     * Parses a prayer from a string (case-insensitive, supports multiple formats)
     */
    public static SlayerPrayer fromString(String str) {
        if (str == null || str.isEmpty()) {
            return NONE;
        }

        String normalized = str.toUpperCase().trim()
                .replace(" ", "_")
                .replace("PROTECT_FROM_", "PROTECT_")
                .replace("MISSILES", "RANGED");

        // Try exact match first
        for (SlayerPrayer prayer : values()) {
            if (prayer.name().equalsIgnoreCase(normalized)) {
                return prayer;
            }
        }

        // Try partial matches
        for (SlayerPrayer prayer : values()) {
            if (prayer.name().contains(normalized) || normalized.contains(prayer.name())) {
                return prayer;
            }
            if (prayer.displayName.toUpperCase().contains(str.toUpperCase())) {
                return prayer;
            }
        }

        // Common aliases - short forms for quick config entry
        switch (normalized) {
            case "MELEE":
            case "PROT_MELEE":
            case "PMELEE":
            case "PM":
                return PROTECT_MELEE;
            case "MAGIC":
            case "MAGE":
            case "PROT_MAGIC":
            case "PROT_MAGE":
            case "PMAGIC":
            case "PMAGE":
                return PROTECT_MAGIC;
            case "RANGED":
            case "RANGE":
            case "PROT_RANGED":
            case "PROT_RANGE":
            case "PRANGED":
            case "PRANGE":
            case "PR":
                return PROTECT_RANGED;
            default:
                return NONE;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
