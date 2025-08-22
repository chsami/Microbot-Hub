package net.runelite.client.plugins.microbot.valetotems.utils;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.ValeTotemConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Utility class for inventory management in the Vale Totems minigame
 */
public class InventoryUtils {

    // Fixed item IDs that don't change based on config
    public static final int KNIFE_ID = ItemID.KNIFE;
    public static final int FLETCHING_KNIFE_ID = ItemID.FLETCHING_KNIFE;
    public static final int VALE_OFFERING_ID = 31054;
    public static final int LOG_BASKET_ID = ItemID.LOG_BASKET; // For extended route detection
    // Configuration-dependent item IDs will be retrieved via methods
    private static ValeTotemConfig config;

    /**
     * Set the configuration for this utility class
     * @param config the ValeTotemConfig instance
     */
    public static void setConfig(ValeTotemConfig config) {
        InventoryUtils.config = config;
    }

    /**
     * Get the configured log item ID
     * @return the item ID for the configured log type
     */
    public static int getLogId() {
        if (config == null) {
            return ItemID.YEW_LOGS; // fallback
        }
        return FletchingItemMapper.getLogItemId(config.logType());
    }

    /**
     * Get the configured bow item ID
     * @return the item ID for the configured bow type
     */
    public static int getBowId() {
        if (config == null) {
            return ItemID.YEW_LONGBOW_U; // fallback
        }
        return FletchingItemMapper.getBowItemId(config.logType(), config.bowType());
    }

    /**
     * Gets the ID of the knife in the inventory. Prefers Fletching Knife.
     * @return The item ID of the knife, or -1 if no knife is found.
     */
    public static int getKnifeId() {
        if (Rs2Inventory.hasItem(FLETCHING_KNIFE_ID)) {
            return FLETCHING_KNIFE_ID;
        }
        if (Rs2Inventory.hasItem(KNIFE_ID)) {
            return KNIFE_ID;
        }
        return -1;
    }

    /**
     * Check if player has a knife in inventory
     * @return true if knife is present
     */
    public static boolean hasKnife() {
        return getKnifeId() != -1;
    }

    /**
     * Get the number of configured logs in inventory
     * @return count of configured log type
     */
    public static int getLogCount() {
        return Rs2Inventory.count(getLogId());
    }

    /**
     * Get the number of configured bows in inventory
     * @return count of configured bow type
     */
    public static int getBowCount() {
        return Rs2Inventory.count(getBowId());
    }

    /**
     * Check if player has enough logs for a full round (20 logs minimum)
     * @return true if has 20 or more logs
     */
    public static boolean hasEnoughLogsForFullRound() {
        return getLogCount() >= 20;
    }

    /**
     * Check if player has enough logs for one totem (4 logs minimum)
     * @return true if has 4 or more logs
     */
    public static boolean hasEnoughLogsForOneTotem() {
        return getLogCount() >= 4;
    }

    /**
     * Check if player has the required items to start the minigame
     * @return true if has knife and logs
     */
    public static boolean hasRequiredItems() {
        return hasKnife() && getLogCount() > 0;
    }

    /**
     * Check if player has enough fletched bows for one totem decoration
     * @return true if has 4 or more bows
     */
    public static boolean hasEnoughBowsForOneTotem() {
        return getBowCount() >= 4;
    }

    /**
     * Check if player has enough fletched bows for full round (20 bows)
     * @return true if has 20 or more bows
     */
    public static boolean hasEnoughBowsForFullRound() {
        return getBowCount() >= 20;
    }

    /**
     * Perform fletching operation - use knife on log
     * @return true if fletching action was initiated
     */
    public static boolean startFletching() {
        int knifeId = getKnifeId();
        if (knifeId == -1 || getLogCount() == 0) {
            return false;
        }
        
        // Use knife on log
        return Rs2Inventory.combine(knifeId, getLogId());
    }

    /**
     * Check how many more bows can be fletched with current logs
     * @return number of additional bows that can be made
     */
    public static int getMaxFletchableBows() {
        return getLogCount(); // 1 log = 1 bow
    }

    /**
     * Check how many more logs are needed for a specific number of bows
     * @param targetBows the target number of bows needed
     * @return number of additional logs needed
     */
    public static int getLogsNeededForBows(int targetBows) {
        int currentBows = getBowCount();
        int currentLogs = getLogCount();
        int totalAvailable = currentBows + currentLogs;
        
        return Math.max(0, targetBows - totalAvailable);
    }

    /**
     * Get total fletching materials (logs + existing bows)
     * @return total count of logs and bows combined
     */
    public static int getTotalFletchingMaterials() {
        return getLogCount() + getBowCount();
    }

    /**
     * Check if inventory has space for more items
     * @param additionalItems number of additional items needed
     * @return true if has enough space
     */
    public static boolean hasInventorySpace(int additionalItems) {
        return Rs2Inventory.getEmptySlots() >= additionalItems;
    }

