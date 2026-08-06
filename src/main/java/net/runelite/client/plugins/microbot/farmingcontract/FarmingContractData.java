package net.runelite.client.plugins.microbot.farmingcontract;

import com.google.common.collect.ImmutableMap;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

import java.util.Map;

public final class FarmingContractData {

    static final WorldPoint JANE_LOCATION = new WorldPoint(1248, 3727, 0);
    static final WorldPoint BIN_LOCATION = new WorldPoint(1272, 3729, 0);
    static final int PINEAPPLE = ItemID.PINEAPPLE;
    static final int VOLCANIC_ASH = ItemID.FOSSIL_VOLCANIC_ASH;
    static final int BUCKET_EMPTY = ItemID.BUCKET_EMPTY;
    static final int BUCKET_ULTRACOMPOST = ItemID.BUCKET_ULTRACOMPOST;
    static final int MAGIC_SECATEURS = ItemID.FAIRY_ENCHANTED_SECATEURS;
    static final int VOLCANIC_ASH_PER_BIN = 50;
    static final int BIN_CAPACITY = 30;

    static final Map<PatchImplementation, String> PATCH_NAMES = ImmutableMap.<PatchImplementation, String>builder()
        .put(PatchImplementation.HERB, "Herb patch")
        .put(PatchImplementation.ALLOTMENT, "Allotment")
        .put(PatchImplementation.FLOWER, "Flower Patch")
        .put(PatchImplementation.BUSH, "Bush Patch")
        .put(PatchImplementation.TREE, "Tree patch")
        .put(PatchImplementation.FRUIT_TREE, "Fruit Tree Patch")
        .put(PatchImplementation.CACTUS, "Cactus patch")
        .build();

    static final Map<PatchImplementation, WorldPoint> PATCH_LOCATIONS = ImmutableMap.<PatchImplementation, WorldPoint>builder()
        .put(PatchImplementation.HERB, new WorldPoint(1238, 3726, 0))
        .put(PatchImplementation.ALLOTMENT, new WorldPoint(1267, 3727, 0))
        .put(PatchImplementation.FLOWER, new WorldPoint(1260, 3725, 0))
        .put(PatchImplementation.BUSH, new WorldPoint(1260, 3733, 0))
        .put(PatchImplementation.TREE, new WorldPoint(1231, 3735, 0))
        .put(PatchImplementation.FRUIT_TREE, new WorldPoint(1242, 3758, 0))
        .put(PatchImplementation.CACTUS, new WorldPoint(1264, 3747, 0))
        .build();

    static final Map<Produce, Integer> SEED_MAPPINGS = ImmutableMap.<Produce, Integer>builder()
        // Allotments
        .put(Produce.POTATO, ItemID.POTATO_SEED)
        .put(Produce.ONION, ItemID.ONION_SEED)
        .put(Produce.CABBAGE, ItemID.CABBAGE_SEED)
        .put(Produce.TOMATO, ItemID.TOMATO_SEED)
        .put(Produce.SWEETCORN, ItemID.SWEETCORN_SEED)
        .put(Produce.STRAWBERRY, ItemID.STRAWBERRY_SEED)
        .put(Produce.WATERMELON, ItemID.WATERMELON_SEED)
        .put(Produce.SNAPE_GRASS, ItemID.SNAPE_GRASS_SEED)
        // Flowers
        .put(Produce.MARIGOLD, ItemID.MARIGOLD_SEED)
        .put(Produce.ROSEMARY, ItemID.ROSEMARY_SEED)
        .put(Produce.NASTURTIUM, ItemID.NASTURTIUM_SEED)
        .put(Produce.WOAD, ItemID.WOAD_SEED)
        .put(Produce.LIMPWURT, ItemID.LIMPWURT_SEED)
        .put(Produce.WHITE_LILY, ItemID.WHITE_LILY_SEED)
        // Herbs
        .put(Produce.GUAM, ItemID.GUAM_SEED)
        .put(Produce.MARRENTILL, ItemID.MARRENTILL_SEED)
        .put(Produce.TARROMIN, ItemID.TARROMIN_SEED)
        .put(Produce.HARRALANDER, ItemID.HARRALANDER_SEED)
        .put(Produce.RANARR, ItemID.RANARR_SEED)
        .put(Produce.TOADFLAX, ItemID.TOADFLAX_SEED)
        .put(Produce.IRIT, ItemID.IRIT_SEED)
        .put(Produce.AVANTOE, ItemID.AVANTOE_SEED)
        .put(Produce.KWUARM, ItemID.KWUARM_SEED)
        .put(Produce.SNAPDRAGON, ItemID.SNAPDRAGON_SEED)
        .put(Produce.CADANTINE, ItemID.CADANTINE_SEED)
        .put(Produce.LANTADYME, ItemID.LANTADYME_SEED)
        .put(Produce.DWARF_WEED, ItemID.DWARF_WEED_SEED)
        .put(Produce.TORSTOL, ItemID.TORSTOL_SEED)
        // Trees (saplings)
        .put(Produce.OAK, ItemID.PLANTPOT_OAK_SAPLING)
        .put(Produce.WILLOW, ItemID.PLANTPOT_WILLOW_SAPLING)
        .put(Produce.MAPLE, ItemID.PLANTPOT_MAPLE_SAPLING)
        .put(Produce.YEW, ItemID.PLANTPOT_YEW_SAPLING)
        .put(Produce.MAGIC, ItemID.PLANTPOT_MAGIC_TREE_SAPLING)
        // Fruit trees (saplings)
        .put(Produce.APPLE, ItemID.PLANTPOT_APPLE_SAPLING)
        .put(Produce.BANANA, ItemID.PLANTPOT_BANANA_SAPLING)
        .put(Produce.ORANGE, ItemID.PLANTPOT_ORANGE_SAPLING)
        .put(Produce.CURRY, ItemID.PLANTPOT_CURRY_SAPLING)
        .put(Produce.PINEAPPLE, ItemID.PLANTPOT_PINEAPPLE_SAPLING)
        .put(Produce.PAPAYA, ItemID.PLANTPOT_PAPAYA_SAPLING)
        .put(Produce.PALM, ItemID.PLANTPOT_PALM_SAPLING)
        .put(Produce.DRAGONFRUIT, ItemID.PLANTPOT_DRAGONFRUIT_SAPLING)
        // Bushes
        .put(Produce.REDBERRIES, ItemID.REDBERRY_BUSH_SEED)
        .put(Produce.CADAVABERRIES, ItemID.CADAVABERRY_BUSH_SEED)
        .put(Produce.DWELLBERRIES, ItemID.DWELLBERRY_BUSH_SEED)
        .put(Produce.JANGERBERRIES, ItemID.JANGERBERRY_BUSH_SEED)
        .put(Produce.WHITEBERRIES, ItemID.WHITEBERRY_BUSH_SEED)
        .put(Produce.POISON_IVY, ItemID.POISONIVY_BUSH_SEED)
        // Cactus
        .put(Produce.CACTUS, ItemID.CACTUS_SEED)
        .put(Produce.POTATO_CACTUS, ItemID.POTATO_CACTUS_SEED)
        .build();

