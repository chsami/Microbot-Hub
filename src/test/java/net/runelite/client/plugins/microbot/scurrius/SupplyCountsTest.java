package net.runelite.client.plugins.microbot.scurrius;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SupplyCountsTest {
    private static final int TAB = 8007;
    private static final int LOBSTER = 379;
    private static final int POTION = 2434;

    private static int quantity(List<int[]> slots, int id) {
        return SupplyCounts.quantity(slots, slot -> slot[0], slot -> slot[1], id);
    }

    @Test
    void aStackOfFiveTabsIsFiveNotOne() {
        List<int[]> inventory = List.of(new int[]{TAB, 5}, new int[]{LOBSTER, 1}, new int[]{POTION, 1});
        assertEquals(5, quantity(inventory, TAB));
    }

    @Test
    void singleItemsCountOneEachAndStacksSplitOverSlotsAdd() {
        List<int[]> inventory = List.of(new int[]{LOBSTER, 1}, new int[]{LOBSTER, 1}, new int[]{LOBSTER, 1},
                new int[]{TAB, 2}, new int[]{TAB, 3});
        assertEquals(3, quantity(inventory, LOBSTER));
        assertEquals(5, quantity(inventory, TAB));
        assertEquals(0, quantity(inventory, POTION));
    }

    @Test
    void theReportedInventoryIsFull() {
        // id 8007 x5 in one stack, 20 lobsters, 4 prayer potions
        java.util.ArrayList<int[]> inventory = new java.util.ArrayList<>();
        inventory.add(new int[]{TAB, 5});
        for (int i = 0; i < 20; i++) inventory.add(new int[]{LOBSTER, 1});
        for (int i = 0; i < 4; i++) inventory.add(new int[]{POTION, 1});
        assertNull(SupplyCounts.shortage(quantity(inventory, LOBSTER), 20, quantity(inventory, POTION), 2, quantity(inventory, TAB), 5));
    }

    @Test
    void shortageNamesEveryShortItemWithItsAmounts() {
        assertEquals("food 18/20", SupplyCounts.shortage(18, 20, 2, 2, 5, 5));
        assertEquals("food 0/20, prayer potions 1/2, teleport tabs 1/5", SupplyCounts.shortage(0, 20, 1, 2, 1, 5));
        assertNull(SupplyCounts.shortage(20, 20, 2, 2, 5, 5));
        assertNull(SupplyCounts.shortage(25, 20, 3, 2, 6, 5));
    }
}
