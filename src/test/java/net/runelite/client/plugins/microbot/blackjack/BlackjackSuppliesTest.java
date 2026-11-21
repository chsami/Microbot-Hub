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

    @Test
    void healTargetAboveMaxHitpointsIsCappedAtMaxHitpoints()
    {
        int target = BlackjackScript.healTargetHitpoints(75, 57);
        assertEquals(57, target);
        assertEquals(0, BlackjackScript.winesNeededToReach(57, target));
        assertEquals(2, BlackjackScript.winesNeededToReach(38, target));
        assertEquals(75, BlackjackScript.healTargetHitpoints(75, 99));
        assertEquals(75, BlackjackScript.healTargetHitpoints(75, 0));
    }

    @Test
    void heldWineBelowNeedBuysFromShopInsteadOfReturning()
    {
        int needed = BlackjackScript.winesNeededToReach(39, 75);
        assertEquals(BlackjackScript.WineShopStep.BUY,
                BlackjackScript.wineShopStep(1, -1, needed, true, 0));
        assertEquals(BlackjackScript.WineShopStep.BUY,
                BlackjackScript.wineShopStep(0, -1, 0, true, 0));
        assertEquals(BlackjackScript.WineShopStep.BUY,
                BlackjackScript.wineShopStep(1, -1, 0, true, 0));
        assertEquals(BlackjackScript.WineShopStep.RETURN,
                BlackjackScript.wineShopStep(needed + 1, -1, needed, true, 0));
    }

    @Test
    void purchaseWaitsForJugsThenReturnsOrBuysMore()
    {
        assertEquals(BlackjackScript.WineShopStep.WAIT,
                BlackjackScript.wineShopStep(1, 1, 4, false, 1));
        assertEquals(BlackjackScript.WineShopStep.RETURN,
                BlackjackScript.wineShopStep(10, 1, 4, false, 1));
        assertEquals(BlackjackScript.WineShopStep.BUY,
                BlackjackScript.wineShopStep(4, 1, 4, false, 1));
    }

    @Test
    void purchaseThatAddsNoJugsRetriesThenFails()
    {
        assertEquals(BlackjackScript.WineShopStep.BUY,
                BlackjackScript.wineShopStep(1, 1, 4, true, 1));
        assertEquals(BlackjackScript.WineShopStep.BUY,
                BlackjackScript.wineShopStep(1, 1, 4, true, 2));
        assertEquals(BlackjackScript.WineShopStep.FAIL,
                BlackjackScript.wineShopStep(1, 1, 4, true, 3));
    }
}