    static final Map<Produce, int[]> PROTECTION_PAYMENTS = ImmutableMap.<Produce, int[]>builder()
        // Trees
        .put(Produce.OAK, new int[]{ItemID.BASKET_TOMATO_5, 1})
        .put(Produce.WILLOW, new int[]{ItemID.BASKET_APPLE_5, 1})
        .put(Produce.MAPLE, new int[]{ItemID.BASKET_ORANGE_5, 1})
        .put(Produce.YEW, new int[]{ItemID.CACTUS_SPINE, 10})
        .put(Produce.MAGIC, new int[]{ItemID.COCONUT, 25})
        // Fruit trees
        .put(Produce.APPLE, new int[]{ItemID.SWEETCORN, 9})
        .put(Produce.BANANA, new int[]{ItemID.BASKET_APPLE_5, 4})
        .put(Produce.ORANGE, new int[]{ItemID.BASKET_STRAWBERRY_5, 3})
        .put(Produce.CURRY, new int[]{ItemID.BASKET_BANANA_5, 5})
        .put(Produce.PINEAPPLE, new int[]{ItemID.WATERMELON, 10})
        .put(Produce.PAPAYA, new int[]{ItemID.PINEAPPLE, 10})
        .put(Produce.PALM, new int[]{ItemID.PAPAYA, 15})
        .put(Produce.DRAGONFRUIT, new int[]{ItemID.COCONUT, 15})
        .build();

    static int getProtectionItemId(Produce produce) {
        int[] payment = PROTECTION_PAYMENTS.get(produce);
        return payment != null ? payment[0] : -1;
    }

    static int getProtectionItemQty(Produce produce) {
        int[] payment = PROTECTION_PAYMENTS.get(produce);
        return payment != null ? payment[1] : 0;
    }

    static boolean hasProtectionData(Produce produce) {
        return PROTECTION_PAYMENTS.containsKey(produce);
    }

    static int getSeedId(Produce produce) {
        return SEED_MAPPINGS.getOrDefault(produce, -1);
    }

    static int getSeedsRequired(Produce produce) {
        return produce.getPatchImplementation() == PatchImplementation.ALLOTMENT ? 3 : 1;
    }

    static boolean needsCoins(Produce produce) {
        PatchImplementation type = produce.getPatchImplementation();
        return type == PatchImplementation.TREE || type == PatchImplementation.FRUIT_TREE;
    }

    static boolean usesSecateurs(PatchImplementation type) {
        return type == PatchImplementation.ALLOTMENT
            || type == PatchImplementation.HERB
            || type == PatchImplementation.FLOWER
            || type == PatchImplementation.BUSH;
    }

    static boolean needsClearAfterHarvest(PatchImplementation type) {
        return type == PatchImplementation.BUSH
            || type == PatchImplementation.CACTUS;
    }

    static boolean needsCheckHealth(PatchImplementation type) {
        return type == PatchImplementation.TREE
            || type == PatchImplementation.FRUIT_TREE
            || type == PatchImplementation.BUSH
            || type == PatchImplementation.CACTUS;
    }

    private FarmingContractData() {}
}