    /**
     * Get the number of vale offerings in inventory
     * @return count of vale offerings
     */
    public static int getValeOfferingCount() {
        return Rs2Inventory.itemQuantity(VALE_OFFERING_ID);
    }

    /**
     * Check if inventory is full
     * @return true if no empty slots
     */
    public static boolean isInventoryFull() {
        return Rs2Inventory.isFull();
    }

    /**
     * Get a summary of current inventory state for the minigame
     * @return formatted string with inventory status
     */
    public static String getInventorySummary() {
        String fletchingType = config != null ? 
            FletchingItemMapper.getFletchingDescription(config.logType(), config.bowType()) : 
            "Yew Longbow (u)";
        
        return String.format("Knife: %s | Logs: %d | %s: %d | Offerings: %d | Slots: %d/%d",
                hasKnife() ? "✓" : "✗",
                getLogCount(),
                fletchingType,
                getBowCount(),
                getValeOfferingCount(),
                28 - Rs2Inventory.getEmptySlots(),
                28);
    }

    /**
     * Calculate optimal banking strategy
     * @return number of logs to withdraw for optimal inventory management
     */
    public static int getOptimalLogWithdrawAmount() {
        // Keep 1 slot for knife, rest for logs
        // Consider existing bows and logs
        int availableSlots = 27; // 28 - 1 for knife
        
        // Withdraw enough for full round (25 total) but don't exceed inventory
        int targetTotal = 27;
        int needed = Math.max(0, targetTotal);
        
        return Math.min(needed, availableSlots);
    }

    /**
     * Check if ready for banking (low on materials)
     * @return true if should return to bank
     */
    public static boolean shouldReturnToBank() {
        // Return to bank if we don't have enough materials for even one totem
        return getTotalFletchingMaterials() < 4 || !hasKnife();
    }

    /**
     * Check if player has a log basket in inventory
     * @return true if log basket is present
     */
    public static boolean hasLogBasket() {
        return Rs2Inventory.hasItem(LOG_BASKET_ID);
    }

    /**
     * Get the number of logs stored in the log basket from GameSession tracking
     * @param gameSession the game session that tracks log basket contents
     * @return number of logs in basket, 0 if no session or basket not found
     */
    public static int getLogBasketLogCount(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        if (gameSession == null || !hasLogBasket()) {
            return 0;
        }
        return gameSession.getLogBasketLogCount();
    }

    /**
     * Check if log basket has enough logs for extended route
     * @param gameSession the game session that tracks log basket contents
     * @return true if basket has sufficient logs for 8 totems
     */
    public static boolean hasLogBasketSufficientLogs(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        // For extended route, we need logs for 8 totems (40 total minimum)
        return getLogBasketLogCount(gameSession) >= 40;
    }

