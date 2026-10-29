package net.runelite.client.plugins.microbot.FourtifyWoodcutting.data;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtify.core.game.Gate;
import net.runelite.client.plugins.microbot.fourtify.core.game.Gates;
import net.runelite.client.plugins.microbot.fourtify.core.game.Spot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the woodcutting spots from {@code SpotData}, which is generated and must not be edited by hand, with their
 * members flag; members spots are used only on members worlds. Left out: Wilderness spots and spots no player can use
 * (minCombat above 126).
 * <p>
 * One constant per spot, named {@code TYPE_x_y}, with the columns {@code type, town, x, y, plane, radius, minCombat,
 * members, wilderness, trees}; each is typed, so a missing or misplaced column is a compile error. The constants are
 * cut over the enums {@code SpotDataA}, {@code SpotDataB}, ... (one enum's constants are made in one static initializer,
 * which the JVM limits to 64 KB), and {@code SpotData.all()} lists them in table order.
 * <ul>
 * <li>{@code type}: the {@link TreeType} constant.</li>
 * <li>{@code town}: names the spot for logs only.</li>
 * <li>{@code x,y,plane}: the centre of the spot, the open tile nearest the average of its trees from which a tree can
 * be walked to.</li>
 * <li>{@code radius}: the farthest tree from the centre, + 1 tile.</li>
 * <li>{@code minCombat}: 2 x the combat level of the strongest aggressive NPC spawning within 16 tiles of any of the
 * spot's trees, + 1; 0 when there is none; 127 (no player) for level 63 and above. Conditional aggression counts as
 * aggressive. The level-2 giant spider is left out (it did not attack a combat-3 player in a live test), the Port
 * Sarim jail knight cannot leave its cell, and goblins, men and women did not attack in live tests.</li>
 * <li>{@code members}: true for members-only trees or outside the free-to-play area.</li>
 * <li>{@code wilderness}: true in the overworld Wilderness.</li>
 * <li>{@code trees}: how many trees the spot holds.</li>
 * </ul>
 * Access rules of single spots are the constants of {@code GateData}, kept by hand (a column of the generated file would
 * be lost each time it is made again): a type, the x and y of a spot's centre as in {@code SpotData}, and a quest or a
 * skill level, as {@link Gates} describes. A spot with gates is skipped by every picker until the account meets all of
 * them. A gate that names no constant of {@code SpotData} stops the load: the spots were made again and the spot moved.
 * Rules that apply to every spot of a tree type are the tree's own gates ({@link TreeType#gates()}) instead.
 * <p>
 * Some spots are reachable but give nothing without quest progress. The Miscellania maples (MAPLE spots at 2547,3868
 * and 2554,3861) can only be chopped once Throne of Miscellania has reached a certain point, and the kingdom keeps
 * their logs. The Etceteria teak and mahogany trees (TEAK spots at 2601,3896 and 2593,3892, MAHOGANY spots at 2610,3892
 * and 2597,3894) can only be chopped once Royal Trouble is complete, and the kingdom keeps their logs too. Before
 * that, the click does nothing. The evergreen trees on both islands are not part of the kingdoms and chop as usual.
 * The gates of those spots name the quests, but not the point of Throne of Miscellania or the kingdom's approval, which
 * the plugin cannot read, so such a spot is still found out by a click that does nothing, and {@code ChopTree} drops
 * it for the run.
 */
public final class Spots {
    private static final Map<TreeType, List<Spot>> SPOTS = load();

    private Spots() {
    }

    public static List<Spot> forTree(TreeType tree) {
        return SPOTS.getOrDefault(tree, Collections.emptyList());
    }

    /** The largest radius of any spot, the most the picked spot's trees can be from its centre. */
    public static int maxRadius() {
        return SPOTS.values().stream().flatMap(List::stream).mapToInt(Spot::getRadius).max().orElse(0);
    }

    private static Map<TreeType, List<Spot>> load() {
        Map<TreeType, List<Spot>> spots = new EnumMap<>(TreeType.class);
        Map<String, List<Gate>> gates = Gates.load(GateData.values());
        Set<String> keys = new HashSet<>();
        for (SpotRow row : SpotData.all()) {
            int x = row.getX();
            int y = row.getY();
            String key = Gates.key(row.getType().name(), x, y);
            keys.add(key);
            if (row.isWilderness() || row.getMinCombat() > 126) {
                continue;
            }
            int plane = row.getPlane();
            String name = row.getTown() + " (" + x + ", " + y + (plane > 0 ? ", floor " + plane : "") + ")";
            spots.computeIfAbsent(row.getType(), t -> new ArrayList<>()).add(new Spot(name,
                    new WorldPoint(x, y, plane), row.getRadius(), row.getMinCombat(), row.isMembers(),
                    gates.getOrDefault(key, Collections.emptyList())));
        }
        if (!keys.containsAll(gates.keySet())) {
            Set<String> unknown = new HashSet<>(gates.keySet());
            unknown.removeAll(keys);
            throw new IllegalStateException("woodcutting GateData names spots that are not in SpotData: " + unknown);
        }
        return spots;
    }
}
