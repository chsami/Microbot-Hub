package net.runelite.client.plugins.microbot.FourtifyFishing.data;

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
 * Loads the fishing spots from {@code SpotData}, which is generated and must not be edited by hand, by kind, with
 * their members flag; members spots are used only on members worlds. Left out: Wilderness spots and spots no player
 * can use (minCombat above 126).
 * <p>
 * One constant per spot, named {@code TYPE_x_y}, with the columns {@code type, town, x, y, plane, radius, minCombat,
 * members, wilderness, spots}; each is typed, so a missing or misplaced column is a compile error.
 * <ul>
 * <li>{@code type}: the {@link SpotKind} constant.</li>
 * <li>{@code town}: names the spot for logs only.</li>
 * <li>{@code x,y,plane}: the centre of the spot, an open tile beside a fishing spot with a walk to a bank.</li>
 * <li>{@code radius}: the distance from the centre within which the spot's fishing spots are found, the farthest
 * spawn plus 12 tiles because the fishing spots move (measured in a live run).</li>
 * <li>{@code minCombat}: 2 x the combat level of the strongest aggressive NPC spawning within 16 tiles of any of the
 * spot's fishing spots, + 1; 0 when there is none; 127 (no player) for level 63 and above. Conditional aggression
 * counts as aggressive. Goblins, men and women did not attack in live tests, and the level-2 giant spider is left
 * out.</li>
 * <li>{@code members}: true outside the free-to-play mainland (Karamja counts, since it takes the boat; only Musa
 * Point's cage and harpoon spots are free-to-play).</li>
 * <li>{@code wilderness}: true in the overworld Wilderness.</li>
 * <li>{@code spots}: how many fishing spots the spot holds.</li>
 * </ul>
 * Access rules of single spots are the constants of {@code GateData}, kept by hand (a column of the generated file would
 * be lost each time it is made again): a type, the x and y of a spot's centre as in {@code SpotData}, and a quest or a
 * skill level, as {@link Gates} describes. A spot with gates is skipped by every picker until the account meets all of
 * them. A gate that names no constant of {@code SpotData} stops the load: the spots were made again and the spot moved.
 * Rules that apply to a whole method are the method's own requirements instead.
 */
public final class Spots {
    private static final Map<SpotKind, List<Spot>> SPOTS = load();

    private Spots() {
    }

    public static List<Spot> of(SpotKind kind) {
        return SPOTS.getOrDefault(kind, Collections.emptyList());
    }

    /** The largest radius of any spot. */
    public static int largestRadius() {
        return SPOTS.values().stream().flatMap(List::stream).mapToInt(Spot::getRadius).max().orElse(0);
    }

    private static Map<SpotKind, List<Spot>> load() {
        Map<SpotKind, List<Spot>> spots = new EnumMap<>(SpotKind.class);
        Map<String, List<Gate>> gates = Gates.load(GateData.values());
        Set<String> keys = new HashSet<>();
        for (SpotData row : SpotData.values()) {
            int x = row.getX();
            int y = row.getY();
            String key = Gates.key(row.getType().name(), x, y);
            keys.add(key);
            if (row.isWilderness() || row.getMinCombat() > 126) {
                continue;
            }
            int plane = row.getPlane();
            String name = row.getTown() + " (" + x + ", " + y + (plane > 0 ? ", floor " + plane : "") + ")";
            spots.computeIfAbsent(row.getType(), k -> new ArrayList<>()).add(new Spot(name,
                    new WorldPoint(x, y, plane), row.getRadius(), row.getMinCombat(), row.isMembers(),
                    gates.getOrDefault(key, Collections.emptyList())));
        }
        if (!keys.containsAll(gates.keySet())) {
            Set<String> unknown = new HashSet<>(gates.keySet());
            unknown.removeAll(keys);
            throw new IllegalStateException("fishing GateData names spots that are not in SpotData: " + unknown);
        }
        return spots;
    }
}
