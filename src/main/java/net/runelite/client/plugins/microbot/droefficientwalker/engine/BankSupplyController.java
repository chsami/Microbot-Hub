package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.BankCandidateCapacity;
import net.runelite.client.plugins.microbot.droefficientwalker.TravelTimePolicy;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

final class BankSupplyController {
   final WorldPoint bankLocation;
   final BankSupplyController.BankSupplyRequirement requirement;
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

   BankSupplyController(WorldPoint bankLocation, BankSupplyRequirement requirement, int directRouteCost, int bankDetourCost) {
      this.bankLocation = bankLocation;
      this.requirement = requirement;
      this.directRouteCost = directRouteCost;
      this.bankDetourCost = bankDetourCost;
   }

   static int estimateRouteTicks(List<WorldPoint> route, boolean running) {
      if (route.isEmpty()) {
         return Integer.MAX_VALUE;
      }

      int completedSegmentTicks = 0;
      int walkingSteps = 0;

      for (int waypointIndex = 1; waypointIndex < route.size(); waypointIndex++) {
         if (route.get(waypointIndex - 1).distanceTo(route.get(waypointIndex)) == 1) {
            walkingSteps++;
         } else {
            completedSegmentTicks += (running ? (walkingSteps + 1) / 2 : walkingSteps) + 4;
            walkingSteps = 0;
         }
      }

      return completedSegmentTicks + (running ? (walkingSteps + 1) / 2 : walkingSteps);
   }

   boolean hasStartedBankInteraction() {
      return this.bankOpenRequested || this.withdrawalPending || this.bankCloseRequested;
   }

