package net.runelite.client.plugins.microbot.herbrun;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum AllotmentSeedType {
    BEST("Best available", -1, 0),
    POTATO("Potato seed", ItemID.POTATO_SEED, 1),
    ONION("Onion seed", ItemID.ONION_SEED, 5),
    CABBAGE("Cabbage seed", ItemID.CABBAGE_SEED, 7),
    TOMATO("Tomato seed", ItemID.TOMATO_SEED, 12),
    SWEETCORN("Sweetcorn seed", ItemID.SWEETCORN_SEED, 20),
    STRAWBERRY("Strawberry seed", ItemID.STRAWBERRY_SEED, 31),
    WATERMELON("Watermelon seed", ItemID.WATERMELON_SEED, 47),
    SNAPE_GRASS("Snape grass seed", ItemID.SNAPE_GRASS_SEED, 61);

    private final String seedName;
    private final int itemId;
    private final int levelRequired;

    AllotmentSeedType(String seedName, int itemId, int levelRequired) {
        this.seedName = seedName;
        this.itemId = itemId;
        this.levelRequired = levelRequired;
    }

    public static List<AllotmentSeedType> getPlantableSeeds(int farmingLevel) {
        return Arrays.stream(values())
                .filter(seed -> seed != BEST && seed.levelRequired <= farmingLevel)
                .sorted(Comparator.comparingInt(AllotmentSeedType::getLevelRequired).reversed())
                .collect(Collectors.toList());
    }

    public boolean canPlant(int farmingLevel) {
        return this != BEST && farmingLevel >= this.levelRequired;
    }

    @Override
    public String toString() {
        return seedName;
    }
}
