package net.runelite.client.plugins.microbot.FourtifyWoodcutting.data;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The published chance that a woodcutting roll gives a log, from {@code ChanceData}, which is generated and must not
 * be edited by hand: for each tree and axe (by its chop chance in {@link Axe}), one value in 256ths per Woodcutting
 * level. The values are looked up, not computed, because the chance does not follow one simple rule across trees and
 * axes.
 * <p>
 * One constant per tree and axe, named {@code TREE_chopChance}, with the columns
 * {@code type, chopChance, firstLevel, chances...}; each is typed, so a missing or misplaced column is a compile error.
 * <ul>
 * <li>{@code type}: the {@link TreeType} constant.</li>
 * <li>{@code chopChance}: the axe's chop chance in {@link Axe} (bronze 1000).</li>
 * <li>{@code firstLevel}: the first Woodcutting level of the chart.</li>
 * <li>{@code chances...}: the chance in 256ths that a woodcutting roll gives a log at that level and at each level
 * above it. The last value holds for all higher levels: the charts stop at the first level with chance 256, or at
 * 99.</li>
 * </ul>
 */
final class Chances {
    /** The table per tree, by the axe's chop chance; each row is the first level, then the chances from there. */
    private static final Map<TreeType, TreeMap<Integer, int[]>> ROWS = load();

    private Chances() {
    }

    /**
     * Chance in 256ths at a level (1 to 99; a level below the first of the row takes its first value, and one above
     * the last its last value) for the best row of an axe at most as good as the given chop chance, so an axe with a
     * chop chance between two rows counts as the lower one.
     */
    static int in256(TreeType tree, int woodcuttingLevel, int axeChopChance) {
        TreeMap<Integer, int[]> rows = ROWS.get(tree);
        Map.Entry<Integer, int[]> row = rows.floorEntry(axeChopChance);
        int[] values = (row != null ? row : rows.firstEntry()).getValue();
        int at = Math.max(0, Math.min(values.length - 2, woodcuttingLevel - values[0]));
        return values[1 + at];
    }

    private static Map<TreeType, TreeMap<Integer, int[]>> load() {
        Map<TreeType, TreeMap<Integer, int[]>> rows = new EnumMap<>(TreeType.class);
        for (ChanceData data : ChanceData.values()) {
            int[] row = new int[1 + data.getChances().length];
            row[0] = data.getFirstLevel();
            System.arraycopy(data.getChances(), 0, row, 1, data.getChances().length);
            rows.computeIfAbsent(data.getType(), t -> new TreeMap<>()).put(data.getChopChance(), row);
        }
        return rows;
    }
}
