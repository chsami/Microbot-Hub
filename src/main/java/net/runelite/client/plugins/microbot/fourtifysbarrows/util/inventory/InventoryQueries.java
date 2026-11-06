package net.runelite.client.plugins.microbot.fourtifysbarrows.util.inventory;

import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

public final class InventoryQueries
{
    private InventoryQueries()
    {
    }

    public static Item[] items(Client client)
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY.getId());
        return inventory == null ? new Item[0] : inventory.getItems();
    }
    public static int freeSlots(Client client)
    {
        return 28 - (int) Arrays.stream(items(client)).limit(28)
                .filter(item -> item != null && item.getId() > 0).count();
    }

    public static int count(Client client, int id)
    {
        return Arrays.stream(items(client)).filter(item -> item.getId() == id).mapToInt(Item::getQuantity).sum();
    }

    public static boolean wearing(Client client, int id)
    {
        ItemContainer equipment = client.getItemContainer(94);
        return equipment != null && Arrays.stream(equipment.getItems()).anyMatch(item -> item.getId() == id);
    }
}
