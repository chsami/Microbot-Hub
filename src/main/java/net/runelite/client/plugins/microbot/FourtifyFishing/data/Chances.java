package net.runelite.client.plugins.microbot.FourtifyFishing.data;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The published chance that the roll of one fish succeeds, from {@code ChanceData}, which is generated and must not
 * be edited by hand: for each method, fish and tool (a harpoon's catch rate in {@link Harpoon}), one value per Fishing
 * level from the fish's first level. The values are looked up, not computed, because they come from a low and a high
 * value per fish that the game rounds to 256ths level by level.
 * <p>
 * One constant per method, fish and tool, named {@code METHOD_ITEM_variant}, with the columns
 * {@code method, item, variant, firstLevel, chances...}.
 * <ul>
 * <li>{@code method}: the {@link FishingMethod} constant.</li>
 * <li>{@code item}: the fish's constant in {@code ItemID}, so a wrong name does not compile.</li>
 * <li>{@code variant}: the catch rate of the harpoon in {@link Harpoon} (100 plain, 120 dragon, 135 crystal); 100 for
 * everything else.</li>
 * <li>{@code firstLevel}: the first Fishing level of the chart.</li>
 * <li>{@code chances...}: the chance that the fish's own roll succeeds at that level and at each level above it, each
 * a whole number of 256ths written as the exact decimal (the big net's mackerel is the chance of at least one of two
 * rolls). The last value holds for all higher levels: the charts stop at 99, or at the first level with chance 1.</li>
 * </ul>
 * Each constant is typed, so a missing or misplaced column is a compile error and the loader parses nothing.
 * Where a spot offers several fish, the better fish is rolled first and the next only when that fails, so the chart's
 * chance of a fish is its own roll times the chance that every better fish failed. The rows hold the own rolls, and
 * the plugin builds that cascade from the fish a player qualifies for.
 */
final class Chances {
    /** First level and the chances from there, by method, item and tool variant. */
    private static final Map<String, TreeMap<Integer, Row>> ROWS = load();

    private static final class Row {
        final int firstLevel;
        final double[] chances;

        Row(int firstLevel, double[] chances) {
            this.firstLevel = firstLevel;
            this.chances = chances;
        }
    }

    private Chances() {
    }

    private static String key(String method, int itemId) {
        return method + "," + itemId;
    }

    /**
     * The chance at a level (a level above the last of the row takes its last value; below the first level of the
     * fish the chance is 0) for the best tool variant at most as good as the given catch rate, so a rate between two
     * variants counts as the lower one.
     */
    static double roll(FishingMethod method, int itemId, int variant, int fishingLevel) {
        Row row = row(method, itemId, variant);
        if (fishingLevel < row.firstLevel) {
            return 0;
        }
        return row.chances[Math.min(row.chances.length - 1, fishingLevel - row.firstLevel)];
    }

    static int firstLevel(FishingMethod method, int itemId) {
        return row(method, itemId, 0).firstLevel;
    }

    private static Row row(FishingMethod method, int itemId, int variant) {
        TreeMap<Integer, Row> variants = ROWS.get(key(method.name(), itemId));
        if (variants == null) {
            throw new IllegalArgumentException(method + " has no chances for item " + itemId);
        }
        Map.Entry<Integer, Row> row = variants.floorEntry(variant);
        return (row != null ? row : variants.firstEntry()).getValue();
    }

    private static Map<String, TreeMap<Integer, Row>> load() {
        Map<String, TreeMap<Integer, Row>> rows = new HashMap<>();
        for (ChanceData data : ChanceData.values()) {
            rows.computeIfAbsent(key(data.getMethod().name(), data.getItem()), k -> new TreeMap<>())
                    .put(data.getVariant(), new Row(data.getFirstLevel(), data.getChances()));
        }
        return rows;
    }
}
