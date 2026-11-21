package net.runelite.client.plugins.microbot.autofarmer;

import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public enum HerbSeedType {
    BEST("Best available", -1, 0, 0), // Special option for dynamic selection
    GUAM("Guam seed", ItemID.GUAM_SEED, 9, 12.5),
    MARRENTILL("Marrentill seed", ItemID.MARRENTILL_SEED, 14, 15),
    TARROMIN("Tarromin seed", ItemID.TARROMIN_SEED, 19, 18),
    HARRALANDER("Harralander seed", ItemID.HARRALANDER_SEED, 26, 24),
    RANARR("Ranarr seed", ItemID.RANARR_SEED, 32, 30.5),
    TOADFLAX("Toadflax seed", ItemID.TOADFLAX_SEED, 38, 38.5),
    IRIT("Irit seed", ItemID.IRIT_SEED, 44, 48.5),
    AVANTOE("Avantoe seed", ItemID.AVANTOE_SEED, 50, 61.5),
    KWUARM("Kwuarm seed", ItemID.KWUARM_SEED, 56, 78),
    SNAPDRAGON("Snapdragon seed", ItemID.SNAPDRAGON_SEED, 62, 98.5),
    CADANTINE("Cadantine seed", ItemID.CADANTINE_SEED, 67, 120),
    LANTADYME("Lantadyme seed", ItemID.LANTADYME_SEED, 73, 151.5),
    DWARF_WEED("Dwarf weed seed", ItemID.DWARF_WEED_SEED, 79, 192),
    TORSTOL("Torstol seed", ItemID.TORSTOL_SEED, 85, 224.5);

    private final String seedName;
    private final int itemId;
    private final int levelRequired;

    // Farming XP per herb picked. Patch completion XP is separate.
    private final double harvestXp;

    HerbSeedType(String seedName, int itemId, int levelRequired, double harvestXp) {
        this.seedName = seedName;
        this.itemId = itemId;
        this.levelRequired = levelRequired;
        this.harvestXp = harvestXp;
    }

    public String getSeedName() {
        return seedName;
    }

    public int getItemId() {
        return itemId;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    double getHarvestXp() {
        return harvestXp;
    }

    String getHerbName() {
        return seedName.substring(0, seedName.length() - " seed".length());
    }

    /**
     * Gets all herb types that can be planted at the given farming level,
     * sorted by level requirement (highest first)
     *
     * @param farmingLevel The player's farming level
     * @return List of plantable herbs sorted by level (highest first)
     */
    public static List<HerbSeedType> getPlantableHerbs(int farmingLevel) {
        return Arrays.stream(values())
                .filter(herb -> herb != BEST && herb.levelRequired <= farmingLevel)
                .sorted(Comparator.comparingInt(HerbSeedType::getLevelRequired).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Checks if this herb type can be planted at the given farming level
     *
     * @param farmingLevel The player's farming level
     * @return true if the herb can be planted
     */
    public boolean canPlant(int farmingLevel) {
        return this != BEST && farmingLevel >= this.levelRequired;
    }

    @Override
    public String toString() {
        return seedName;
    }
}
