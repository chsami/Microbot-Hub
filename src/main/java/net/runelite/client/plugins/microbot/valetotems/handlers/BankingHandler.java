package net.runelite.client.plugins.microbot.valetotems.handlers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.ValeTotemConfig;
import net.runelite.client.plugins.microbot.valetotems.enums.BankLocation;
import net.runelite.client.plugins.microbot.valetotems.enums.GameObjectId;
import net.runelite.client.plugins.microbot.valetotems.enums.GameState;
import net.runelite.client.plugins.microbot.valetotems.utils.CoordinateUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.GameObjectUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.InventoryUtils;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

import java.util.Random;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.microbot.valetotems.models.GameSession;
import org.slf4j.event.Level;

/**
 * Handles all banking operations for the Vale Totems minigame
 */
public class BankingHandler {

    private static final Random random = new Random();
    private static final double SHORT_AFK_PROBABILITY = 0.40; // 40% chance
    private static final double MEDIUM_AFK_PROBABILITY = 0.30; // 30% chance
    private static final double LONG_AFK_PROBABILITY = 0.10; // 10% chance

    private static final int BANK_INTERACTION_DISTANCE = 5;
    private static final long BANK_TIMEOUT_MS = 10000; // 10 seconds
    
    // Configuration instance
    private static ValeTotemConfig config;

    /**
     * Set the configuration for this handler
     * @param config the ValeTotemConfig instance
     */
    public static void setConfig(ValeTotemConfig config) {
        BankingHandler.config = config;
    }

    /**
     * Navigate to the bank and position correctly for banking
     * @return true if successfully positioned at bank
     */
    public static boolean navigateToBank() {
        try {
            // Check if already near bank
            if (CoordinateUtils.isNearBank(BANK_INTERACTION_DISTANCE)) {
                // Move to optimal banking position if not there
                if (!CoordinateUtils.isAtBankingPosition()) {
                    return Rs2Walker.walkTo(BankLocation.PLAYER_STANDING_TILE.getLocation());
                }
                return true;
            }

            // Walk to bank area
            boolean walked = Rs2Walker.walkTo(BankLocation.BANK_BOOTH.getLocation());
            if (walked) {
                // Wait for arrival and position correctly
                Rs2Player.waitForWalking();
                return Rs2Walker.walkTo(BankLocation.PLAYER_STANDING_TILE.getLocation());
            }

            return false;
        } catch (Exception e) {
            Microbot.log("Error navigating to bank: " + e.getMessage());
            return false;
        }
    }

