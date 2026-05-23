package net.runelite.client.plugins.microbot.banksshopper;

import net.runelite.api.GameState;
import net.runelite.api.coords.LocalPoint;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2BankID;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
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
    private WorldPoint shopNpcLocation;

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
        shopNpcLocation = null;
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

                        if (!Rs2Shop.isOpen() && !ensureShopOpen()) return;

                        boolean successfullAction = false;
                        boolean outOfStock = false;
                        if (Rs2Shop.isOpen()) {
                            // First successful interaction pins the return-trip target to the
                            // NPC's actual tile, not whatever tile the user toggled the plugin on.
                            if (shopNpcLocation == null) {
                                Rs2NpcModel npc = Rs2Shop.getNearestShopNpc(plugin.getNpcName(), plugin.isUseExactNaming());
                                if (npc != null) shopNpcLocation = npc.getWorldLocation();
                            }
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
        shopNpcLocation = null;

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    /**
     * Fast-path bank deposit: if a bank booth or banker is loaded in the current scene
     * and the pathfinder can reach its tile, click it directly so the game server handles
     * pathing — no Rs2Walker invocation. After depositing, only invoke the walker for the
     * return trip if the shop NPC isn't itself loaded + reachable from the new position.
     *
     * @return true when the bank was opened + deposited via direct interaction; false to
     *         signal the caller should fall back to the walker-based round trip.
     */
    private boolean tryDirectBankDeposit() {
        // Closest in-scene Rs2BankID-matched object — booths use "Bank", chests use "Use",
        // deposit boxes use "Deposit". Same gate the agent server uses ("reachable" = same
        // WorldView as player): if Rs2GameObject.getAll returns it, it's in scene. Clicking
        // the default left-click action lets the server walk the player to an adjacent tile
        // and open the appropriate UI (bank widget or deposit-box widget).
        boolean alreadyOpen = Rs2Bank.isOpen() || Rs2DepositBox.isOpen();
        if (!alreadyOpen) {
            TileObject bankObject = Rs2GameObject.getAll(o -> Rs2BankID.BANK_ID_SET.contains(o.getId())).stream()
                    .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                    .orElse(null);

            if (bankObject != null) {
                Microbot.log("[BanksShopper] direct-click bank id=" + bankObject.getId()
                        + " at " + bankObject.getWorldLocation()
                        + " from " + Rs2Player.getWorldLocation());
                if (!Rs2GameObject.interact(bankObject)) return false;
                if (!sleepUntil(() -> Rs2Bank.isOpen() || Rs2DepositBox.isOpen(), 15_000)) {
                    Microbot.log("[BanksShopper] click sent but no bank/deposit UI opened");
                    return false;
                }
            } else {
                Rs2NpcModel banker = Rs2Npc.getBankerNPC();
                if (banker == null || !Rs2Bank.openBank(banker)) return false;
            }
        }

        if (Rs2Bank.isOpen()) {
            for (String itemName : plugin.getItemNames()) {
                if (itemName.matches("\\d+")) {
                    Rs2Bank.depositAll(Integer.parseInt(itemName));
                } else {
                    Rs2Bank.depositAll(itemName, false);
                }
            }
            Rs2Bank.closeBank();
        } else if (Rs2DepositBox.isOpen()) {
            for (String itemName : plugin.getItemNames()) {
                if (itemName.matches("\\d+")) {
                    Rs2DepositBox.depositAll(Integer.parseInt(itemName));
                } else {
                    Rs2DepositBox.depositAll(Collections.singletonList(itemName));
                }
            }
            Rs2DepositBox.closeDepositBox();
        } else {
            return false;
        }

        // Return-trip walker is owned by SHOPPING (see ensureShopOpen): drive only when the
        // NPC is missing from the loaded scene, then cancel + await so the visual route
        // disappears immediately and the next SHOPPING tick can issue a clean Trade click.
        return true;
    }

    /**
     * One-shot "open the shop" gate for the SHOPPING state. Handles the two failure modes
     * separately so we never re-click while waiting:
     * <ul>
     *   <li>NPC not loaded in the scene → start a background {@code Rs2Walker.walkTo}
     *       toward {@link #shopNpcLocation} (captured on the first trade), poll for the
     *       NPC to enter the scene, then {@code clearWalkingRoute} + await the async
     *       thread so the ShortestPath overlay clears and no late {@code currentTarget}
     *       write resurrects the route.</li>
     *   <li>NPC in scene but shop UI not yet open → send exactly one
     *       {@code Rs2Npc.interact(npc, "Trade")} and wait. The previous polling pattern
     *       ({@code sleepUntil(() -> Rs2Shop.openShop(...), 5000)}) re-invoked
     *       {@code Rs2Npc.interact} every scheduler tick, queuing redundant Trade clicks
     *       while the player was still walking to the NPC.</li>
     * </ul>
     * @return true when the shop UI is open and the caller can proceed with trades;
     *         false to defer to the next scheduler tick.
     */
    private boolean ensureShopOpen() {
        Rs2NpcModel npc = Rs2Shop.getNearestShopNpc(plugin.getNpcName(), plugin.isUseExactNaming());
        if (npc == null) {
            WorldPoint walkTarget = shopNpcLocation != null ? shopNpcLocation : initialPlayerLocation;
            if (walkTarget == null) return false;

            CompletableFuture<Void> walkFuture = CompletableFuture.runAsync(
                    () -> Rs2Walker.walkTo(walkTarget, 4));
            sleepUntil(() -> Rs2Shop.getNearestShopNpc(plugin.getNpcName(), plugin.isUseExactNaming()) != null
                            || Rs2Player.getWorldLocation().distanceTo(walkTarget) <= 4,
                    30_000);
            Rs2Walker.clearWalkingRoute("banksshopper:shop-npc-in-range");
            try {
                walkFuture.get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // walker thread already exited (most common) or interrupted — either way
                // we've cleared the route; let the next tick retry.
            }
            return false;
        }

        Rs2Npc.interact(npc, "Trade");
        sleepUntil(Rs2Shop::isOpen, 5000);
        return Rs2Shop.isOpen();
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