   boolean tickBankSupplyPreparation(Client client, WalkerEngine walker) {
      if (this.requirement.itemId >= 0 && !TeleportProviders.isItemAllowedOnWorld(client, this.requirement.itemId)) {
         throw new IllegalStateException(
            "The planned bank teleport item is unavailable on this world. Retry the walk."
         );
      }

      Widget bankWindow = client.getWidget(786433);
      boolean bankOpen = bankWindow != null && !bankWindow.isHidden();
      if (this.bankCloseRequested) {
         return !bankOpen;
      }

      if (!bankOpen) {
         if (this.withdrawalPending) {
            throw new IllegalStateException(
               "The bank closed before withdrawal was verified. Check inventory, then retry."
            );
         }

         if (!this.bankOpenRequested) {
            this.bankEpochBeforeOpen = Rs2Bank.getBankLiveEpoch();
            if (!walker.openNearbyBank()) {
               throw new IllegalStateException(
                  "No visible bank is accessible here. Open a bank manually, then retry the walk."
               );
            }

            this.bankOpenRequested = true;
         }

         Microbot.status = "Waiting for the bank to open. Complete any bank PIN prompt; Cancel walk stops the detour.";
         return false;
      } else {
         if (Rs2Bank.getBankLiveEpoch() <= (this.bankOpenRequested ? this.bankEpochBeforeOpen : 0)) {
            Microbot.status = "Waiting for a live bank inventory.";
            return false;
         }

         ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
         ItemContainer bank = client.getItemContainer(InventoryID.BANK);
         if (inventory != null && bank != null && inventory.getItems() != null && bank.getItems() != null) {
            if (this.withdrawalPending) {
               if (countItemQuantity(inventory, this.pendingItemId) < this.inventoryCountBeforeWithdrawal + this.pendingQuantity || countItemQuantity(bank, this.pendingItemId) > this.bankCountBeforeWithdrawal - this.pendingQuantity) {
                  Microbot.status = "Waiting to confirm the teleport supplies withdrawal. Cancel walk stops the detour.";
                  return false;
               }

               this.withdrawalPending = false;
            }

            LinkedHashMap<Integer, Integer> bankStock = new LinkedHashMap<>();

            for (Item item : bank.getItems()) {
               if (item != null && item.getId() >= 0) {
                  bankStock.merge(item.getId(), item.getQuantity(), Integer::sum);
               }
            }

            Map<Integer, Integer> neededItems = this.requirement.provider.getRequiredBankItems(client, bankStock, this.requirement.itemId);
            if (neededItems == null) {
               String providerName = this.requirement.provider.name;
               throw new IllegalStateException(
                  "The bank stock or requirements for "
                     + providerName
                     + " changed. Check its supplies and requirements, then retry the walk."
               );
            }

            if (neededItems.isEmpty()) {
               Widget bankTitleBar = client.getWidget(786434);
               if (bankTitleBar != null && bankTitleBar.getDynamicChildren() != null) {
                  for (Widget closeButton : bankTitleBar.getDynamicChildren()) {
                     if (walker.clickWidgetAction(closeButton, "Close")) {
                        this.bankCloseRequested = true;
                        return false;
                     }
                  }
               }

               throw new IllegalStateException(
                  "Cannot close the bank. Close it manually, then retry the walk."
               );
            } else {
               if (client.getVarbitValue(3958) != 0) {
                  throw new IllegalStateException(
                     "Bank withdrawals are set to Note. Select Item in the bank, then retry the walk."
                  );
               }

               if (!this.requirement.hasWithdrawalInventorySpace(client, neededItems)) {
                  throw new IllegalStateException(
                     "No inventory space for the teleport supplies. Bank unused items, then retry."
                  );
               }

               Entry<Integer, Integer> firstNeededItem = neededItems.entrySet().iterator().next();
               int itemId = firstNeededItem.getKey();
               int withdrawalQuantity = selectWithdrawalBatchSize(firstNeededItem.getValue());
               Widget bankItemsWidget = client.getWidget(786444);
               Widget itemWidget = null;
               if (bankItemsWidget != null && bankItemsWidget.getDynamicChildren() != null) {
                  for (Widget bankItemWidget : bankItemsWidget.getDynamicChildren()) {
                     if (bankItemWidget != null && bankItemWidget.getItemId() == itemId && !bankItemWidget.isHidden()) {
                        itemWidget = bankItemWidget;
                        break;
                     }
                  }
               }

               if (!walker.clickWidgetAction(itemWidget, "Withdraw-" + withdrawalQuantity)) {
                  throw new IllegalStateException(
                     "Cannot select the bank teleport supplies. Clear bank search and make the item visible, then retry."
                  );
               }

               this.pendingItemId = itemId;
               this.pendingQuantity = withdrawalQuantity;
               this.inventoryCountBeforeWithdrawal = countItemQuantity(inventory, itemId);
               this.bankCountBeforeWithdrawal = countItemQuantity(bank, itemId);
               this.withdrawalPending = true;
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private static int countItemQuantity(ItemContainer container, int itemId) {
      if (container != null && container.getItems() != null) {
         int quantity = 0;

         for (Item item : container.getItems()) {
            if (item != null && item.getId() == itemId) {
               quantity += item.getQuantity();
            }
         }

         return quantity;
      } else {
         return -1;
      }
   }

   static int selectWithdrawalBatchSize(int requiredQuantity) {
      return requiredQuantity >= 10 ? 10 : (requiredQuantity >= 5 ? 5 : 1);
   }

   static boolean isBankDetourPreferred(int directRouteTicks, int bankDetourTicks) {
      return TravelTimePolicy.preferAlternative(directRouteTicks, bankDetourTicks);
   }

   static final class BankSupplyRequirement {
      final TeleportProviders.TeleportProvider provider;
      final WorldPoint destination;
      final int itemId;
      final Map<Integer, Integer> requiredItems;

      BankSupplyRequirement(TeleportProviders.TeleportProvider provider, WorldPoint destination, int itemId) {
         this(provider, destination, itemId, Map.of(itemId, 1));
      }

      BankSupplyRequirement(TeleportProviders.TeleportProvider provider, WorldPoint destination, Map<Integer, Integer> requiredItems) {
         this(provider, destination, -1, requiredItems);
      }

      private BankSupplyRequirement(TeleportProviders.TeleportProvider provider, WorldPoint destination, int itemId, Map<Integer, Integer> requiredItems) {
         this.provider = provider;
         this.destination = destination;
         this.itemId = itemId;
         this.requiredItems = Collections.unmodifiableMap(new LinkedHashMap<>(requiredItems));
      }

      int getWithdrawalCost() {
         int withdrawals = 0;
         for (int quantity : this.requiredItems.values()) {
            while (quantity > 0) {
               quantity -= BankSupplyController.selectWithdrawalBatchSize(quantity);
               withdrawals++;
            }
         }

         int cost = 10 + Math.max(0, withdrawals - 1) * 2;
         return cost + (this.provider.isWhistleProvider() ? 4 : 0);
      }

      boolean canPrepareSupplies(Client client) {
         return BankCandidateCapacity.canPrepare(client, this.requiredItems);
      }

      static boolean hasWithdrawalInventorySpace(Client client, Map<Integer, Integer> neededItems) {
         ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
         if (inventory != null && inventory.getItems() != null) {
            int occupiedSlots = 0;

            for (Item item : inventory.getItems()) {
               if (item != null && item.getId() >= 0) {
                  occupiedSlots++;
               }
            }

            int requiredSlots = 0;

            for (int itemId : neededItems.keySet()) {
               if (client.getItemDefinition(itemId) == null) {
                  return false;
               }

               if (BankSupplyController.countItemQuantity(inventory, itemId) == 0 || !client.getItemDefinition(itemId).isStackable()) {
                  requiredSlots++;
               }
            }

            return occupiedSlots + requiredSlots <= 28;
         } else {
            return false;
         }
      }
   }
}
