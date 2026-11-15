package net.runelite.client.plugins.microbot.moonsofperil.handlers;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

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

    @Test
    void dropsTheNetAndKnifeLeftOverFromFishing() {
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            inventory.when(() -> Rs2Inventory.contains(ItemID.BIG_NET)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.contains(ItemID.KNIFE)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.drop(anyInt())).thenReturn(true);

            ResupplyHandler.dropFishingTools();

            inventory.verify(() -> Rs2Inventory.drop(ItemID.BIG_NET));
            inventory.verify(() -> Rs2Inventory.drop(ItemID.KNIFE));
        }
    }

    @Test
    void dropsNothingWhenNoFishingToolIsCarried() {
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            ResupplyHandler.dropFishingTools();

            inventory.verify(() -> Rs2Inventory.drop(anyInt()), never());
        }
    }
}
