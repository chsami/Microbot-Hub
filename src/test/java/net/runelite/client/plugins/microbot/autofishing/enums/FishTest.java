package net.runelite.client.plugins.microbot.autofishing.enums;

import net.runelite.client.game.FishingSpot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishTest {
    @Test
    void bigNetFishUseTheBigNetSpot() {
        for (Fish fish : new Fish[]{Fish.BASS, Fish.COD, Fish.MACKEREL}) {
            assertArrayEquals(FishingSpot.SHARK.getIds(), fish.getFishingSpot(), fish.name());
        }
    }

    @Test
    void bigNetFishTravelToBigNetSpotLocations() {
        for (Fish fish : new Fish[]{Fish.COD, Fish.MACKEREL}) {
            List<FishingSpotLocation> locations = fish.getAvailableLocations();
            assertFalse(locations.isEmpty(), fish.name());
            for (FishingSpotLocation location : locations) {
                assertTrue(location.getTooltip().contains(FishingSpot.SHARK.getWorldMapTooltip()), fish + " " + location);
            }
        }
    }
}