    /**
     * Withdraw logs from log basket by emptying it
     * This will empty as many logs as possible based on available inventory space
     * @param gameSession the game session to update log basket tracking
     * @return true if emptying was successful
     */
    public static boolean emptyLogBasket(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        try {
            if (gameSession == null) {
                System.err.println("GameSession is null - cannot track log basket");
                return false;
            }
            
            if (!hasLogBasket()) {
                System.err.println("No log basket in inventory to empty");
                return false;
            }

            boolean interacted = false;
            boolean empty = false;
            int logsInInventoryStart = getLogCount();

            int logsInBasket = gameSession.getLogBasketLogCount();
            if (logsInBasket == 0) {
                System.out.println("Log basket is already empty");
                return true;
            }
            
            if (Rs2Bank.isOpen()) {
                int logsInBankStart = Rs2Bank.count(getLogId());
                System.out.println("Emptying log basket with " + logsInBasket + " logs");
                empty = Rs2Inventory.interact(LOG_BASKET_ID, "Empty");
                if (!sleepUntil(() -> Rs2Bank.count(getLogId()) > logsInBankStart, 3000)) {
                    System.out.println("Failed to empty log basket, or basket was empty, retrying...");
                    empty = Rs2Inventory.interact(LOG_BASKET_ID, "Empty"); // One retry
                }
            } else {
                int availableSpace = Rs2Inventory.emptySlotCount();
                if (availableSpace == 0) {
                    System.err.println("No inventory space to empty log basket");
                    return false;
                }

                // Interact with log basket to empty it
                if (Rs2Inventory.interact(LOG_BASKET_ID, "Check")) {
                    Rs2Dialogue.sleepUntilHasContinue();
                    sleepGaussian(200, 100);
                    Rs2Dialogue.clickContinue();
                    Rs2Dialogue.sleepUntilHasDialogueOption("Yes");
                    sleepGaussian(200, 100);
                    Rs2Dialogue.keyPressForDialogueOption("Yes");
                    Rs2Inventory.waitForInventoryChanges(1000);
                    sleepGaussian(400, 200);
                    interacted = true;
                }
            }

            int logsInInventoryEnd = getLogCount();

            if (empty) {
                // Wait for the emptying to complete
                sleep(500, 800);
                // Update the game session tracking - empty the basket
                gameSession.emptyLogBasket();
                return true;
            } else if (interacted) {
                gameSession.removeLogsFromBasket(logsInInventoryEnd - logsInInventoryStart);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error emptying log basket: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fill log basket with logs from inventory
     * This will fill as many logs as possible based on basket capacity and available logs
     * @param gameSession the game session to update log basket tracking
     * @return true if filling was successful
     */
    public static boolean fillLogBasket(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        try {
            if (gameSession == null) {
                System.err.println("GameSession is null - cannot track log basket");
                return false;
            }
            
            if (!hasLogBasket()) {
                System.err.println("No log basket in inventory to fill");
                return false;
            }

            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                sleep(500, 800);
            }
            
            int currentLogsInBasket = gameSession.getLogBasketLogCount();
            int maxBasketCapacity = 28;
            int availableBasketSpace = maxBasketCapacity - currentLogsInBasket;
            
            if (availableBasketSpace == 0) {
                System.out.println("Log basket is already full");
                return true;
            }
            
            int logsInInventory = getLogCount();
            if (logsInInventory == 0) {
                System.out.println("No logs in inventory to fill basket with");
                return true;
            }
            
            System.out.println("Filling log basket (current: " + currentLogsInBasket + "/28, inventory logs: " + logsInInventory + ")");

            int logsInInventoryStart = getLogCount();

            sleep(200,600);
            
            // Interact with log basket to fill it
            boolean interacted = Rs2Inventory.interact(LOG_BASKET_ID, "Fill");
            if (interacted) {
                // Wait for the filling to complete
                sleep(700, 1200);

                int logsInInventoryEnd = getLogCount();

                int difference = logsInInventoryStart - logsInInventoryEnd;

                if (difference == 0) {
                    System.out.println("No logs were filled into the basket");
                    return false;
                }

                gameSession.addLogsToBasket(difference);
                
                // Update the game session tracking - add the logs that were filled
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error filling log basket: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get optimal log amount for extended route
     * @param gameSession the game session that tracks log basket contents
     * @return number of logs to manage for 8 totems
     */
    public static int getOptimalLogAmountForExtendedRoute(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        int totalLogsNeeded = 40; // 8 totems * 5 logs each
        int currentLogs = getLogCount();
        int basketLogs = getLogBasketLogCount(gameSession);
        int totalCurrentLogs = currentLogs + basketLogs;
        
        // Calculate how many more logs we need
        int logsNeeded = Math.max(0, totalLogsNeeded - totalCurrentLogs);
        
        // Don't exceed inventory capacity (leaving room for knife and basket)
        int maxCanTake = 26; // 28 slots - knife - basket
        
        return Math.min(logsNeeded, maxCanTake);
    }

    public static int getOptimalLogBasketLogAmountForExtendedRoute(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        int totalLogsNeeded = 40 - 26; // 8 totems * 5 logs each - we will hold 26 in inventory
        int basketLogs = getLogBasketLogCount(gameSession);
        
        // Calculate how many more logs we need
        int logsNeeded = Math.max(0, totalLogsNeeded - basketLogs);
        
        // Don't exceed inventory capacity (leaving room for knife and basket)
        int maxCanTake = 26; // 28 slots - knife - basket
        
        return Math.min(logsNeeded, maxCanTake);
    }

    /**
     * Check if we have enough materials for extended route
     * @param gameSession the game session that tracks log basket contents
     * @return true if sufficient materials for 8 totems
     */
    public static boolean hasEnoughMaterialsForExtendedRoute(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        if (!hasLogBasket()) return false;
        
        int totalLogs = getLogCount() + getLogBasketLogCount(gameSession);
        int totalBows = getBowCount();
        int totalMaterials = totalLogs + totalBows;
        
        // Need materials for 8 totems (40 total)
        return totalMaterials >= 40 && hasKnife();
    }

    /**
     * Check if log basket has available capacity
     * @param gameSession the game session that tracks log basket contents
     * @return true if basket can hold more logs
     */
    public static boolean hasLogBasketSpace(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        if (!hasLogBasket() || gameSession == null) {
            return false;
        }
        return gameSession.hasLogBasketSpace();
    }

    /**
     * Get available space in log basket
     * @param gameSession the game session that tracks log basket contents
     * @return number of logs that can be added to basket
     */
    public static int getLogBasketAvailableSpace(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        if (!hasLogBasket() || gameSession == null) {
            return 0;
        }
        return gameSession.getLogBasketAvailableSpace();
    }

    /**
     * Check if we should empty the log basket (for initial banking or log type change)
     * @param gameSession the game session that tracks log basket contents
     * @return true if basket should be emptied
     */
    public static boolean shouldEmptyLogBasket(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        // Always empty basket if it has logs and we're starting a new banking session
        // This ensures we start fresh with the correct log type
        return hasLogBasket() && gameSession != null && gameSession.getLogBasketLogCount() > 0;
    }
} 