    /**
     * Open the bank interface
     * @return true if bank interface is open
     */
    public static boolean openBank() {
        try {
            // Check if bank is already open
            if (Rs2Bank.isOpen()) {
                return true;
            }

            // Ensure we're close enough to the bank
            if (!CoordinateUtils.isNearBank(BANK_INTERACTION_DISTANCE)) {
                if (!navigateToBank()) {
                    return false;
                }
            }

            // Interact with bank booth using string search
            boolean interacted = GameObjectUtils.findAndInteractAtLocationByName(
                    GameObjectId.BANK_BOOTH.getSearchTerm(),
                    BankLocation.BANK_BOOTH.getLocation(),
                    "Bank"
            );

            if (interacted) {
                // Wait for bank to open
                long startTime = System.currentTimeMillis();
                while (!Rs2Bank.isOpen() && System.currentTimeMillis() - startTime < BANK_TIMEOUT_MS) {
                    sleep(100);
                }
                return Rs2Bank.isOpen();
            }

            return false;
        } catch (Exception e) {
            Microbot.log("Error opening bank: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deposit all items except knife
     * @return true if successfully deposited items
     */
    public static boolean depositAllExceptKnife() {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                return false;
            }

            // Deposit all except knife
            boolean deposited = Rs2Bank.depositAllExcept(InventoryUtils.KNIFE_ID, InventoryUtils.FLETCHING_KNIFE_ID);
            
            if (deposited) {
                sleep(600); // Wait for deposit animation
            }

            return true;
        } catch (Exception e) {
            Microbot.log("Error depositing items: " + e.getMessage());
            return false;
        }
    }

        /**
     * Withdraw the required items for the minigame
     * Note: This function assumes materials have already been pre-checked
     * @param gameSession the game session to update if critical errors occur
     * @return true if successfully withdrew required items
     */
    public static boolean withdrawRequiredItems(GameSession gameSession) {
        try {
            // Bank should already be open from prior checks, but ensure it is
            if (!Rs2Bank.isOpen() && !openBank()) {
                Microbot.log("Cannot withdraw items - failed to open bank");
                return false;
            }

            // Calculate how many logs to withdraw
            int logsToWithdraw = InventoryUtils.getOptimalLogWithdrawAmount();

            if (logsToWithdraw <= 0) {
                Microbot.log("Already have sufficient logs in inventory - no withdrawal needed");
                return true; // Already have enough materials
            }

            // Get the configured log type ID
            int logId = InventoryUtils.getLogId();
            String logTypeName = config != null ? config.logType().getDisplayName() : "Yew Logs";

            // Withdraw logs (materials check already performed, so this should succeed)
            Microbot.log("Withdrawing " + logsToWithdraw + " " + logTypeName + " from bank");
            boolean withdrew = Rs2Bank.withdrawX(logId, logsToWithdraw);
            if (withdrew) {
                Microbot.log("Successfully withdrew " + logsToWithdraw + " " + logTypeName);
                sleep(1000); // Wait for withdraw
            } else {
                Microbot.log("Failed to withdraw logs from bank");
                return false;
            }

            // Final verification
            boolean hasRequired = InventoryUtils.hasRequiredItems();
            if (hasRequired) {
                Microbot.log("Successfully completed item withdrawal - all required items now in inventory");
            } else {
                Microbot.log("Warning: Required items verification failed after withdrawal");
            }

            return hasRequired;

        } catch (Exception e) {
            Microbot.log("Error withdrawing required items: " + e.getMessage());
            return false;
        }
    }

    public static boolean fixZoom() {
        try {
            Rs2Camera.setZoom(0);
            return true;
        } catch (Exception e) {
            Microbot.log("Error fixing zoom: " + e.getMessage());
            return false;
        }
    }

    /**
     * Detect which route type to use based on inventory and bank contents
     * This method ensures the bank is open before checking for log basket
     * @return RouteType.EXTENDED if log basket is present, RouteType.STANDARD otherwise
     */
    public static TotemLocation.RouteType detectRouteTypeWithBankOpen() {
        try {
            // Check inventory first
            if (InventoryUtils.hasLogBasket()) {
                Microbot.log("Log basket detected in inventory - enabling Extended Route (8 totems)");
                return TotemLocation.RouteType.EXTENDED;
            }

            // Ensure bank is open before checking
            if (!Rs2Bank.isOpen() && !openBank()) {
                Microbot.log("Could not open bank for route detection - defaulting to Standard Route");
                return TotemLocation.RouteType.STANDARD;
            }

            // Check bank for log basket
            if (Rs2Bank.hasItem(InventoryUtils.LOG_BASKET_ID)) {
                Microbot.log("Log basket detected in bank - enabling Extended Route (8 totems)");
                return TotemLocation.RouteType.EXTENDED;
            }

            Microbot.log("No log basket detected in inventory or bank - using Standard Route (5 totems)");
            return TotemLocation.RouteType.STANDARD;

        } catch (Exception e) {
            Microbot.log("Error detecting route type: " + e.getMessage());
            Microbot.log("Defaulting to Standard Route due to error");
            return TotemLocation.RouteType.STANDARD;
        }
    }

    /**
     * Perform a unified banking cycle that detects route type and calls appropriate banking method
     * @param gameSession the game session to update with detected route type
     * @return true if banking cycle completed successfully
     */
    public static boolean performUnifiedBankingCycle(GameSession gameSession) {
        try {
            // Navigate to bank if needed
            if (!CoordinateUtils.isNearBank(BANK_INTERACTION_DISTANCE)) {
                if (!navigateToBank()) {
                    Microbot.log("Failed to navigate to bank");
                    return false;
                }
            }

            // Open bank first
            if (!openBank()) {
                Microbot.log("Failed to open bank");
                return false;
            }

            // Now detect route type with bank open
            TotemLocation.RouteType detectedRouteType = detectRouteTypeWithBankOpen();
            
            // Update game session with detected route type
            if (gameSession.getCurrentRouteType() != detectedRouteType) {
                gameSession.setRouteType(detectedRouteType);
            }

            // Handle random AFK
            handleRandomAFK();

            boolean bankingSuccess = false;
            
            // Call appropriate banking method based on detected route type
            if (detectedRouteType == TotemLocation.RouteType.EXTENDED) {
                Microbot.log("Performing extended route banking cycle");
                bankingSuccess = performExtendedRouteBankingOperations(gameSession);
            } else {
                Microbot.log("Performing standard route banking cycle");
                bankingSuccess = performStandardBankingOperations(gameSession);
            }

            if (bankingSuccess) {
                // Close bank
                Rs2Bank.closeBank();
                fixZoom();
                Microbot.log("Banking cycle completed successfully using " + detectedRouteType.getDescription());
            }

            return bankingSuccess;

        } catch (Exception e) {
            Microbot.log("Error during unified banking cycle: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform standard route banking operations (assumes bank is already open)
     * @return true if standard banking operations completed successfully
     */
    private static boolean performStandardBankingOperations(GameSession gameSession) {
        try {
            // Step 1: Ensure we have a knife in inventory
            if (!ensureKnifeInInventory(gameSession)) {
                Microbot.log("Failed to ensure knife availability for standard route");
                return false;
            }

            // Step 2: Deposit all except knife
            if (!depositAllExceptKnife()) {
                Microbot.log("Failed to deposit items for standard route");
                return false;
            }

            // Step 3: Check if we have enough materials
            if (!hasSufficientMaterials(1)) {
                Microbot.log("Not enough materials for standard route - stopping bot");
                gameSession.setState(GameState.STOPPING);
                return false;
            }

            // Step 4: Withdraw required items
            if (!withdrawRequiredItems(gameSession)) {
                Microbot.log("Failed to withdraw required items for standard route");
                return false;
            }

            return true;
        } catch (Exception e) {
            Microbot.log("Error during standard banking operations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform extended route banking operations (assumes bank is already open)
     * @return true if extended banking operations completed successfully
     */
    private static boolean performExtendedRouteBankingOperations(GameSession gameSession) {
        try {
            Microbot.log("Starting extended route banking operations");

            // Step 1: Ensure we have knife and log basket in inventory
            if (!ensureKnifeAndLogBasketInInventory(gameSession)) {
                Microbot.log("Failed to ensure knife and log basket are in inventory");
                return false;
            }

            // Step 2: Empty log basket if this is initial banking or log type changed
            if (InventoryUtils.shouldEmptyLogBasket(gameSession)) {
                Microbot.log("Emptying log basket for fresh start");
                if (!InventoryUtils.emptyLogBasket(gameSession)) {
                    Microbot.log("Failed to empty log basket");
                    return false;
                }
            }

            // Step 3: Check if we have enough materials for extended route
            if (!hasSufficientMaterialsForExtendedRoute(gameSession, 1)) {
                Microbot.log("Not enough materials for extended route - stopping bot");
                gameSession.setState(GameState.STOPPING);
                return false;
            }

            // Step 4: Deposit all items except knife and log basket
            if (!depositAllExceptKnifeAndLogBasket()) {
                Microbot.log("Failed to deposit items while preserving knife and log basket");
                return false;
            }

            // Step 5: Perform log basket filling operation
            if (!performLogBasketFillingOperation(gameSession)) {
                Microbot.log("Failed to perform log basket filling operation");
                return false;
            }

            Microbot.log("Extended route banking operations completed successfully");
            return true;

        } catch (Exception e) {
            Microbot.log("Error during extended route banking operations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles random AFK scenarios while banking
     */
    private static void handleRandomAFK() {
        double roll = random.nextDouble(); // Generates a value between 0.0 and 1.0

        if (roll < LONG_AFK_PROBABILITY) {
            // Long AFK: 30 to 90 seconds
            Microbot.log("Performing a long AFK...");
            Microbot.status = "Long AFK";
            Rs2Antiban.moveMouseOffScreen(90);
            sleep(30000, 90000);
        } else if (roll < MEDIUM_AFK_PROBABILITY) {
            // Medium AFK: 10 to 30 seconds
            Microbot.log("Performing a medium AFK...");
            Microbot.status = "Medium AFK";
            Rs2Antiban.moveMouseOffScreen(90);
            sleep(10000, 30000);
        } else if (roll < SHORT_AFK_PROBABILITY) {
            // Short AFK: 2 to 8 seconds
            Microbot.log("Performing a short AFK...");
            Microbot.status = "Short AFK";
            Rs2Antiban.moveMouseOffScreen(60);
            sleep(2000, 8000);
        }
    }

    /**
     * Check if we have enough materials in bank for continued operation
     * @param roundsPlanned number of rounds we want to complete
     * @return true if bank has sufficient materials
     */
    public static boolean hasSufficientMaterials(int roundsPlanned) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                Microbot.log("Unable to check materials - cannot open bank");
                return false;
            }

            boolean hasSufficientMaterials = true;
            StringBuilder shortageDetails = new StringBuilder();

            // Check for knife
            boolean hasKnifeAvailable = InventoryUtils.hasKnife() ||
                Rs2Bank.hasItem(InventoryUtils.KNIFE_ID) ||
                Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID);

            if (!hasKnifeAvailable) {
                hasSufficientMaterials = false;
                shortageDetails.append("• No knife available (inventory or bank)\n");
            }

            // Check for logs (25 logs per round for standard route)
            int logsNeeded = roundsPlanned * 25;
            int currentLogs = InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();
            int bankLogs = Rs2Bank.count(logId);
            int totalLogsAvailable = currentLogs + bankLogs;

            if (totalLogsAvailable < logsNeeded) {
                hasSufficientMaterials = false;
                String logTypeName = config != null ? config.logType().getDisplayName() : "Yew Logs";
                shortageDetails.append(String.format("• Logs: Have %d, Need %d (%s)\n",
                    totalLogsAvailable, logsNeeded, logTypeName));
            }

            // Log material status
            if (hasSufficientMaterials) {
                Microbot.log("Material check passed - sufficient materials for " + roundsPlanned + " rounds");
            } else {
                Microbot.log("=== MATERIAL SHORTAGE DETECTED ===");
                Microbot.log("Standard Route Requirements:");
                Microbot.log(shortageDetails.toString().trim());
                Microbot.log("==================================");
                Microbot.showMessage("Material Shortage: " + shortageDetails.toString().trim());
            }

            return hasSufficientMaterials;

        } catch (Exception e) {
            Microbot.log("Error checking bank materials: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle critical material shortage by setting game state to STOPPING
     * @param gameSession the game session to update
     * @param missingItem description of what material is missing
     */
    private static void handleCriticalMaterialShortage(GameSession gameSession, String missingItem) {
        // Log critical error with clear formatting
        Microbot.log("=== CRITICAL MATERIAL SHORTAGE DETECTED ===");
        Microbot.log("Issue: " + missingItem);
        Microbot.log("Bot Status: Stopping due to insufficient materials");
        Microbot.log("Action Required: Please restock materials in bank before restarting");
        Microbot.log("=============================================");

        // Update game session state
        gameSession.setState(GameState.STOPPING);
    }


    /**
     * Check if we have sufficient materials in bank for extended route
     * @param gameSession the game session that tracks log basket contents
     * @param roundsPlanned number of extended route rounds planned
     * @return true if bank has sufficient materials for extended route
     */
    public static boolean hasSufficientMaterialsForExtendedRoute(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession, int roundsPlanned) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                Microbot.log("Unable to check materials for extended route - cannot open bank");
                return false;
            }

            boolean hasSufficientMaterials = true;
            StringBuilder shortageDetails = new StringBuilder();

            // Check for knife
            boolean hasKnifeAvailable = InventoryUtils.hasKnife() ||
                Rs2Bank.hasItem(InventoryUtils.KNIFE_ID) ||
                Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID);

            if (!hasKnifeAvailable) {
                hasSufficientMaterials = false;
                shortageDetails.append("• No knife available (inventory or bank)\n");
            }

            // Check for log basket
            boolean hasLogBasket = InventoryUtils.hasLogBasket();
            if (!hasLogBasket) {
                hasSufficientMaterials = false;
                shortageDetails.append("• No log basket available (inventory or bank)\n");
            }

            // Check for logs (40 logs per extended route round - 8 totems * 5 each)
            int logsNeeded = roundsPlanned * 40;
            int currentLogs = InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();
            int bankLogs = Rs2Bank.count(logId);
            int logBasketLogs = InventoryUtils.getLogBasketLogCount(gameSession);
            int totalLogsAvailable = currentLogs + bankLogs + logBasketLogs;

            if (totalLogsAvailable < logsNeeded) {
                hasSufficientMaterials = false;
                String logTypeName = config != null ? config.logType().getDisplayName() : "Yew Logs";
                shortageDetails.append(String.format("• Logs: Have %d (inventory: %d, bank: %d, basket: %d), Need %d (%s)\n",
                    totalLogsAvailable, currentLogs, bankLogs, logBasketLogs, logsNeeded, logTypeName));
            }

            // Log material status
            if (hasSufficientMaterials) {
                Microbot.log("Extended route material check passed - sufficient materials");
            } else {
                Microbot.log("=== MATERIAL SHORTAGE DETECTED (EXTENDED ROUTE) ===");
                Microbot.log("Extended Route Requirements (8 totems per round):");
                Microbot.log(shortageDetails.toString().trim());
                Microbot.log("==================================================");
                Microbot.showMessage("Material Shortage: " + shortageDetails.toString().trim());
            }

            return hasSufficientMaterials;

        } catch (Exception e) {
            Microbot.log("Error checking bank materials for extended route: " + e.getMessage());
            return false;
        }
    }

    /**
     * Withdraw log basket from bank if available and not in inventory
     * @return true if log basket is now available (either was in inventory or successfully withdrawn)
     */
    public static boolean ensureLogBasketAvailable() {
        try {
            // Check if already in inventory
            if (InventoryUtils.hasLogBasket()) {
                return true;
            }
            
            // Check if bank is open
            if (!Rs2Bank.isOpen() && !openBank()) {
                return false;
            }
            
            // Check if log basket is in bank and withdraw it
            if (Rs2Bank.hasItem(InventoryUtils.LOG_BASKET_ID)) {
                Microbot.log("Withdrawing log basket from bank for extended route");
                boolean withdrew = Rs2Bank.withdrawOne(InventoryUtils.LOG_BASKET_ID);
                if (withdrew) {
                    sleep(600); // Wait for withdrawal
                    return InventoryUtils.hasLogBasket();
                } else {
                    Microbot.log("Failed to withdraw log basket from bank");
                    return false;
                }
            }

            Microbot.log("No log basket found in bank");
            return false;
            
        } catch (Exception e) {
            Microbot.log("Error ensuring log basket availability: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deposit all items except knife and log basket (for extended route)
     * @return true if successfully deposited items
     */
    public static boolean depositAllExceptKnifeAndLogBasket() {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                return false;
            }

            // Deposit all except knife and log basket
            boolean deposited = Rs2Bank.depositAllExcept(
                InventoryUtils.KNIFE_ID, 
                InventoryUtils.FLETCHING_KNIFE_ID,
                InventoryUtils.LOG_BASKET_ID
            );
            
            if (deposited) {
                sleep(600); // Wait for deposit animation
            }

            return true;
        } catch (Exception e) {
            Microbot.log("Error depositing items (preserving knife and log basket): " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensure knife is available in inventory (prioritizes fletching knife)
     * @param gameSession the game session to update if critical errors occur
     * @return true if knife is available in inventory
     */
    private static boolean ensureKnifeInInventory(GameSession gameSession) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                Microbot.log("Cannot ensure knife availability - failed to open bank");
                return false;
            }

            // Check if we already have a knife in inventory
            if (InventoryUtils.hasKnife()) {
                return true;
            }

            // Try to withdraw fletching knife first (prioritized)
            if (Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID)) {
                Microbot.log("Withdrawing Fletching knife from bank (prioritized)");
                if (Rs2Bank.withdrawOne(InventoryUtils.FLETCHING_KNIFE_ID)) {
                    sleep(600);
                    if (InventoryUtils.hasKnife()) {
                        Microbot.log("Successfully withdrew Fletching knife");
                        return true;
                    }
                } else {
                    Microbot.log("Failed to withdraw Fletching knife from bank");
                }
            }

            // Try to withdraw regular knife as fallback
            if (Rs2Bank.hasItem(InventoryUtils.KNIFE_ID)) {
                Microbot.log("Withdrawing regular knife from bank");
                if (Rs2Bank.withdrawOne(InventoryUtils.KNIFE_ID)) {
                    sleep(600);
                    if (InventoryUtils.hasKnife()) {
                        Microbot.log("Successfully withdrew regular knife");
                        return true;
                    }
                } else {
                    Microbot.log("Failed to withdraw regular knife from bank");
                }
            }

            // No knife found anywhere - critical error
            handleCriticalMaterialShortage(gameSession, "No knife available (checked inventory and bank for both Fletching knife and regular knife)");
            return false;

        } catch (Exception e) {
            Microbot.log("Error ensuring knife in inventory: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensure knife and log basket are in inventory (for extended route)
     * @param gameSession the game session to update if critical errors occur
     * @return true if both items are available in inventory
     */
    private static boolean ensureKnifeAndLogBasketInInventory(GameSession gameSession) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                Microbot.log("Cannot ensure knife and log basket availability - failed to open bank");
                return false;
            }

            // Ensure we have a knife (prioritizing fletching knife)
            if (!InventoryUtils.hasKnife()) {
                if (Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID)) {
                    Microbot.log("Withdrawing Fletching knife from bank (prioritized)");
                    if (!Rs2Bank.withdrawOne(InventoryUtils.FLETCHING_KNIFE_ID)) {
                        Microbot.log("Failed to withdraw Fletching knife");
                        return false;
                    }
                } else if (Rs2Bank.hasItem(InventoryUtils.KNIFE_ID)) {
                    Microbot.log("Withdrawing regular knife from bank");
                    if (!Rs2Bank.withdrawOne(InventoryUtils.KNIFE_ID)) {
                        Microbot.log("Failed to withdraw knife");
                        return false;
                    }
                } else {
                    handleCriticalMaterialShortage(gameSession, "No knife available (checked inventory and bank for both Fletching knife and regular knife)");
                    return false;
                }
                sleep(600);
            }

            // Ensure we have a log basket
            if (!InventoryUtils.hasLogBasket()) {
                if (Rs2Bank.hasItem(InventoryUtils.LOG_BASKET_ID)) {
                    if (!Rs2Bank.withdrawOne(InventoryUtils.LOG_BASKET_ID)) {
                        Microbot.log("Failed to withdraw log basket");
                        return false;
                    }
                } else {
                    handleCriticalMaterialShortage(gameSession, "No log basket available (checked inventory and bank)");
                    return false;
                }
                sleep(600);
            }

            return InventoryUtils.hasKnife() && InventoryUtils.hasLogBasket();

        } catch (Exception e) {
            Microbot.log("Error ensuring knife and log basket in inventory: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform the log basket filling operation according to user specifications:
     * 1. Take inventory full of logs
     * 2. Close bank
     * 3. Fill log basket
     * 4. Open bank
     * 5. Take rest of needed logs to inventory
     * @param gameSession the game session to update log basket tracking
     * @return true if operation completed successfully
     */
    private static boolean performLogBasketFillingOperation(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        try {
            Microbot.log("Starting log basket filling operation");

            // Step 1: Take inventory full of logs (leaving space for knife and basket)
            int logsToWithdraw = InventoryUtils.getOptimalLogBasketLogAmountForExtendedRoute(gameSession) - InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();

            if (!Rs2Bank.withdrawX(logId, logsToWithdraw)) {
                Microbot.log("Failed to withdraw logs to fill inventory");
                return false;
            }
            sleep(300,800);

            // Step 2: Close bank
            Rs2Bank.closeBank();
            sleep(500,1000);

            // Step 3: Fill log basket with logs from inventory
            if (!InventoryUtils.fillLogBasket(gameSession)) {
                Microbot.log("Failed to fill log basket");
                return false;
            }

            // Step 4: Open bank again
            if (!openBank()) {
                Microbot.log("Failed to reopen bank after filling basket");
                return false;
            }

            // Step 5: Take rest of needed logs to inventory
            int logsStillNeeded = InventoryUtils.getOptimalLogAmountForExtendedRoute(gameSession);
            if (logsStillNeeded > 0) {
                Microbot.log("Withdrawing additional " + logsStillNeeded + " logs for extended route");
                if (!Rs2Bank.withdrawX(logId, logsStillNeeded)) {
                    Microbot.log("Failed to withdraw additional logs");
                    return false;
                }
                sleep(1000);
            }

            Microbot.log("Log basket filling operation completed successfully");
            return true;

        } catch (Exception e) {
            Microbot.log("Error during log basket filling operation: " + e.getMessage());
            return false;
        }
    }
}