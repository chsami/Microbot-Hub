package net.runelite.client.plugins.microbot.banksshopper;

import net.runelite.api.GameState;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

enum ShopperState {
    SHOPPING,
    BANKING,
    HOPPING
}

public class BanksShopperScript extends Script {

    private final BanksShopperPlugin plugin;
    private ShopperState state = ShopperState.SHOPPING;

    public BanksShopperScript(final BanksShopperPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the shop - handles both NPC shops and game object shops (e.g., Culinaromancer's chest).
     */
    private boolean openShopInterface() {
        if (Rs2Shop.isOpen()) return true;
        if (plugin.isUseGameObject()) {
            return Rs2GameObject.interact(plugin.getNpcName(), plugin.getShopAction());
        } else {
            return Rs2Shop.openShop(plugin.getNpcName(), plugin.isUseExactNaming());
        }
    }

    public boolean run(BanksShopperConfig config) {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn() || Rs2AntibanSettings.actionCooldownActive)
                    return;

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                switch (state) {
                    case SHOPPING:
                        boolean missingAllRequiredItems = plugin.getItemNames().stream().noneMatch((itemName) -> {
                            if (itemName == null || itemName.isEmpty())
                                return false;
                            if (itemName.matches("\\d+")) {
                                return Rs2Inventory.hasItem(Integer.parseInt(itemName));
                            } else {
                                return Rs2Inventory.hasItem(itemName);
                            }
                        });

                        if (missingAllRequiredItems && plugin.getSelectedAction() == Actions.SELL) {
                            Microbot.status = "[Shutting down] - Reason: Not enough supplies.";
                            Microbot.showMessage(Microbot.status);
                            Microbot.stopPlugin(plugin);
                            return;
                        }

                        sleepUntil(this::openShopInterface, 5000);

                        boolean successfullAction = false;
                        boolean outOfStock = false;
                        if (Rs2Shop.isOpen()) {
                            for (String itemName : plugin.getItemNames()) {
                                if (!isRunning() || Microbot.pauseAllScripts.get())
                                    break;
                                if (itemName.length() <= 1)
                                    continue;

                                switch (plugin.getSelectedAction()) {
                                    case BUY:
                                        // Check if name is purely numeric or alphanumeric
                                        if (itemName.matches("\\d+")) {
                                            outOfStock = !Rs2Shop.hasMinimumStock(Integer.parseInt(itemName),
                                                    plugin.getMinStock());
                                            if (outOfStock)
                                                continue;
                                            if (plugin.isUnlimitedStock()) {
                                                while (isRunning() && !Rs2Inventory.isFull()) {
                                                    if (!processBuyAction(Integer.parseInt(itemName),
                                                            plugin.getSelectedQuantity().toString()))
                                                        break;
                                                    sleepGaussian(200, 40);
                                                }
                                                successfullAction = true;
                                            } else {
                                                successfullAction = processBuyAction(Integer.parseInt(itemName),
                                                        plugin.getSelectedQuantity().toString());
                                            }
                                        } else {
                                            outOfStock = !Rs2Shop.hasMinimumStock(itemName, plugin.getMinStock());
                                            if (outOfStock)
                                                continue;
                                            if (plugin.isUnlimitedStock()) {
                                                while (isRunning() && !Rs2Inventory.isFull()) {
                                                    if (!processBuyAction(itemName,
                                                            plugin.getSelectedQuantity().toString()))
                                                        break;
                                                    sleepGaussian(200, 40);
                                                }
                                                successfullAction = true;
                                            } else {
                                                successfullAction = processBuyAction(itemName,
                                                        plugin.getSelectedQuantity().toString());
                                            }
                                        }
                                        if (Rs2Inventory.isFull()) {
                                            System.out.println("Inventory is full, stopping buy action to bank.");
                                            if (!plugin.isBlastFurnaceOptimization() && !plugin.isFastMode()) {
                                                Rs2Shop.closeShop();
                                            }
                                            state = ShopperState.BANKING;
                                            return;
                                        }
                                        break;
                                    case SELL:
                                        if (Rs2Shop.isFull())
                                            continue;
                                        // Check if name is purely numeric or alphanumeric
                                        if (itemName.matches("\\d+")) {
                                            while (isRunning() && processSellAction(Integer.parseInt(itemName),
                                                    plugin.getSelectedQuantity().toString())) {
                                                sleepGaussian(200, 40);
                                                if (Rs2Shop.hasMinimumStock(Integer.parseInt(itemName),
                                                        plugin.getMinStock())) {
                                                    System.out.println("Stop selling over the minimum stock for item: "
                                                            + itemName);
                                                    successfullAction = true;
                                                    break;
                                                }
                                            }
                                        } else {
                                            while (isRunning() && processSellAction(itemName,
                                                    plugin.getSelectedQuantity().toString())) {
                                                sleepGaussian(200, 40);
                                                if (Rs2Shop.hasMinimumStock(itemName, plugin.getMinStock())) {
                                                    System.out.println("Stop selling over the minimum stock for item: "
                                                            + itemName);
                                                    successfullAction = true;
                                                    break;
                                                }
                                            }
                                        }
                                        break;
                                    default:
                                        System.out.println("Invalid action specified in config.");
                                }
                            }
                            if (!plugin.isFastMode()) {
                                Rs2Shop.closeShop();
                            }
                            if (successfullAction) {
                                if (plugin.isUnlimitedStock()) {
                                    state = ShopperState.SHOPPING;
                                } else {
                                    state = ShopperState.HOPPING;
                                }
                                return;
                            } else if (outOfStock) {
                                System.out.println("Out of stock for all items, hopping worlds...");
                                state = ShopperState.HOPPING;
                                return;
                            }
                        }
                        break;
                    case BANKING:
                        if (plugin.isFastMode()) {
                            if (!bankItemsFastMode()) {
                                return;
                            }
                        } else if (plugin.isBlastFurnaceOptimization()) {
                            if (!bankItemsWithoutWalkBack()) {
                                return;
                            }
                        } else {
                            var walkBackLocation = plugin.getShopLocation() != null
                                    ? plugin.getShopLocation() : initialPlayerLocation;
                            if (!Rs2Bank.bankItemsAndWalkBackToOriginalPosition(plugin.getItemNames(),
                                    walkBackLocation)) {
                                return;
                            }
                        }
                        state = ShopperState.SHOPPING;
                        break;
                    case HOPPING:
                        hopWorld();
                        state = ShopperState.SHOPPING;
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        if (Rs2Shop.isOpen()) {
            Rs2Shop.closeShop();
        }

        if (plugin.isUseLogout()) {
            Rs2Player.logout();
        }

        state = ShopperState.SHOPPING; // Reset state to SHOPPING for next run
        initialPlayerLocation = null; // Reset initial player location

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    /**
     * Hops to a new world
     */
    private void hopWorld() {
        System.out.println("[BanksShopper] Starting world hop sequence...");
        Rs2Shop.closeShop();

        int currentWorld = Microbot.getClient().getWorld();
        int targetWorld = currentWorld + 1;
        System.out.println("[BanksShopper] Current world: " + currentWorld + ", target world: " + targetWorld);
        Microbot.hopToWorld(targetWorld);
        System.out.println("[BanksShopper] Hop command sent.");

        // Wait for hop to complete
        boolean enteredHoppingState = sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING, 5000);
        System.out.println(enteredHoppingState
            ? "[BanksShopper] Client entered HOPPING state."
            : "[BanksShopper] Client did not enter HOPPING state within timeout.");

        boolean returnedToLoggedIn = sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 10000);
        System.out.println(returnedToLoggedIn
            ? "[BanksShopper] Hop completed and client returned to LOGGED_IN state."
            : "[BanksShopper] Client did not return to LOGGED_IN state within timeout.");

        // Brief pause after hopping
        sleep(1500, 2500);
        System.out.println("[BanksShopper] World hop sequence finished.");
    }

    private void hopWorldWithKeyboardShortcut() {
        Rs2Keyboard.keyHold(KeyEvent.VK_CONTROL);
        Rs2Keyboard.keyHold(KeyEvent.VK_SHIFT);
        Rs2Keyboard.keyHold(KeyEvent.VK_RIGHT);
        sleepGaussian(300, 150);
        Rs2Keyboard.keyRelease(KeyEvent.VK_SHIFT);
        Rs2Keyboard.keyRelease(KeyEvent.VK_CONTROL);
        Rs2Keyboard.keyRelease(KeyEvent.VK_RIGHT);
        System.out.println("Sent world hop shortcut: Ctrl+Shift+Right");
    }

    /**
     * Processes the buy action for the specified item.
     * 
     * @param itemName The name of the item to buy.
     * @param quantity The quantity of the item to buy.
     * @return true if bought successfully, false otherwise.
     */
    private boolean processBuyAction(String itemName, String quantity) {
        if (Rs2Inventory.isFull()) {
            System.out.println("Avoid buying item - Inventory is full");
            return false;
        }

        boolean boughtItem = Rs2Shop.buyItem(itemName, quantity);

        if (boughtItem) {
            Rs2Inventory.waitForInventoryChanges(3000);
        }

        System.out.println(boughtItem ? "Successfully bought " + quantity + " item: " + itemName
                : "Failed to buy " + quantity + " item ID: " + itemName);
        return boughtItem;
    }

    /**
     * Processes the buy action for the specified item.
     * 
     * @param itemID   The ID of the item to buy.
     * @param quantity The quantity of the item to buy.
     * @return true if bought successfully, false otherwise.
     */
    private boolean processBuyAction(int itemID, String quantity) {
        if (Rs2Inventory.isFull()) {
            System.out.println("Avoid buying item - Inventory is full");
            return false;
        }

        boolean boughtItem = Rs2Shop.buyItem(itemID, quantity);

        if (boughtItem) {
            Rs2Inventory.waitForInventoryChanges(3000);
        }

        System.out.println(boughtItem ? "Successfully bought " + quantity + " item ID: " + itemID
                : "Failed to buy " + quantity + " item ID: " + itemID);
        return boughtItem;
    }

    /**
     * Processes the sell action for the specified item.
     * 
     * @param itemName The name of the item to sell.
     * @param quantity The quantity of the item to sell.
     * @return true if sold successfully, false otherwise.
     */
    private boolean processSellAction(String itemName, String quantity) {
        if (Rs2Inventory.hasItem(itemName)) {
            boolean soldItem = Rs2Inventory.sellItem(itemName, quantity);
            System.out.println(soldItem ? "Successfully sold " + quantity + " " + itemName
                    : "Failed to sell " + quantity + " " + itemName);
            return soldItem;
        }
        System.out.println("Item " + itemName + " not found in inventory.");
        return false;
    }

    /**
     * Processes the sell action for the specified item.
     * 
     * @param itemID   The name of the item to sell.
     * @param quantity The quantity of the item to sell.
     * @return true if sold successfully, false otherwise.
     */
    private boolean processSellAction(int itemID, String quantity) {
        if (Rs2Inventory.hasItem(itemID)) {
            boolean soldItem = Rs2Inventory.sellItem(itemID, quantity);
            System.out.println(soldItem ? "Successfully sold " + quantity + ", item ID:" + itemID
                    : "Failed to sell " + quantity + ", item ID: " + itemID);
            return soldItem;
        }
        System.out.println("Item ID" + itemID + " not found in inventory.");
        return false;
    }

    private boolean bankItemsWithoutWalkBack() {
        boolean openedBank = Rs2Bank.isOpen() || Rs2Bank.openBank();

        if (!openedBank) {
            openedBank = Rs2Bank.walkToBankAndUseBank();
        }

        if (!openedBank || !Rs2Bank.isOpen()) {
            return false;
        }

        for (String itemName : plugin.getItemNames()) {
            if (itemName == null || itemName.length() <= 1) {
                continue;
            }

            if (itemName.matches("\\d+")) {
                Rs2Bank.depositAll(Integer.parseInt(itemName));
            } else {
                Rs2Bank.depositAll(itemName);
            }
            sleepGaussian(120, 40);
        }

        if (Rs2Bank.isOpen()) {
            if (plugin.isBlastFurnaceOptimization()) {
                sleepUntil(this::openShopInterface, 5000);
                return true;
            }

            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
        }

        return true;
    }

    /**
     * Fast mode banking: don't close shop/bank interfaces, interact directly by name.
     * Uses walkFastCanvas for short distances, checks if NPC is on screen before walking.
     */
    private boolean bankItemsFastMode() {
        String bankObjectName = plugin.getBankName();

        // Open bank - interact directly by name (Rs2GameObject.interact walks if needed)
        if (!Rs2Bank.isOpen()) {
            boolean interacted = Rs2GameObject.interact(bankObjectName, "Bank") ||
                    Rs2GameObject.interact(bankObjectName, "Use");
            if (!interacted) {
                if (!Rs2Bank.walkToBankAndUseBank()) {
                    System.out.println("[FastMode] Failed to interact with bank: " + bankObjectName);
                    return false;
                }
            }
            if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
                System.out.println("[FastMode] Bank did not open in time.");
                return false;
            }
        }

        // Deposit all items
        Rs2Bank.depositAll();
        sleepGaussian(200, 40);

        // Check if the shop is already interactable from here
        if (plugin.isUseGameObject()) {
            // For game object shops (e.g., Culinaromancer's chest) - try interacting directly
            if (openShopInterface()) {
                sleepUntil(Rs2Shop::isOpen, 5000);
                return true;
            }
        } else {
            // Check if the shop NPC is already on screen - if so, just click it directly
            var shopNpc = Rs2Npc.getNpc(plugin.getNpcName(), plugin.isUseExactNaming());
            if (shopNpc != null && Rs2Camera.isTileOnScreen(shopNpc.getLocalLocation())) {
                sleepUntil(this::openShopInterface, 5000);
                return true;
            }
        }

        // NPC not on screen - walk back to shop location using fast canvas if close enough
        var walkBackLocation = plugin.getShopLocation() != null
                ? plugin.getShopLocation() : initialPlayerLocation;
        if (walkBackLocation != null && Rs2Player.getWorldLocation().distanceTo(walkBackLocation) > 1) {
            int distance = Rs2Player.getWorldLocation().distanceTo(walkBackLocation);
            if (distance <= 12) {
                LocalPoint local = LocalPoint.fromWorld(
                        Microbot.getClient().getTopLevelWorldView(), walkBackLocation);
                if (local != null && Rs2Camera.isTileOnScreen(local)) {
                    Rs2Walker.walkFastCanvas(walkBackLocation);
                } else {
                    Rs2Walker.walkTo(walkBackLocation, 1);
                }
            } else {
                Rs2Walker.walkTo(walkBackLocation, 1);
            }
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(walkBackLocation) <= 2, 10000);
        }

        // Re-open shop
        sleepUntil(this::openShopInterface, 5000);

        return true;
    }
}
