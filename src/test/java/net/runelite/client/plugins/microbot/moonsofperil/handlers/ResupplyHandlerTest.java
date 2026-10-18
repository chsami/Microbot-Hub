package net.runelite.client.plugins.microbot.moonsofperil.handlers;

import net.runelite.api.gameval.ObjectID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResupplyHandlerTest {
    @Test
    void fishesTheRiverFishingSpotNotTheRetiredMoonsId() {
        assertEquals(ObjectID.RIVER_FISHING_SPOT, ResupplyHandler.BREAM_FISHING_SPOT_ID);
        assertNotEquals(ObjectID.SAILING_GANGPLANK_PROXY_WIDE_VIKING, ResupplyHandler.BREAM_FISHING_SPOT_ID);
    }

    @Test
    void fishingStallsOnlyAfterTimeoutWithoutProgress() {
        long start = 1_000_000L;
        assertFalse(ResupplyHandler.isFishingStalled(start, start));
        assertFalse(ResupplyHandler.isFishingStalled(start, start + ResupplyHandler.FISHING_STALL_TIMEOUT_MS - 1));
        assertTrue(ResupplyHandler.isFishingStalled(start, start + ResupplyHandler.FISHING_STALL_TIMEOUT_MS));
    }
}
