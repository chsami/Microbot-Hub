package net.runelite.client.plugins.microbot.irkedmlm;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;

/**
 * Fixed Motherlode Mine world coordinates and static blocked tiles (map truth only — no gameplay rules).
 */
public final class IrkedMLMMapConstants {

    public static final int UPPER_HUB_MIN_Y = 5670;

    public static final WorldPoint LADDER_BOTTOM_WALK = new WorldPoint(3755, 5673, 0);
    public static final WorldPoint LADDER_TOP_WALK = new WorldPoint(3755, 5675, 0);
    public static final WorldPoint LADDER_BOTTOM_SAFE = new WorldPoint(3755, 5673, 0);

    public static final WorldPoint LOWER_HOPPER_WALK = new WorldPoint(3748, 5672, 0);
    public static final WorldPoint UPPER_HOPPER_WALK = new WorldPoint(3755, 5677, 0);

    public static final WorldPoint SUPPLY_CRATE_POINT = new WorldPoint(3752, 5674, 0);
    public static final WorldPoint DEPOSIT_BOX_LOC = new WorldPoint(3759, 5665, 0);
    public static final WorldPoint WATERWHEEL_AREA = new WorldPoint(3747, 5672, 0);

    public static final int HOPPER_OBJECT_ID = 26674;
    public static final int HOPPER_LOC_TOLERANCE = 2;

    /**
     * Every map region the Motherlode Mine occupies, matching core RuneLite's
     * {@code MotherlodePlugin.MOTHERLODE_MAP_REGIONS}.
     *
     * <p>Why this matters: the sack varbit ({@code MOTHERLODE_SACK_TRANSMIT}) is only meaningful
     * inside the mine. Outside it the value can read 0, which is indistinguishable from an empty
     * sack — so on the walk in, an ungated read says "sack is empty, go mine" when it may be full.
     * Every sack read must be gated on {@link #inMine()}.
     */
    private static final Set<Integer> MOTHERLODE_MAP_REGIONS = Set.of(
            14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193
    );

