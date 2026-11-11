package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks bank supply preparation and estimates teleport withdrawal costs.
 */
final class BankSupplyController
{
    final WorldPoint bankLocation;
    final BankSupplyRequirement requirement;
    final int directRouteCost;
    final int bankDetourCost;
    BankSupplyController(WorldPoint point, BankSupplyRequirement requirement, int firstCost, int secondCost)
    {
        this.bankLocation = point;
        this.requirement = requirement;
        this.directRouteCost = firstCost;
        this.bankDetourCost = secondCost;
    }

    static int estimateRouteTicks(List<WorldPoint> path, boolean compressed)
    {
        if (path.isEmpty())
        {
            return Integer.MAX_VALUE;
        }

        int score = 0;
        int contiguous = 0;

        for (int index = 1; index < path.size(); index++)
        {
            if (path.get(index - 1).distanceTo(path.get(index)) == 1)
            {
                contiguous++;
            }
            else
            {
                score += (compressed ? (contiguous + 1) / 2 : contiguous) + 4;
                contiguous = 0;
            }
        }

        return score + (compressed ? (contiguous + 1) / 2 : contiguous);
    }

    /** Restored nested bank/resource requirement descriptor. */
    static final class BankSupplyRequirement
    {
        final TeleportProviders.TeleportProvider provider;
        final WorldPoint destination;
        final int itemId;
        final Map<Integer, Integer> requiredItems;

        BankSupplyRequirement(TeleportProviders.TeleportProvider provider, WorldPoint point, int itemId)
        {
            this(provider, point, itemId, Map.of(itemId, 1));
        }

        BankSupplyRequirement(TeleportProviders.TeleportProvider provider, WorldPoint point,
          Map<Integer, Integer> requirements)
        {
            this(provider, point, -1, requirements);
        }

        private BankSupplyRequirement(TeleportProviders.TeleportProvider provider, WorldPoint point,
                  int itemId, Map<Integer, Integer> requirements)
        {
            this.provider = provider;
            this.destination = point;
            this.itemId = itemId;
            this.requiredItems = Collections.unmodifiableMap(new LinkedHashMap<>(requirements));
        }

    }
}
