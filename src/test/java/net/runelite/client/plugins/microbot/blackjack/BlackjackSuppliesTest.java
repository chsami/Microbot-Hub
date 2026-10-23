package net.runelite.client.plugins.microbot.blackjack;

import net.runelite.api.Item;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BlackjackSuppliesTest
{
    @Test
    void countsExactWineIdsAndNoteQuantities()
    {
        Item[] items = new Item[28];
        for (int i = 0; i < 17; i++)
        {
            items[i] = new Item(1993, 1);
        }
        items[17] = new Item(1994, 2167);
        items[18] = new Item(1935, 1);
        items[19] = new Item(995, 53984);
        BlackjackSupplies supplies = BlackjackSupplies.from(items);
        assertEquals(17, supplies.wine);
        assertEquals(2167, supplies.notedWine);
    }

    @Test
    void emptyInventoryAndHealingEstimate()
    {
        assertEquals(0, BlackjackSupplies.from(new Item[0]).wine);
        assertEquals(0, BlackjackSupplies.from(new Item[0]).notedWine);
        assertEquals(2, BlackjackScript.winesNeededToReach(44, 66));
        assertEquals(0, BlackjackScript.winesNeededToReach(66, 66));
    }
}
