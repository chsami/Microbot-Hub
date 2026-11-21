package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.util.BitSet;
import net.runelite.api.CollisionDataFlag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapClickSafetyTest
{
    /** A fence between rows 5 and 6, like the north side of the field west of the Ectofuntus. */
    private static int[][] fencedGrid()
    {
        int[][] flags = new int[12][12];
        for (int x = 0; x < 12; x++)
        {
            flags[x][5] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
            flags[x][6] |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
        }
        return flags;
    }

    @Test
    void routeTileAgainstFenceIsClickedOneStepAwayOnTheSameSide()
    {
        int[][] flags = fencedGrid();
        assertTrue(MinimapClickSafety.fenceAdjacent(flags, new BitSet(), 4, 6));
        assertArrayEquals(new int[]{4, 7}, MinimapClickSafety.safeTarget(flags, new BitSet(), 4, 6));
        assertArrayEquals(new int[]{4, 4}, MinimapClickSafety.safeTarget(flags, new BitSet(), 4, 5));
    }

    @Test
    void openTilesAreClickedUnchanged()
    {
        int[][] flags = fencedGrid();
        assertFalse(MinimapClickSafety.fenceAdjacent(flags, new BitSet(), 4, 8));
        assertArrayEquals(new int[]{4, 8}, MinimapClickSafety.safeTarget(flags, new BitSet(), 4, 8));
    }

    @Test
    void stationaryHitsAreNotTransportProgress()
    {
        assertTrue(WalkerEngine.isStationaryCombatAnimation(30, true, true));
        assertFalse(WalkerEngine.isStationaryCombatAnimation(-1, true, true));
        assertFalse(WalkerEngine.isStationaryCombatAnimation(30, false, true));
        assertFalse(WalkerEngine.isStationaryCombatAnimation(30, true, false));
    }
}
