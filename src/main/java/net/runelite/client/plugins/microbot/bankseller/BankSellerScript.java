package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class BankSellerScript extends Script {

    public boolean run(BankSellerPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (Rs2GrandExchange.hasSoldOffer()) {
                    if (Rs2GrandExchange.openExchange()) {
                        sleepUntil(Rs2GrandExchange::isOpen);
                        Rs2GrandExchange.collectAllToBank();
                        sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer());
                    }
                }

                if (!hasInventoryItems()) {
                    if (bankHasTradeableItems()) {
                        withdrawFromBank();
                    } else {
                        Microbot.stopPlugin(plugin);
                        return;
                    }
                } else {
                    sellInventory();
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean bankHasTradeableItems() {
        return Rs2Bank.bankItems().stream().anyMatch(Rs2ItemModel::isTradeable);
    }

    private boolean hasInventoryItems() {
        return Rs2Inventory.all(Rs2ItemModel::isTradeable).size() > 0;
    }

    private void withdrawFromBank() {
        if (!Rs2Bank.openBank()) {
            return;
        }
        sleepUntil(Rs2Bank::isOpen);
        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty);
        Rs2Bank.setWithdrawAsNote();
        for (Rs2ItemModel item : Rs2Bank.bankItems()) {
            if (Rs2Inventory.isFull()) {
                break;
            }
            if (!item.isTradeable()) {
                continue;
            }
            Rs2Bank.withdrawAll(item.getId());
            sleepUntil(() -> Rs2Inventory.hasItem(item.getName(), true));
        }
        Rs2Bank.closeBank();
    }

    private void sellInventory() {
        List<Rs2ItemModel> items = Rs2Inventory.all(Rs2ItemModel::isTradeable);
        if (items.isEmpty()) {
            return;
        }

        if (!Rs2GrandExchange.openExchange()) {
            return;
        }
        sleepUntil(Rs2GrandExchange::isOpen);

        for (Rs2ItemModel item : items) {
            while (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                if (Rs2GrandExchange.hasSoldOffer()) {
                    Rs2GrandExchange.collectAllToBank();
                }
                sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0
                        || Rs2GrandExchange.hasSoldOffer());
            }
            if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                break;
            }
            int price = Rs2GrandExchange.getPrice(item.getId());
            if (price <= 0) {
                price = 1;
            }
            GrandExchangeRequest request = GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.SELL)
                    .itemName(item.getName())
                    .quantity(item.getQuantity())
                    .price(price)
                    .closeAfterCompletion(false)
                    .build();
            Rs2GrandExchange.processOffer(request);
            sleepUntil(Rs2GrandExchange::isOpen);
            sleepUntil(() -> !Rs2Inventory.hasItem(item.getName(), true));
        }

        Rs2GrandExchange.closeExchange();
        sleepUntil(() -> !Rs2GrandExchange.isOpen());
    }
}