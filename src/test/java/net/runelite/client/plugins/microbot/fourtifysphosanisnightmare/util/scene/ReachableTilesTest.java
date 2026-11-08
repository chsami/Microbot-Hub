package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene;

import java.util.Collection;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReachableTilesTest
{
    /** Instance coordinates far from the scene's own numbers, as inside her dream. */
    private static final int BASE_X = 8176;
    private static final int BASE_Y = 1576;
    private static final WorldPoint START = new WorldPoint(BASE_X + 50, BASE_Y + 50, 3);

    @Test
    public void anOpenFloorGivesEveryTileWithinReachInTheStartsCoordinates()
    {
        Collection<WorldPoint> tiles = ReachableTiles.search(new int[104][104], BASE_X, BASE_Y, START, 2);
        assertEquals(25, tiles.size());
        assertTrue(tiles.contains(START.dx(2).dy(-2)));
        assertFalse(tiles.contains(START.dx(3)));
    }

    @Test
    public void aBlockedTileAndTheDiagonalsPastItAreLeftOut()
    {
        int[][] flags = new int[104][104];
        flags[51][50] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        Collection<WorldPoint> tiles = ReachableTiles.search(flags, BASE_X, BASE_Y, START, 1);
        assertFalse(tiles.contains(START.dx(1)));
        assertFalse(tiles.contains(START.dx(1).dy(1)), "a diagonal step may not cut past a blocked tile");
        assertTrue(tiles.contains(START.dx(-1).dy(1)));
    }

    @Test
    public void aStartOutsideTheSceneHasNoTiles()
    {
        assertTrue(ReachableTiles.search(new int[104][104], 0, 0, START, 4).isEmpty());
    }
}
