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
            System.err.println("Error navigating to bank: " + e.getMessage());
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
            System.err.println("Error opening bank: " + e.getMessage());
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
            System.err.println("Error depositing items: " + e.getMessage());
            return false;
        }
    }

    /**
     * Withdraw the required items for the minigame
     * @param gameSession the game session to update if critical errors occur
     * @return true if successfully withdrew required items
     */
    public static boolean withdrawRequiredItems(GameSession gameSession) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                return false;
            }

            // Ensure we have a knife first
            if (!InventoryUtils.hasKnife()) {
                if (Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID)) {
                    if (!Rs2Bank.withdrawOne(InventoryUtils.FLETCHING_KNIFE_ID)) {
                        System.err.println("Failed to withdraw Fletching knife");
                        return false;
                    }
                } else if (Rs2Bank.hasItem(InventoryUtils.KNIFE_ID)) {
                    if (!Rs2Bank.withdrawOne(InventoryUtils.KNIFE_ID)) {
                        System.err.println("Failed to withdraw knife");
                        return false;
                    }
                } else {
                    System.err.println("No knife found in bank!");
                    handleCriticalMaterialShortage(gameSession, "No knife available in inventory or bank");
                    return false;
                }
                sleep(600);
            }

            // Calculate how many logs to withdraw
            int logsToWithdraw = InventoryUtils.getOptimalLogWithdrawAmount();
            
            if (logsToWithdraw <= 0) {
                return true; // Already have enough materials
            }

            // Get the configured log type ID
            int logId = InventoryUtils.getLogId();

            // Check if bank has enough logs
            if (!Rs2Bank.hasBankItem(logId, logsToWithdraw)) {
                String logTypeName = config != null ? config.logType().getDisplayName() : "Yew Logs";
                System.err.println("Not enough " + logTypeName + " in bank! Need: " + logsToWithdraw);
                handleCriticalMaterialShortage(gameSession, "Insufficient " + logTypeName + " in bank (need " + logsToWithdraw + ")");
                return false;
            }

            // Withdraw logs
            Microbot.log("Withdrawing " + logsToWithdraw + " logs");
            boolean withdrew = Rs2Bank.withdrawX(logId, logsToWithdraw);
            if (withdrew) {
                sleep(1000); // Wait for withdraw
            }

            return withdrew && InventoryUtils.hasRequiredItems();

        } catch (Exception e) {
            System.err.println("Error withdrawing required items: " + e.getMessage());
            return false;
        }
    }

    public static boolean fixZoom() {
        try {
            Rs2Camera.setZoom(0);
            return true;
        } catch (Exception e) {
            System.err.println("Error fixing zoom: " + e.getMessage());
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
                System.out.println("Log basket detected in inventory - enabling Extended Route (8 totems)");
                return TotemLocation.RouteType.EXTENDED;
            }
            
            // Ensure bank is open before checking
            if (!Rs2Bank.isOpen() && !openBank()) {
                System.out.println("Could not open bank for route detection - defaulting to Standard Route");
                return TotemLocation.RouteType.STANDARD;
            }
            
            // Check bank for log basket
            if (Rs2Bank.hasItem(InventoryUtils.LOG_BASKET_ID)) {
                System.out.println("Log basket detected in bank - enabling Extended Route (8 totems)");
                return TotemLocation.RouteType.EXTENDED;
            }
            
            System.out.println("No log basket detected in inventory or bank - using Standard Route (5 totems)");
            return TotemLocation.RouteType.STANDARD;
            
        } catch (Exception e) {
            System.err.println("Error detecting route type: " + e.getMessage());
            System.out.println("Defaulting to Standard Route due to error");
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
                    System.err.println("Failed to navigate to bank");
                    return false;
                }
            }

            // Open bank first
            if (!openBank()) {
                System.err.println("Failed to open bank");
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
                System.out.println("Performing extended route banking cycle");
                bankingSuccess = performExtendedRouteBankingOperations(gameSession);
            } else {
                System.out.println("Performing standard route banking cycle");
                bankingSuccess = performStandardBankingOperations(gameSession);
            }

            if (bankingSuccess) {
                // Close bank
                Rs2Bank.closeBank();
                fixZoom();
                System.out.println("Banking cycle completed successfully using " + detectedRouteType.getDescription());
            }

            return bankingSuccess;

        } catch (Exception e) {
            System.err.println("Error during unified banking cycle: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform standard route banking operations (assumes bank is already open)
     * @return true if standard banking operations completed successfully
     */
    private static boolean performStandardBankingOperations(GameSession gameSession) {
        try {
            // Deposit all except knife
            if (!depositAllExceptKnife()) {
                System.err.println("Failed to deposit items for standard route");
                return false;
            }

            // Check if we have enough materials
            if (!hasSufficientMaterials(1)) {
                System.err.println("Not enough materials for standard route - stopping bot");
                gameSession.setState(GameState.STOPPING);
                return false;
            }

            // Withdraw required items
            if (!withdrawRequiredItems(gameSession)) {
                System.err.println("Failed to withdraw required items for standard route");
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("Error during standard banking operations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform extended route banking operations (assumes bank is already open)
     * @return true if extended banking operations completed successfully
     */
    private static boolean performExtendedRouteBankingOperations(GameSession gameSession) {
        try {
            System.out.println("Starting extended route banking operations");
            
            // Step 1: Ensure we have knife and log basket in inventory
            if (!ensureKnifeAndLogBasketInInventory()) {
                System.err.println("Failed to ensure knife and log basket are in inventory");
                return false;
            }

            // Step 2: Empty log basket if this is initial banking or log type changed
            if (InventoryUtils.shouldEmptyLogBasket(gameSession)) {
                System.out.println("Emptying log basket for fresh start");
                if (!InventoryUtils.emptyLogBasket(gameSession)) {
                    System.err.println("Failed to empty log basket");
                    return false;
                }
            }

            // Step 3: Check if we have enough materials for extended route
            if (!hasSufficientMaterialsForExtendedRoute(gameSession, 1)) {
                System.err.println("Not enough materials for extended route - stopping bot");
                gameSession.setState(GameState.STOPPING);
                return false;
            }

            // Step 4: Deposit all items except knife and log basket
            if (!depositAllExceptKnifeAndLogBasket()) {
                System.err.println("Failed to deposit items while preserving knife and log basket");
                return false;
            }

            // Step 5: Perform log basket filling operation
            if (!performLogBasketFillingOperation(gameSession)) {
                System.err.println("Failed to perform log basket filling operation");
                return false;
            }

            System.out.println("Extended route banking operations completed successfully");
            return true;
            
        } catch (Exception e) {
            System.err.println("Error during extended route banking operations: " + e.getMessage());
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
            System.out.println("Performing a long AFK...");
            Microbot.status = "Long AFK";
            Rs2Antiban.moveMouseOffScreen(90);
            sleep(30000, 90000);
        } else if (roll < MEDIUM_AFK_PROBABILITY) {
            // Medium AFK: 10 to 30 seconds
            System.out.println("Performing a medium AFK...");
            Microbot.status = "Medium AFK";
            Rs2Antiban.moveMouseOffScreen(90);
            sleep(10000, 30000);
        } else if (roll < SHORT_AFK_PROBABILITY) {
            // Short AFK: 2 to 8 seconds
            System.out.println("Performing a short AFK...");
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
                return false;
            }

            // Check for knife
            boolean hasKnifeAvailable = InventoryUtils.hasKnife() || Rs2Bank.hasItem(InventoryUtils.KNIFE_ID) || Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID);
            
            // Check for logs (25 logs per round)
            int logsNeeded = roundsPlanned * 25;
            int currentLogs = InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();
            int bankLogs = Rs2Bank.count(logId);
            
            boolean hasEnoughLogs = (currentLogs + bankLogs) >= logsNeeded;

            return hasKnifeAvailable && hasEnoughLogs;

        } catch (Exception e) {
            System.err.println("Error checking bank materials: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle critical material shortage by setting game state to STOPPING
     * @param gameSession the game session to update
     * @param missingItem description of what material is missing
     */
    private static void handleCriticalMaterialShortage(GameSession gameSession, String missingItem) {
        System.err.println("=== CRITICAL ERROR ===");
        System.err.println("Missing required materials: " + missingItem);
        System.err.println("Cannot continue with Vale Totems bot");
        System.err.println("Bot will now close bank, logout, and stop");
        System.err.println("======================");
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
                return false;
            }

            // Check for knife
            boolean hasKnifeAvailable = InventoryUtils.hasKnife() || 
                Rs2Bank.hasItem(InventoryUtils.KNIFE_ID) || 
                Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID);
            
            // Check for log basket
            boolean hasLogBasket = InventoryUtils.hasLogBasket();
            
            // Check for logs (40 logs per extended route round - 8 totems * 5 each)
            int logsNeeded = roundsPlanned * 40;
            int currentLogs = InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();
            int bankLogs = Rs2Bank.count(logId);
            
            // Add log basket contents to total available logs using tracked count
            int logBasketLogs = InventoryUtils.getLogBasketLogCount(gameSession);
            
            boolean hasEnoughLogs = (currentLogs + bankLogs + logBasketLogs) >= logsNeeded;

            return hasKnifeAvailable && hasLogBasket && hasEnoughLogs;

        } catch (Exception e) {
            System.err.println("Error checking bank materials for extended route: " + e.getMessage());
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
                System.out.println("Withdrawing log basket from bank for extended route");
                boolean withdrew = Rs2Bank.withdrawOne(InventoryUtils.LOG_BASKET_ID);
                if (withdrew) {
                    sleep(600); // Wait for withdrawal
                    return InventoryUtils.hasLogBasket();
                } else {
                    System.err.println("Failed to withdraw log basket from bank");
                    return false;
                }
            }
            
            System.out.println("No log basket found in bank");
            return false;
            
        } catch (Exception e) {
            System.err.println("Error ensuring log basket availability: " + e.getMessage());
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
            System.err.println("Error depositing items (preserving knife and log basket): " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensure knife and log basket are in inventory
     * @return true if both items are available in inventory
     */
    private static boolean ensureKnifeAndLogBasketInInventory() {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                return false;
            }

            // Ensure we have a knife
            if (!InventoryUtils.hasKnife()) {
                if (Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID)) {
                    if (!Rs2Bank.withdrawOne(InventoryUtils.FLETCHING_KNIFE_ID)) {
                        System.err.println("Failed to withdraw Fletching knife");
                        return false;
                    }
                } else if (Rs2Bank.hasItem(InventoryUtils.KNIFE_ID)) {
                    if (!Rs2Bank.withdrawOne(InventoryUtils.KNIFE_ID)) {
                        System.err.println("Failed to withdraw knife");
                        return false;
                    }
                } else {
                    System.err.println("No knife found in bank!");
                    return false;
                }
                sleep(600);
            }

            // Ensure we have a log basket
            if (!InventoryUtils.hasLogBasket()) {
                if (Rs2Bank.hasItem(InventoryUtils.LOG_BASKET_ID)) {
                    if (!Rs2Bank.withdrawOne(InventoryUtils.LOG_BASKET_ID)) {
                        System.err.println("Failed to withdraw log basket");
                        return false;
                    }
                } else {
                    System.err.println("No log basket found in bank!");
                    return false;
                }
                sleep(600);
            }

            return InventoryUtils.hasKnife() && InventoryUtils.hasLogBasket();

        } catch (Exception e) {
            System.err.println("Error ensuring knife and log basket in inventory: " + e.getMessage());
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
            System.out.println("Starting log basket filling operation");

            // Step 1: Take inventory full of logs (leaving space for knife and basket)
            int logsToWithdraw = InventoryUtils.getOptimalLogBasketLogAmountForExtendedRoute(gameSession) - InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();
            
            if (!Rs2Bank.withdrawX(logId, logsToWithdraw)) {
                System.err.println("Failed to withdraw logs to fill inventory");
                return false;
            }
            sleep(300,800);

            // Step 2: Close bank
            Rs2Bank.closeBank();
            sleep(500,1000);

            // Step 3: Fill log basket with logs from inventory
            if (!InventoryUtils.fillLogBasket(gameSession)) {
                System.err.println("Failed to fill log basket");
                return false;
            }

            // Step 4: Open bank again
            if (!openBank()) {
                System.err.println("Failed to reopen bank after filling basket");
                return false;
            }

            // Step 5: Take rest of needed logs to inventory
            int logsStillNeeded = InventoryUtils.getOptimalLogAmountForExtendedRoute(gameSession);
            if (logsStillNeeded > 0) {
                System.out.println("Withdrawing additional " + logsStillNeeded + " logs for extended route");
                if (!Rs2Bank.withdrawX(logId, logsStillNeeded)) {
                    System.err.println("Failed to withdraw additional logs");
                    return false;
                }
                sleep(1000);
            }

            System.out.println("Log basket filling operation completed successfully");
            return true;

        } catch (Exception e) {
            System.err.println("Error during log basket filling operation: " + e.getMessage());
            return false;
        }
    }
}