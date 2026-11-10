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
    boolean arrivedAtBank;

    private boolean bankOpenRequested;
    private boolean withdrawalPending;
    private boolean bankCloseRequested;
    private int bankEpochBeforeOpen;
    private int inventoryCountBeforeWithdrawal;
    private int bankCountBeforeWithdrawal;
    private int pendingItemId;
    private int pendingQuantity;

    BankSupplyController(WorldPoint point, BankSupplyRequirement requirement, int firstCost, int secondCost)
    {
        this.bankLocation = point;
        this.requirement = requirement;
        this.directRouteCost = firstCost;
        this.bankDetourCost = secondCost;
    }

    static boolean isBankDetourPreferred(int currentCost, int alternativeCost)
    {
        return TravelTimePolicy.preferAlternative(currentCost, alternativeCost);
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

    boolean hasStartedBankInteraction()
    {
        return this.bankOpenRequested || this.withdrawalPending || this.bankCloseRequested;
    }

    boolean tickBankSupplyPreparation(Client client, EfficientWalker walker)
    {
        if (this.requirement.itemId >= 0 && !net.runelite.client.plugins.microbot.droslayer.walker.TeleportProviders.isItemAllowedOnWorld(client, this.requirement.itemId))
        {
            throw new IllegalStateException(
                "The planned bank teleport item is unavailable on this world. Retry the walk.");
        }

        Widget bankRoot = client.getWidget(786433);
        boolean bankOpen = bankRoot != null && !bankRoot.isHidden();

        if (this.bankCloseRequested)
        {
            return !bankOpen;
        }

        if (!bankOpen)
        {
            if (this.withdrawalPending)
            {
                throw new IllegalStateException(
                    "The bank closed before withdrawal was verified. Check inventory, then retry.");
            }

            if (!this.bankOpenRequested)
            {
                this.bankEpochBeforeOpen = Rs2Bank.getBankLiveEpoch();
                if (!walker.openNearbyBank())
                {
                    throw new IllegalStateException(
                        "No visible bank is accessible here. Open a bank manually, then retry the walk.");
                }
                this.bankOpenRequested = true;
            }

            Microbot.status = "Waiting for the bank to open. Complete any bank PIN prompt; Cancel walk stops the detour.";
            return false;
        }

        if (Rs2Bank.getBankLiveEpoch() <= (this.bankOpenRequested ? this.bankEpochBeforeOpen : 0))
        {
            Microbot.status = "Waiting for a live bank inventory.";
            return false;
        }

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        if (inventory == null || bank == null || inventory.getItems() == null || bank.getItems() == null)
        {
            return false;
        }

        if (this.withdrawalPending)
        {
            if (countItemQuantity(inventory, this.pendingItemId) < this.inventoryCountBeforeWithdrawal + this.pendingQuantity || countItemQuantity(bank, this.pendingItemId) > this.bankCountBeforeWithdrawal - this.pendingQuantity)
            {
                Microbot.status = "Waiting to confirm the teleport supplies withdrawal. Cancel walk stops the detour.";
                return false;
            }
            this.withdrawalPending = false;
        }

        Map<Integer, Integer> bankCounts = new LinkedHashMap<>();
        for (Item item : bank.getItems())
        {
            if (item != null && item.getId() >= 0)
            {
                bankCounts.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        Map<Integer, Integer> required = this.requirement.provider.getRequiredBankItems(client, bankCounts, this.requirement.itemId);
        if (required == null)
        {
            throw new IllegalStateException(
                "The bank stock or requirements for " + this.requirement.provider.name
                    + " changed. Check its supplies and requirements, then retry the walk.");
        }

        if (!required.isEmpty())
        {
            if (client.getVarbitValue(3958) != 0)
            {
                throw new IllegalStateException(
                    "Bank withdrawals are set to Note. Select Item in the bank, then retry the walk.");
            }

            if (!this.requirement.hasWithdrawalInventorySpace(client, required))
            {
                throw new IllegalStateException(
                    "No inventory space for the teleport supplies. Bank unused items, then retry.");
            }

            Map.Entry<Integer, Integer> entry = required.entrySet().iterator().next();
            int itemId = entry.getKey();
            int amount = selectWithdrawalBatchSize(entry.getValue());

            Widget bankItems = client.getWidget(786444);
            Widget itemWidget = null;
            if (bankItems != null && bankItems.getDynamicChildren() != null)
            {
                for (Widget child : bankItems.getDynamicChildren())
                {
                    if (child != null && child.getItemId() == itemId && !child.isHidden())
                    {
                        itemWidget = child;
                        break;
                    }
                }
            }

            if (!walker.clickWidgetAction(itemWidget, "Withdraw-" + amount))
            {
                throw new IllegalStateException(
                    "Cannot select the bank teleport supplies. Clear bank search and make the item visible, then retry.");
            }

            this.pendingItemId = itemId;
            this.pendingQuantity = amount;
            this.inventoryCountBeforeWithdrawal = countItemQuantity(inventory, itemId);
            this.bankCountBeforeWithdrawal = countItemQuantity(bank, itemId);
            this.withdrawalPending = true;
            return false;
        }

        Widget bankClose = client.getWidget(786434);
        if (bankClose != null && bankClose.getDynamicChildren() != null)
        {
            for (Widget child : bankClose.getDynamicChildren())
            {
                if (walker.clickWidgetAction(child, "Close"))
                {
                    this.bankCloseRequested = true;
                    return false;
                }
            }
        }

        throw new IllegalStateException(
            "Cannot close the bank. Close it manually, then retry the walk.");
    }

    private static int countItemQuantity(ItemContainer container, int itemId)
    {
        if (container == null || container.getItems() == null)
        {
            return -1;
        }

        int quantity = 0;
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() == itemId)
            {
                quantity += item.getQuantity();
            }
        }
        return quantity;
    }

    static int selectWithdrawalBatchSize(int amount)
    {
        if (amount >= 10)
        {
            return 10;
        }
        if (amount >= 5)
        {
            return 5;
        }
        return 1;
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

        int getWithdrawalCost()
        {
            int withdrawalActions = 0;
            for (int requested : this.requiredItems.values())
            {
                int remaining = requested;
                while (remaining > 0)
                {
                    remaining -= selectWithdrawalBatchSize(remaining);
                    withdrawalActions++;
                }
            }

            return 8 + 2 * Math.max(1, withdrawalActions) + (this.provider.isWhistleProvider() ? 4 : 0);
        }

        boolean canPrepareSupplies(Client client)
        {
            return hasWithdrawalInventorySpace(client, this.requiredItems);
        }

        static boolean hasWithdrawalInventorySpace(Client client, Map<Integer, Integer> requirements)
        {
            return BankCandidateCapacity.canPrepare(client, requirements);
        }
    }
}
