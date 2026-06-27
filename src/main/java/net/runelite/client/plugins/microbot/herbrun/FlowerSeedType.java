package net.runelite.client.plugins.microbot.herbrun;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum FlowerSeedType {
    MARIGOLD("Marigold seed", ItemID.MARIGOLD_SEED, 2),
    ROSEMARY("Rosemary seed", ItemID.ROSEMARY_SEED, 11),
    NASTURTIUM("Nasturtium seed", ItemID.NASTURTIUM_SEED, 24),
    WOAD("Woad seed", ItemID.WOAD_SEED, 25),
    LIMPWURT("Limpwurt seed", ItemID.LIMPWURT_SEED, 26),
    WHITE_LILY("White lily seed", ItemID.WHITE_LILY_SEED, 58);

    private final String seedName;
    private final int itemId;
    private final int levelRequired;

    FlowerSeedType(String seedName, int itemId, int levelRequired) {
        this.seedName = seedName;
        this.itemId = itemId;
        this.levelRequired = levelRequired;
    }

    public boolean canPlant(int farmingLevel) {
        return farmingLevel >= this.levelRequired;
    }

    @Override
    public String toString() {
        return seedName;
    }
}
