package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MmCavesWallTargetTest {
    @Test
    void samplesStayInsideTheSameWallTileAndAwayFromItsExactCenter() {
        WorldPoint tile = new WorldPoint(2448, 9174, 1);
        for (int i = 0; i < 1000; i++) {
            MmCavesWallTarget target = MmCavesWallTarget.sample(tile);
            assertEquals(tile, target.tile);
            assertTrue(Math.abs(target.offsetX) <= MmCavesWallTarget.MAX_OFFSET);
            assertTrue(Math.abs(target.offsetY) <= MmCavesWallTarget.MAX_OFFSET);
            assertFalse(target.offsetX == 0 && target.offsetY == 0);
        }
    }
}
