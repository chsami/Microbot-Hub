package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DroSlayerCatacombsBlackDemonLocationTest {
    @Test void blackDemonSpotReachesBlackDemonsAndNotTheLesserDemonArea() {
        WorldPoint spot = DroSlayerLocation.fromName("catacombs black demons").getWorldPoint();
        assertEquals(0, spot.getPlane());
        assertTrue(spot.distanceTo(new WorldPoint(1720, 10083, 0)) <= 5);
        assertTrue(spot.distanceTo(new WorldPoint(1718, 10079, 0)) <= 12);
        assertTrue(spot.distanceTo(new WorldPoint(1721, 10090, 0)) <= 12);
        assertTrue(spot.distanceTo(new WorldPoint(1714, 10066, 0)) > 12);
    }
}
