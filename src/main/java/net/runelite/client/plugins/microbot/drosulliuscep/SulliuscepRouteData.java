/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.api.coords.WorldPoint;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User-recording-derived Sulliuscep route.
 *
 * The script owns Ferox/Fossil Island/Magic Mushtree travel; WebWalk handles approaches.
 * Once the player lands at Sticky Swamp, the six-tree course is owned locally:
 * active tree -> exact recorded obstacle sequence -> next active tree.
 */
public final class SulliuscepRouteData
{
    public static final int VARBIT_ACTIVE_SULLIUSCEP = 5808;
    public static final int VARBIT_PIT_FILLED = 5809;

    public static final String SULLIUSCEP_NAME = "Sulliuscep";

    // Recorder IDs are retained only as secondary/fallback identifiers. Tree clicks are
    // primarily resolved by the visible name "Sulliuscep" + action "Cut" near the anchor.
    public static final int PIT_OBJECT_ID = 31426;
    public static final int RUBBER_CAP_MUSHROOM_OBJECT_ID = 30606;
    public static final int VINES_OBJECT_ID = 30644;
    public static final int THICK_VINE_OBJECT_ID = 30646;
    public static final int THICK_VINES_OBJECT_ID = 30648;
    public static final int MAGIC_MUSHTREE_HOUSE_OBJECT_ID = 30920;
    public static final int MAGIC_MUSHTREE_OTHER_OBJECT_ID = 30924;
    public static final int POOL_OF_REFRESHMENT_OBJECT_ID = 39651;
    public static final int FEROX_BANK_CHEST_OBJECT_ID = 26711;

    public static final WorldPoint FEROX_LANDING = new WorldPoint(3150, 3636, 0);
    public static final WorldPoint FEROX_POOL = new WorldPoint(3128, 3636, 0);
    public static final WorldPoint FEROX_POOL_APPROACH = new WorldPoint(3142, 3630, 0);
    public static final WorldPoint FEROX_BANK = new WorldPoint(3130, 3632, 0);          // chest/object tile
    public static final WorldPoint FEROX_BANK_APPROACH = new WorldPoint(3131, 3632, 0); // recorded standable bank tile

    public static final WorldPoint FOSSIL_ISLAND_LANDING = new WorldPoint(3765, 3869, 1);
    public static final WorldPoint HOUSE_MUSHTREE = new WorldPoint(3765, 3879, 1);
    public static final WorldPoint HOUSE_MUSHTREE_APPROACH = new WorldPoint(3765, 3878, 1);
    public static final WorldPoint STICKY_SWAMP_MUSHTREE = new WorldPoint(3676, 3755, 0);
    // User-supplied Object-ID overlay: Rubber cap mushroom 30606 at X:3664 Y:3800.
    public static final WorldPoint RUBBER_CAP_MUSHROOM = new WorldPoint(3664, 3800, 0);

    /**
     * Six active trees in varbit 5808 order. Anchors come from the supplied recording/helper data;
     * recovery tiles are standable positions observed immediately before/around successful Cuts.
     */
    public static final List<TreeSpot> TREES = List.of(
            new TreeSpot(0, 31420, new WorldPoint(3683, 3758, 0), new WorldPoint(3684, 3757, 0)),
            new TreeSpot(1, 31421, new WorldPoint(3678, 3733, 0), new WorldPoint(3679, 3732, 0)),
            new TreeSpot(2, 31422, new WorldPoint(3683, 3775, 0), new WorldPoint(3672, 3763, 0)),
            new TreeSpot(3, 31423, new WorldPoint(3663, 3781, 0), new WorldPoint(3674, 3770, 0)),
            new TreeSpot(4, 31424, new WorldPoint(3663, 3802, 0), new WorldPoint(3672, 3800, 0)),
            new TreeSpot(5, 31425, new WorldPoint(3678, 3806, 0), new WorldPoint(3665, 3803, 0))
    );

