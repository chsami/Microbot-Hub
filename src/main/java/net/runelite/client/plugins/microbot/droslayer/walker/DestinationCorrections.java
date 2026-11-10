package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Corrections for legacy destination pins, shared by every caller of Efficient Walker.
 * These change the destination, never collision flags: a blocked tile is not made walkable.
 * Nearby pins use the bundled collision map; room/plane corrections use maintained NPC and
 * transport data. Access requirements are still enforced by the ordinary route planner.
 */
public final class DestinationCorrections {
    public static final WorldPoint RIMMINGTON_HOUSE_APPROACH = new WorldPoint(2954, 3223, 0);
    private static final Map<WorldPoint, WorldPoint> CORRECTIONS;
    static {
        Map<WorldPoint, WorldPoint> map = new HashMap<>();
        // Northern Prif map icon duplicates the physical exterior grove at a display offset.
        // The recording confirms the grove outside the north gate, not a walkable city extension.
        add(map, 3233, 6179, 0, 2209, 3427, 0);
        // Advertisement-board footprint is blocked. The adjacent portal approach is walkable.
        map.put(new WorldPoint(2952, 3221, 0), RIMMINGTON_HOUSE_APPROACH);
        add(map, 1697, 10077, 0, 1696, 10078, 0); // CATACOMBS_NECHRYAEL
        add(map, 1678, 10086, 0, 1679, 10085, 0); // CATACOMBS_ABYSSAL_DEMONS
        add(map, 1650, 10084, 0, 1643, 9996, 0); // CATACOMBS_ANKOU
        add(map, 3417, 9932, 0, 3417, 9932, 3); // SLAYER_TOWER_BASEMENT
        add(map, 2431, 9806, 0, 2429, 9806, 0); // STRONGHOLD_CAVE
        add(map, 2467, 9805, 0, 2469, 9803, 0); // STRONGHOLD_CAVE_GREATER_DEMONS
        add(map, 2466, 9823, 0, 2468, 9821, 0); // STRONGHOLD_CAVE_BLACK_DEMONS
        add(map, 2446, 9829, 0, 2445, 9830, 0); // STRONGHOLD_CAVE_DAGANNOTH
        add(map, 2393, 9806, 0, 2391, 9807, 0); // STRONGHOLD_CAVE_ANKOU
        add(map, 1310, 10188, 0, 1311, 10189, 0); // KARUULM
        add(map, 2717, 9477, 0, 2715, 9479, 0); // BRIMHAVEN_BLACK_DRAGONS
        add(map, 2683, 9523, 0, 2703, 9522, 0); // BRIMHAVEN_RED_DRAGONS
        add(map, 2690, 9466, 0, 2702, 9447, 0); // BRIMHAVEN_METAL_DRAGONS
        add(map, 2861, 9822, 0, 2862, 9821, 0); // TAVERLEY_BLACK_DRAGONS
        add(map, 2874, 9847, 0, 2873, 9847, 0); // TAVERLEY_HELLHOUNDS
        add(map, 1743, 5329, 0, 1778, 5343, 1);
        add(map, 1745, 5327, 0, 1778, 5343, 1); // ANCIENT_CAVERN_MITHRIL_DRAGONS
        add(map, 2717, 10016, 0, 2718, 10015, 0); // FREMENNIK_CAVE_TUROTH
        add(map, 3315, 9499, 0, 3316, 9500, 0); // KALPHITE_CAVE
        add(map, 2389, 4435, 0, 2391, 4437, 0); // ZANARIS
        add(map, 2521, 3740, 0, 2523, 3739, 0); // WATERBIRTH_ISLAND
        add(map, 2899, 4449, 0, 2900, 4448, 0); // DAGANNOTH_KINGS
        add(map, 1310, 1251, 0, 1309, 1250, 0); // CERBERUS_LAIR
        add(map, 2378, 9452, 0, 2379, 9452, 0); // THERMONUCLEAR_SMOKE_DEVIL
        add(map, 1991, 4638, 0, 1991, 4641, 0); // MOURNER_TUNNELS
        add(map, 3715, 5765, 0, 3673, 9837, 0); // ARAXYTE_CAVE
        add(map, 3028, 9549, 0, 3029, 9548, 0); // ASGARNIA_ICE_DUNGEON_WYVERNS
        add(map, 2871, 5270, 2, 2870, 5269, 2); // GOD_WARS_AVIANSIES
        add(map, 2926, 10455, 0, 2925, 10456, 0); // GRIMSTONE_DUNGEON
        // Do NOT remap the user-verified Deep Wilderness Dungeon Hill Giants
        // cannon tile (3044, 10318, 0). It is an exact player-action tile,
        // not a legacy map pin. An earlier correction silently rerouted every
        // Walker request to (3042, 10319, 0), preventing cannon placement.
        // Karuulm upper-floor map pins are displayed at an offset from their world tiles.
        add(map, 1298, 10188, 0, 1307, 10232, 1); // Drakes
        add(map, 1300, 10158, 0, 1336, 10182, 1); // Sulphur lizards
        // Chasm task pins corrected against live floor/tile captures.
        add(map, 1435, 10076, 0, 1439, 10074, 2);
        add(map, 1451, 10083, 0, 1450, 10069, 1);
        add(map, 1419, 10072, 0, 1435, 10074, 0);
        add(map, 1419, 10095, 0, 1439, 10074, 2);
        // Herbiboar starts, searches and tunnels: adjacent floor verified against collision map.
        add(map, 3667, 3862, 0, 3666, 3861, 0);
        add(map, 3668, 3865, 0, 3667, 3864, 0);
        add(map, 3670, 3889, 0, 3669, 3888, 0);
        add(map, 3672, 3890, 0, 3671, 3889, 0);
        add(map, 3680, 3836, 0, 3680, 3835, 0);
        add(map, 3680, 3838, 0, 3679, 3839, 0); // Live arrival verified; south tile blocked.
        add(map, 3681, 3859, 0, 3680, 3858, 0);
        add(map, 3681, 3860, 0, 3680, 3861, 0);
        add(map, 3681, 3863, 0, 3680, 3862, 0);
        add(map, 3685, 3869, 0, 3684, 3868, 0);
        add(map, 3686, 3870, 0, 3685, 3870, 0);
        add(map, 3693, 3798, 0, 3693, 3799, 0);
        add(map, 3694, 3847, 0, 3693, 3846, 0);
        add(map, 3695, 3800, 0, 3694, 3799, 0);
        add(map, 3697, 3875, 0, 3696, 3874, 0);
        add(map, 3698, 3847, 0, 3697, 3846, 0);
        add(map, 3699, 3875, 0, 3698, 3874, 0);
        add(map, 3700, 3877, 0, 3699, 3876, 0);
        add(map, 3702, 3808, 0, 3701, 3808, 0);
        add(map, 3703, 3826, 0, 3702, 3827, 0);
        add(map, 3704, 3810, 0, 3703, 3809, 0);
        add(map, 3705, 3830, 0, 3704, 3829, 0);
        add(map, 3706, 3811, 0, 3705, 3810, 0);
        add(map, 3708, 3876, 0, 3707, 3875, 0);
        add(map, 3710, 3877, 0, 3709, 3876, 0);
        add(map, 3710, 3881, 0, 3709, 3880, 0);
        add(map, 3713, 3840, 0, 3712, 3839, 0);
        add(map, 3713, 3850, 0, 3712, 3849, 0);
        add(map, 3715, 3840, 0, 3714, 3839, 0);
        add(map, 3715, 3851, 0, 3714, 3850, 0);
        add(map, 3728, 3893, 0, 3727, 3892, 0);
        add(map, 3751, 3849, 0, 3750, 3848, 0);
        add(map, 3751, 3850, 0, 3750, 3849, 0);
        // Vale Totems: original site pins map to the exact player-supplied tiles.
        // The script now uses these same destinations directly; no collision overrides.
        add(map, 1398, 3329, 0, 1396, 3329, 0);
        add(map, 1413, 3286, 0, 1412, 3287, 0);
        add(map, 1385, 3274, 0, 1384, 3274, 0);
        add(map, 1346, 3319, 0, 1347, 3319, 0);
        add(map, 1370, 3375, 0, 1369, 3373, 0);
        add(map, 1453, 3341, 0, 1450, 3343, 0);
        add(map, 1477, 3332, 0, 1476, 3332, 0);
        add(map, 1438, 3305, 0, 1437, 3305, 0);
        add(map, 1414, 3353, 0, 1413, 3353, 0); // Vale bank standing tile
        // Original Pandemonium request is blocked; adjacent approach is connected to the dock.
        add(map, 3062, 2985, 0, 3061, 2986, 0);
        CORRECTIONS = Collections.unmodifiableMap(map);
    }

    private DestinationCorrections() {}

    private static void add(Map<WorldPoint, WorldPoint> map, int x, int y, int plane,
                            int correctedX, int correctedY, int correctedPlane) {
        map.put(new WorldPoint(x, y, plane), new WorldPoint(correctedX, correctedY, correctedPlane));
    }

    public static WorldPoint resolve(WorldPoint requested) {
        return CORRECTIONS.getOrDefault(requested, requested);
    }
}