    /**
     * True when every loaded map region belongs to the Motherlode Mine. Mirrors core's
     * {@code checkInMlm()}: <em>all</em> regions must match, so a partially-loaded scene on the way
     * in reports false rather than true.
     *
     * <p>Must be called on the client thread.
     */
    public static boolean inMine() {
        var client = Microbot.getClient();
        if (client == null) {
            return false;
        }
        GameState state = client.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING) {
            return false;
        }
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null) {
            return false;
        }
        int[] regions = wv.getMapRegions();
        if (regions == null || regions.length == 0) {
            return false;
        }
        for (int region : regions) {
            if (!MOTHERLODE_MAP_REGIONS.contains(region)) {
                return false;
            }
        }
        return true;
    }

    /** The 2x2 rockfall that blocks the corridor between upper west and upper east. */
    public static final Set<WorldPoint> SHARED_UPPER_ROCKFALL_TILES = Set.of(
            new WorldPoint(3757, 5677, 0),
            new WorldPoint(3758, 5677, 0),
            new WorldPoint(3757, 5678, 0),
            new WorldPoint(3758, 5678, 0)
    );

    /** Flanking tiles: approaching the rockfall from either side is blocked for pathing. */
    public static final Set<WorldPoint> SHARED_UPPER_ROCKFALL_APPROACH_TILES = Set.of(
            new WorldPoint(3756, 5677, 0),
            new WorldPoint(3759, 5677, 0),
            new WorldPoint(3756, 5678, 0),
            new WorldPoint(3759, 5678, 0),
            new WorldPoint(3757, 5676, 0),
            new WorldPoint(3758, 5676, 0)
    );

    /** Tiles unreachable behind rockfall on upper-west (static survey). */
    public static final Set<WorldPoint> WEST_UPPER_ROCKFALL_BLOCKED_TILES = buildWestUpperRockfallBlockedTiles();
    /** Tiles unreachable behind rockfall on upper-east (static survey). */
    public static final Set<WorldPoint> EAST_UPPER_ROCKFALL_BLOCKED_TILES = buildEastUpperRockfallBlockedTiles();


    private IrkedMLMMapConstants() {
    }

    public static boolean isLowerMineTunnel(WorldPoint p) {
        return p != null && p.getPlane() == 0 && p.getY() < UPPER_HUB_MIN_Y;
    }

    public static boolean isRockfallBlockedTile(WorldPoint point) {
        if (point == null) {
            return false;
        }
        return WEST_UPPER_ROCKFALL_BLOCKED_TILES.contains(point)
                || EAST_UPPER_ROCKFALL_BLOCKED_TILES.contains(point)
                || SHARED_UPPER_ROCKFALL_TILES.contains(point)
                || SHARED_UPPER_ROCKFALL_APPROACH_TILES.contains(point);
    }

    public static Set<WorldPoint> permanentRockfallBarriersFor(MLMMiningSpot spot) {
        if (spot == MLMMiningSpot.WEST_UPPER) {
            return WEST_UPPER_ROCKFALL_BLOCKED_TILES;
        }
        if (spot == MLMMiningSpot.EAST_UPPER) {
            return EAST_UPPER_ROCKFALL_BLOCKED_TILES;
        }
        return Collections.emptySet();
    }

    private static Set<WorldPoint> buildWestUpperRockfallBlockedTiles() {
        Set<WorldPoint> blocked = new HashSet<>();
        addWestUpperTilesBehindRockfall(blocked);
        addSharedUpperRockfallLaneTiles(blocked);
        return Collections.unmodifiableSet(blocked);
    }

    private static Set<WorldPoint> buildEastUpperRockfallBlockedTiles() {
        Set<WorldPoint> blocked = new HashSet<>();
        addEastUpperTilesBehindRockfall(blocked);
        addSharedUpperRockfallLaneTiles(blocked);
        return Collections.unmodifiableSet(blocked);
    }

    private static void addSharedUpperRockfallLaneTiles(Set<WorldPoint> out) {
        out.addAll(SHARED_UPPER_ROCKFALL_TILES);
        out.addAll(SHARED_UPPER_ROCKFALL_APPROACH_TILES);
    }

    /** North / east face of upper-east chamber — not reachable from operating side. */
    private static void addEastUpperTilesBehindRockfall(Set<WorldPoint> out) {
        out.add(new WorldPoint(3760, 5678, 0));
        out.add(new WorldPoint(3763, 5679, 0));
        out.add(new WorldPoint(3766, 5681, 0));
        out.add(new WorldPoint(3760, 5679, 0));
        out.add(new WorldPoint(3763, 5678, 0));
        out.add(new WorldPoint(3766, 5680, 0));
        out.add(new WorldPoint(3763, 5677, 0));
        out.add(new WorldPoint(3766, 5679, 0));
        out.add(new WorldPoint(3765, 5680, 0));
        out.add(new WorldPoint(3767, 5677, 0));
        out.add(new WorldPoint(3762, 5677, 0));
        out.add(new WorldPoint(3765, 5679, 0));
        out.add(new WorldPoint(3762, 5676, 0));
        out.add(new WorldPoint(3765, 5678, 0));
        out.add(new WorldPoint(3760, 5677, 0));
        out.add(new WorldPoint(3761, 5676, 0));
        out.add(new WorldPoint(3764, 5678, 0));
        out.add(new WorldPoint(3762, 5679, 0));
        out.add(new WorldPoint(3762, 5678, 0));
        out.add(new WorldPoint(3761, 5679, 0));
        out.add(new WorldPoint(3759, 5677, 0));
        out.add(new WorldPoint(3761, 5678, 0));
        out.add(new WorldPoint(3764, 5680, 0));
        out.add(new WorldPoint(3759, 5678, 0));
        out.add(new WorldPoint(3761, 5677, 0));
        out.add(new WorldPoint(3764, 5679, 0));
    }

    /** Central / west face of upper-west chamber — not reachable from operating side. */
    private static void addWestUpperTilesBehindRockfall(Set<WorldPoint> out) {
        out.add(new WorldPoint(3761, 5682, 0));
        out.add(new WorldPoint(3759, 5686, 0));
        out.add(new WorldPoint(3758, 5682, 0));
        out.add(new WorldPoint(3762, 5685, 0));
        out.add(new WorldPoint(3757, 5678, 0));
        out.add(new WorldPoint(3758, 5683, 0));
        out.add(new WorldPoint(3756, 5680, 0));
        out.add(new WorldPoint(3761, 5685, 0));
        out.add(new WorldPoint(3758, 5678, 0));
        out.add(new WorldPoint(3759, 5683, 0));
        out.add(new WorldPoint(3756, 5681, 0));
        out.add(new WorldPoint(3761, 5684, 0));
        out.add(new WorldPoint(3759, 5684, 0));
        out.add(new WorldPoint(3756, 5682, 0));
        out.add(new WorldPoint(3761, 5683, 0));
        out.add(new WorldPoint(3759, 5685, 0));
        out.add(new WorldPoint(3764, 5685, 0));
        out.add(new WorldPoint(3756, 5683, 0));
        out.add(new WorldPoint(3760, 5684, 0));
        out.add(new WorldPoint(3757, 5682, 0));
        out.add(new WorldPoint(3760, 5685, 0));
        out.add(new WorldPoint(3763, 5685, 0));
        out.add(new WorldPoint(3757, 5683, 0));
        out.add(new WorldPoint(3760, 5686, 0));
        out.add(new WorldPoint(3757, 5684, 0));
        out.add(new WorldPoint(3761, 5686, 0));
        out.add(new WorldPoint(3762, 5684, 0));
        out.add(new WorldPoint(3757, 5679, 0));
        out.add(new WorldPoint(3758, 5684, 0));
        out.add(new WorldPoint(3757, 5680, 0));
        out.add(new WorldPoint(3758, 5685, 0));
        out.add(new WorldPoint(3760, 5683, 0));
        out.add(new WorldPoint(3757, 5681, 0));
    }
}