    /**
     * Exact obstacle chain supplied by the user's screenshots and original recording.
     * activeTreeIndex means "this obstacle is on the route TO this currently-active tree".
     *
     *  #1 -> #2: 30646 (3678,3743)
     *  #2 -> #3: 30648 (3669,3746), (3671,3760), (3672,3764)
     *  #3 -> #4: 30644 (3674,3771) Clear/rake
     *  #4 -> #5: 30648 (3666,3788), (3670,3792), 30646 (3672,3801)
     *  #5 -> #6: direct, no obstacle
     *
     * Pass thresholds deliberately require the player to be one tile BEYOND the obstacle.
     * The prior build treated "standing on the obstacle's coordinate" as passed, which could
     * skip the Chop/Clear while the vine was still blocking movement.
     */
    public static final List<RouteObstacle> OBSTACLES = List.of(
            new RouteObstacle(THICK_VINE_OBJECT_ID, "Thick vine", new WorldPoint(3678, 3743, 0), 1,
                    "Chop", "Thick vine #1", new WorldPoint(3678, 3741, 0), PassRule.Y_LE, 3742),

            new RouteObstacle(THICK_VINES_OBJECT_ID, "Thick vines", new WorldPoint(3669, 3746, 0), 2,
                    "Chop", "Thick vines #2", new WorldPoint(3669, 3749, 0), PassRule.Y_GE, 3747),
            new RouteObstacle(THICK_VINES_OBJECT_ID, "Thick vines", new WorldPoint(3671, 3760, 0), 2,
                    "Chop", "Thick vines #3", new WorldPoint(3671, 3762, 0), PassRule.Y_GE, 3761),
            new RouteObstacle(THICK_VINES_OBJECT_ID, "Thick vines", new WorldPoint(3672, 3764, 0), 2,
                    "Chop", "Thick vines #4", new WorldPoint(3672, 3766, 0), PassRule.Y_GE, 3765),

            new RouteObstacle(VINES_OBJECT_ID, "Vines", new WorldPoint(3674, 3771, 0), 3,
                    "Clear", "Rake vines #5", new WorldPoint(3672, 3771, 0), PassRule.X_LE, 3673),

            new RouteObstacle(THICK_VINES_OBJECT_ID, "Thick vines", new WorldPoint(3666, 3788, 0), 4,
                    "Chop", "Thick vines #6", new WorldPoint(3666, 3790, 0), PassRule.Y_GE, 3789),
            new RouteObstacle(THICK_VINES_OBJECT_ID, "Thick vines", new WorldPoint(3670, 3792, 0), 4,
                    "Chop", "Thick vines #7", new WorldPoint(3670, 3794, 0), PassRule.Y_GE, 3793),
            new RouteObstacle(THICK_VINE_OBJECT_ID, "Thick vine", new WorldPoint(3672, 3801, 0), 4,
                    "Chop", "Thick vine #8", new WorldPoint(3672, 3803, 0), PassRule.Y_GE, 3802)
    );

    private SulliuscepRouteData() {}

    public static TreeSpot tree(int index)
    {
        return index >= 0 && index < TREES.size() ? TREES.get(index) : TREES.get(0);
    }

    public static List<RouteObstacle> obstaclesForTree(int activeTreeIndex)
    {
        return OBSTACLES.stream()
                .filter(o -> o.activeTreeIndex() == activeTreeIndex)
                .collect(Collectors.toList());
    }

    public enum PassRule
    {
        X_LE,
        X_GE,
        Y_LE,
        Y_GE;

        public boolean passed(WorldPoint player, int value)
        {
            if (player == null)
            {
                return false;
            }
            switch (this)
            {
                case X_LE: return player.getX() <= value;
                case X_GE: return player.getX() >= value;
                case Y_LE: return player.getY() <= value;
                case Y_GE: return player.getY() >= value;
                default: return false;
            }
        }
    }

    public static final class TreeSpot
    {
        private final int index;
        private final int recordedObjectId;
        private final WorldPoint location;
        private final WorldPoint recoveryTile;

        public TreeSpot(int index, int recordedObjectId, WorldPoint location, WorldPoint recoveryTile)
        {
            this.index = index;
            this.recordedObjectId = recordedObjectId;
            this.location = location;
            this.recoveryTile = recoveryTile;
        }

        public int index() { return index; }
        public int recordedObjectId() { return recordedObjectId; }
        public WorldPoint location() { return location; }
        public WorldPoint recoveryTile() { return recoveryTile; }
        public String displayName() { return "Sulliuscep #" + (index + 1); }
    }

    public static final class RouteObstacle
    {
        private final int objectId;
        private final String objectName;
        private final WorldPoint location;
        private final int activeTreeIndex;
        private final String action;
        private final String displayName;
        private final WorldPoint recoveryTile;
        private final PassRule passRule;
        private final int passValue;

        public RouteObstacle(int objectId, String objectName, WorldPoint location, int activeTreeIndex,
                             String action, String displayName, WorldPoint recoveryTile,
                             PassRule passRule, int passValue)
        {
            this.objectId = objectId;
            this.objectName = objectName;
            this.location = location;
            this.activeTreeIndex = activeTreeIndex;
            this.action = action;
            this.displayName = displayName;
            this.recoveryTile = recoveryTile;
            this.passRule = passRule;
            this.passValue = passValue;
        }

        public int objectId() { return objectId; }
        public String objectName() { return objectName; }
        public WorldPoint location() { return location; }
        public int activeTreeIndex() { return activeTreeIndex; }
        public String action() { return action; }
        public String displayName() { return displayName; }
        public WorldPoint recoveryTile() { return recoveryTile; }
        public boolean passed(WorldPoint player) { return passRule.passed(player, passValue); }
    }
}
