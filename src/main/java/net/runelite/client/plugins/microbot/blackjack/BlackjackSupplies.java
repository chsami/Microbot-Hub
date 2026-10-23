package net.runelite.client.plugins.microbot.blackjack;

import net.runelite.api.Item;

/** Exact inventory IDs: notes are quantities, not occupied slots or linked IDs. */
final class BlackjackSupplies
{
    final int wine;
    final int notedWine;

    private BlackjackSupplies(int wine, int notedWine)
    {
        this.wine = wine;
        this.notedWine = notedWine;
    }

    static BlackjackSupplies from(Item[] items)
    {
        int wine = 0;
        int notes = 0;
        for (Item item : items)
        {
            if (item == null)
            {
                continue;
            }
            if (item.getId() == 1993)
            {
                wine += item.getQuantity();
            }
            else if (item.getId() == 1994)
            {
                notes += item.getQuantity();
            }
        }
        return new BlackjackSupplies(wine, notes);
    }
}
