package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerDeepWildernessStairsTest {
    @Test public void verifiedHillGiantCannonTileRequiresSurfaceStairHandoff() {
        WorldPoint cannon = new WorldPoint(3044, 10318, 0);
        assertTrue(DroSlayerDeepWildernessStairs.isDungeonTask("Deep Wilderness Dungeon - Hill giants", cannon));
        assertTrue(DroSlayerDeepWildernessStairs.nearSurfaceStairs(new WorldPoint(3044, 3927, 0)));
        assertFalse(DroSlayerDeepWildernessStairs.isInside(new WorldPoint(3044, 3927, 0)));
        assertTrue(DroSlayerDeepWildernessStairs.isInside(new WorldPoint(3044, 10322, 0)));
        assertEquals(16664, DroSlayerDeepWildernessStairs.STAIRCASE_OBJECT_ID);
    }

    @Test public void otherTasksAndRemoteWildernessNeverHijacked() {
        assertFalse(DroSlayerDeepWildernessStairs.isDungeonTask("Lava Maze", new WorldPoint(3110, 3854, 0)));
        assertFalse(DroSlayerDeepWildernessStairs.isDungeonTask("Deep Wilderness Dungeon - Hill giants", new WorldPoint(3044, 3927, 0)));
        assertFalse(DroSlayerDeepWildernessStairs.nearSurfaceStairs(new WorldPoint(3109, 3515, 0)));
        assertFalse(DroSlayerDeepWildernessStairs.nearSurfaceStairs(new WorldPoint(3044, 10322, 0)));
    }
}
