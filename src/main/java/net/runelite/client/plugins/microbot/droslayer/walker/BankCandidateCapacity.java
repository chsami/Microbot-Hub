package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Planning must include the same safe space-making operation as withdrawal. */
public final class BankCandidateCapacity
{
    private BankCandidateCapacity() { }

    public static boolean canPrepare(Client client, Map<Integer, Integer> requirements)
    {
        return rejection(client, requirements).isEmpty();
    }

    /** Empty means ready. Keep missing snapshots distinct from a genuinely full bag. */
    public static String rejection(Client client, Map<Integer, Integer> requirements)
    {
        // RuneLite exposes the live backpack as INV during bank interactions on
        // some game revisions. INVENTORY can be null while the bank is open;
        // do not reject every bank-backed travel candidate on that basis.
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container == null || container.getItems() == null)
            container = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
        if (container == null || container.getItems() == null) return "inventory-container-unavailable";
        Map<Integer, Integer> carried = new HashMap<>();
        int occupied = 0;
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
            {
                occupied++;
                carried.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }
        for (int id : requirements.keySet())
            if (client.getItemDefinition(id) == null) return "item-definition-unavailable item="+id;
        int needed = WithdrawalCapacity.additionalSlotsNeeded(requirements, carried,
            id -> client.getItemDefinition(id).isStackable());
        int available = Math.max(0, 28 - occupied);
        for (Map.Entry<Integer, Integer> item : carried.entrySet())
        {
            if (requirements.containsKey(item.getKey()) || item.getValue() <= 1) continue;
            ItemComposition definition = client.getItemDefinition(item.getKey());
            if (definition != null && !definition.isStackable()
                && definition.getInventoryActions() != null
                && Arrays.stream(definition.getInventoryActions()).anyMatch("Eat"::equalsIgnoreCase))
                available += item.getValue() - 1;
        }
        return needed <= available ? "" : "inventory-space requiredSlots="+needed+" availableSlots="+available+" occupiedSlots="+occupied;
    }
}
