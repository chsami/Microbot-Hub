package net.runelite.client.plugins.microbot.valetotems.handlers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.ValeTotemConfig;
import net.runelite.client.plugins.microbot.valetotems.utils.FletchingItemMapper;
import net.runelite.client.plugins.microbot.valetotems.utils.InventoryUtils;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.awt.event.KeyEvent;

/**
 * Handles fletching operations for the Vale Totems minigame
 * Includes optimization for fletching while walking and log basket support for extended routes
 * 
 * Key features:
 * - Automatic log basket emptying when insufficient logs in inventory
 * - Support for both standard (5 totems) and extended routes (8 totems) 
 * - Backward compatibility with existing methods
 * - GameSession-based tracking for extended route optimization
 */
public class FletchingHandler {

    private static final long FLETCHING_INTERFACE_TIMEOUT_MS = 3000; // 3 seconds
    private static final long FLETCHING_ANIMATION_DELAY_MS = 1800; // Time per fletching action
    private static final int FLETCHING_INTERFACE_WIDGET_ID = 270; // Fletching interface widget ID
    private static final int HOTKEY_TEXT_WIDGET_ID = 13; // Widget ID for hotkey text (270,13[X])
    
    // Fletching quantity widgets
    private static final int QUANTITY_1_CHILD_ID = 7;
    private static final int QUANTITY_5_CHILD_ID = 8;
    private static final int QUANTITY_10_CHILD_ID = 9;
    private static final int QUANTITY_OTHER_CHILD_ID = 10;
    private static final int QUANTITY_X_CHILD_ID = 11;
    private static final int QUANTITY_ALL_CHILD_ID = 12;

    // State tracking for fletching while walking
    private static boolean isFletchingWhileWalking = false;
    private static int targetBowsToFletch = 0;
    
    // Configuration instance
    private static ValeTotemConfig config;

    /**
     * Set the configuration for this handler
     * @param config the ValeTotemConfig instance
     */
    public static void setConfig(ValeTotemConfig config) {
        FletchingHandler.config = config;
    }

    /**
     * Get the appropriate keyboard key for a widget based on its hotkey text
     * @param childId the widget child ID to check
     * @return the KeyEvent constant for the appropriate key, or -1 if no hotkey available
     */
    private static int getWidgetHotkey(int childId) {
        try {
            // Calculate the hotkey index based on child ID
            int hotkeyIndex = getHotkeyIndex(childId);
            if (hotkeyIndex == -1) {
                return -1; // No hotkey mapping
            }

            // Check widget text at 270,13[hotkeyIndex]
            Widget hotkeyWidget = Rs2Widget.getWidget(FLETCHING_INTERFACE_WIDGET_ID, HOTKEY_TEXT_WIDGET_ID);
            if (hotkeyWidget != null && hotkeyWidget.getChildren() != null && 
                hotkeyIndex < hotkeyWidget.getChildren().length) {
                
                Widget specificHotkeyWidget = hotkeyWidget.getChild(hotkeyIndex);
                if (specificHotkeyWidget != null) {
                    String hotkeyText = specificHotkeyWidget.getText();
                    if (hotkeyText != null) {
                        hotkeyText = hotkeyText.replaceAll("<[^>]*>", "").trim(); // Remove HTML tags
                        
                        // Check if it's "Space"
                        if (hotkeyText.equalsIgnoreCase("Space")) {
                            return KeyEvent.VK_SPACE;
                        }
                        
                        // Check if it's a number
                        if (hotkeyText.matches("\\d")) {
                            int number = Integer.parseInt(hotkeyText);
                            return number;
                        }
                    }
                }
            }

            // Fallback to default number key based on position
            return getDefaultNumberKey(hotkeyIndex);

        } catch (Exception e) {
            System.err.println("Error getting widget hotkey: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Get the hotkey index for a given child ID
     * @param childId the widget child ID
     * @return the hotkey index (0-based) or -1 if not found
     */
    private static int getHotkeyIndex(int childId) {
        switch (childId) {
            case 15: return 1; // Shortbow option - '2' key
            case 16: return 2; // Longbow option - '3' key
            default: return -1;
        }
    }

    /**
     * Get the default number key for a hotkey index
     * @param hotkeyIndex the 0-based hotkey index
     * @return the KeyEvent constant for the number key
     */
    private static int getDefaultNumberKey(int hotkeyIndex) {
        switch (hotkeyIndex) {
            case 0: return 1;
            case 1: return 2;
            case 2: return 3;
            case 3: return 4;
            case 4: return 5;
            case 5: return 6;
            case 6: return 7;
            case 7: return 8;
            case 8: return 9;
            default: return -1;
        }
    }

    /**
     * Try to interact with a widget using keyboard shortcut, fallback to clicking
     * @param childId the widget child ID to interact with
     * @param description description for logging
     * @return true if interaction was successful
     */
    private static boolean interactWithWidget(int childId, String description) {
        try {
            // Try keyboard shortcut first
            int hotkey = getWidgetHotkey(childId);
            if (hotkey != -1) {
                if (hotkey == KeyEvent.VK_SPACE) {
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                } else {
                    char keyName = getKeyName(hotkey);
                    Rs2Keyboard.keyPress(keyName);
                }
                Microbot.log("Used keyboard shortcut '" + getKeyName(hotkey) + "' for: " + description);
                return true;
            }

            // Fallback to widget clicking
            Rs2Widget.clickWidget(FLETCHING_INTERFACE_WIDGET_ID, childId);
            Microbot.log("Used widget click (no hotkey available) for: " + description);
            return true;

        } catch (Exception e) {
            System.err.println("Error interacting with widget: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get a human-readable name for a KeyEvent constant
     * @param keyEvent the KeyEvent constant
     * @return the key name (e.g., ' ', '1', '2', etc.)
     */
    private static char getKeyName(int keyEvent) {
        switch (keyEvent) {
            case KeyEvent.VK_SPACE: return ' ';
            case 1: return '1';
            case 2: return '2';
            case 3: return '3';
            case 4: return '4';
            case 5: return '5';
            case 6: return '6';
            case 7: return '7';
            case 8: return '8';
            case 9: return '9';
            default: return 'U';
        }
    }

    /**
     * Start fletching bows using knife and logs
     * @return true if fletching interface opened successfully
     */
    public static boolean startFletching() {
        try {
            if (!InventoryUtils.hasKnife() || InventoryUtils.getLogCount() == 0) {
                System.err.println("Missing materials for fletching");
                return false;
            }

            System.out.println("Starting fletching operation");

            // Use knife on log 
            boolean used = InventoryUtils.startFletching();
            if (used) {
                // Wait for fletching interface to appear
                long startTime = System.currentTimeMillis();
                while (!isFletchingInterfaceOpen() && 
                       System.currentTimeMillis() - startTime < FLETCHING_INTERFACE_TIMEOUT_MS) {
                    sleep(100);
                }

                Microbot.log("Fletching interface opened");
                
                return isFletchingInterfaceOpen();
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error starting fletching: " + e.getMessage());
            return false;
        }
    }

    /**
     * Select target to fletch from the fletching interface
     * @param quantity number of bows to make (1, 5, 10, or All)
     * @return true if selection was successful
     */
    public static boolean selectBow(int quantity) {
        try {
            if (!isFletchingInterfaceOpen()) {
                return false;
            }

            sleepGaussian(200,100);

            // Select quantity using widgets
            if (quantity == 1) {
                interactWithWidget(QUANTITY_1_CHILD_ID, "1 bow");
                Microbot.log("Selected 1 bow");
            } else if (quantity == 5) {
                interactWithWidget(QUANTITY_5_CHILD_ID, "5 bows");
                Microbot.log("Selected 5 bows");
            } else if (quantity == 10) {
                interactWithWidget(QUANTITY_10_CHILD_ID, "10 bows");
                Microbot.log("Selected 10 bows");
            } else if (quantity >= 28) {
                interactWithWidget(QUANTITY_ALL_CHILD_ID, "all bows");
                Microbot.log("Selected all bows");
            } else {
                // For custom quantities, check if the "Other" option is already set correctly
                Widget otherQuantityWidget = Rs2Widget.getWidget(FLETCHING_INTERFACE_WIDGET_ID, QUANTITY_OTHER_CHILD_ID);
                if (otherQuantityWidget != null) {
                    Widget textWidget = otherQuantityWidget.getChild(9);
                    if (textWidget != null) {
                        String currentQuantity = textWidget.getText().replaceAll("<[^>]*>", "");
                        if (Integer.parseInt(currentQuantity) == quantity || otherQuantityWidget.getActions() != null) {
                            if (otherQuantityWidget.getActions() != null) {
                                interactWithWidget(QUANTITY_OTHER_CHILD_ID, "other quantity");
                            }
                            Microbot.log("Selected other quantity");
                        } else {
                            // If the quantity is not what we want, click "X", type the new quantity, and press enter
                            interactWithWidget(QUANTITY_X_CHILD_ID, "X");
                            sleepUntil(() -> Rs2Widget.getChildWidgetText(162, 42).contains("Enter amount"), 2000);
                            sleepGaussian(200,100);
                            Rs2Keyboard.typeString(String.valueOf(quantity));
                            sleepGaussian(300,100);
                            Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                            Microbot.log("Selected other quantity1");
                        }
                    } else {
                        // If the quantity is null, click "X", type the new quantity, and press enter
                        interactWithWidget(QUANTITY_X_CHILD_ID, "X");
                        sleepUntil(() -> Rs2Widget.getChildWidgetText(162, 42).contains("Enter amount"), 2000);
                        sleepGaussian(200,100);
                        Rs2Keyboard.typeString(String.valueOf(quantity));
                        sleepGaussian(300,100);
                        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                        Microbot.log("Selected other quantity2");
                    }
                } else {
                    Microbot.log("No other quantity widget found");
                }
            }

            sleepGaussian(300,100);
            
            // Click the configured bow option using the mapper
            if (config != null) {
                int bowChildId = FletchingItemMapper.getFletchingInterfaceChildId(config.logType(), config.bowType());
                String description = FletchingItemMapper.getFletchingDescriptionWithShortcut(config.logType(), config.bowType());
                interactWithWidget(bowChildId, description);
                Microbot.log("Selected " + FletchingItemMapper.getFletchingDescription(config.logType(), config.bowType()));
            } else {
                // Fallback to yew longbow
                interactWithWidget(16, "Yew Longbow (u) (expected key: 3)");
                Microbot.log("Selected Yew Longbow (u) - fallback");
            }
            
            sleepGaussian(200,100);

            return true;

        } catch (Exception e) {
            System.err.println("Error selecting target to fletch: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fletch a specific number of bows
     * @param quantity number of bows to fletch
     * @return true if fletching completed successfully
     */
    public static boolean fletchBows(int quantity) {
        return fletchBows(quantity, null);
    }

    /**
     * Fletch a specific number of bows with log basket support
     * @param quantity number of bows to fletch
     * @param gameSession the game session for log basket tracking (null if not using extended route)
     * @return true if fletching completed successfully
     */
    public static boolean fletchBows(int quantity, net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        try {
            // Ensure we have enough materials - try to empty log basket if needed
            int availableLogs = InventoryUtils.getLogCount();
            
            // If we don't have enough logs and we have a log basket, try to empty it
            if (availableLogs <= quantity && gameSession != null && InventoryUtils.hasLogBasket()) {
                int logsInBasket = InventoryUtils.getLogBasketLogCount(gameSession);
                if (logsInBasket > 0) {
                    System.out.println("Not enough logs in inventory (" + availableLogs + "/" + quantity + "). Emptying log basket with " + logsInBasket + " logs");
                    if (InventoryUtils.emptyLogBasket(gameSession)) {
                        availableLogs = InventoryUtils.getLogCount(); // Refresh count after emptying
                        System.out.println("After emptying basket: " + availableLogs + " logs available");
                    } else {
                        System.err.println("Failed to empty log basket");
                    }
                }
            }
            
            int actualQuantity = Math.min(quantity, availableLogs);
            
            if (actualQuantity <= 0) {
                System.err.println("No logs available for fletching");
                return false;
            }

            String fletchingDesc = config != null ? 
                FletchingItemMapper.getFletchingDescription(config.logType(), config.bowType()) : 
                "Yew Longbow (u)";
            System.out.println("Fletching " + actualQuantity + " " + fletchingDesc);

            // Start fletching interface
            if (!startFletching()) {
                return false;
            }

            // Select target to fletch and quantity
            if (!selectBow(actualQuantity)) {
                return false;
            }

            // Wait for fletching to complete
            return waitForFletchingCompletion(actualQuantity);

        } catch (Exception e) {
            System.err.println("Error fletching bows: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fletch the exact number of bows needed for one totem (4 bows)
     * @return true if 4 bows were successfully fletched
     */
    public static boolean fletchBowsForOneTotem() {
        return fletchBowsForOneTotem(null);
    }

    /**
     * Fletch the exact number of bows needed for one totem (4 bows) with log basket support
     * @param gameSession the game session for log basket tracking (null if not using extended route)
     * @return true if 4 bows were successfully fletched
     */
    public static boolean fletchBowsForOneTotem(net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        int needed = 4 - InventoryUtils.getBowCount();
        if (needed <= 0) {
            return true; // Already have enough
        }

        return fletchBows(needed, gameSession);
    }

    /**
     * Start fletching while walking (optimization feature)
     * This allows fletching during movement to save time
     * @param bowsNeeded number of bows to fletch during travel
     * @return true if fletching while walking was initiated
     */
    public static boolean startFletchingWhileWalking(int bowsNeeded) {
        return startFletchingWhileWalking(bowsNeeded, null);
    }

    /**
     * Start fletching while walking with log basket support (optimization feature)
     * This allows fletching during movement to save time
     * @param bowsNeeded number of bows to fletch during travel
     * @param gameSession the game session for log basket tracking (null if not using extended route)
     * @return true if fletching while walking was initiated
     */
    public static boolean startFletchingWhileWalking(int bowsNeeded, net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        try {
            // Check if we have enough materials to start fletching
            if (bowsNeeded > 0 && Rs2Player.isMoving() && !isFletchingWhileWalking && !Rs2Player.isAnimating()) {
                // Check available logs and try to empty basket if needed
                int availableLogs = InventoryUtils.getLogCount();
                sleepGaussian(100, 50);

                // If we don't have enough logs and we have a log basket, try to empty it
                if (availableLogs <= bowsNeeded && gameSession != null && InventoryUtils.hasLogBasket()) {
                    int logsInBasket = InventoryUtils.getLogBasketLogCount(gameSession);
                    if (logsInBasket > 0) {
                        System.out.println("Not enough logs for fletching while walking (" + availableLogs + "/" + bowsNeeded + "). Emptying log basket with " + logsInBasket + " logs");
                        if (InventoryUtils.emptyLogBasket(gameSession)) {
                            availableLogs = InventoryUtils.getLogCount(); // Refresh count after emptying
                            System.out.println("After emptying basket for walking fletching: " + availableLogs + " logs available");
                        } else {
                            System.err.println("Failed to empty log basket for walking fletching");
                        }
                        return false;
                    }
                }

                if (availableLogs < bowsNeeded) {
                    System.out.println("Not enough logs for fletching while walking (" + availableLogs + "/" + bowsNeeded + "). Stopping fletching");
                    return false;
                }

                isFletchingWhileWalking = true;
                targetBowsToFletch = bowsNeeded;
                
                String fletchingDesc = config != null ? 
                    FletchingItemMapper.getFletchingDescription(config.logType(), config.bowType()) : 
                    "Yew Longbow (u)";
                System.out.println("Starting fletching while walking: " + bowsNeeded + " " + fletchingDesc);
                
                // Try to start fletching immediately if materials are available
                if (InventoryUtils.hasKnife() && InventoryUtils.getLogCount() > 0) {
                    startFletching();
                    selectBow(targetBowsToFletch);
                    Rs2Antiban.moveMouseOffScreen(20);
                    sleep(500);
                    return true;
                }
                return true; // Marked as started, will try fletching in continue method
            }
            return false;

        } catch (Exception e) {
            System.err.println("Error starting fletching while walking: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stop fletching while walking
     */
    public static void stopFletchingWhileWalking() {
        isFletchingWhileWalking = false;
        targetBowsToFletch = 0;
        System.out.println("Stopped fletching while walking");
    }

    /**
     * Check if currently fletching while walking
     * @return true if fletching while walking is active
     */
    public static boolean isFletchingWhileWalking() {
        return isFletchingWhileWalking;
    }

    /**
     * Wait for fletching animation to complete
     * @param expectedBows number of bows expected to be created
     * @return true if fletching completed successfully
     */
    private static boolean waitForFletchingCompletion(int expectedBows) {
        try {
            int initialBows = InventoryUtils.getBowCount();
            int targetBows = initialBows + expectedBows;
            
            // Wait for fletching to complete
            long startTime = System.currentTimeMillis();
            long maxWaitTime = expectedBows * FLETCHING_ANIMATION_DELAY_MS + 5000; // Extra buffer
            
            while (InventoryUtils.getBowCount() < targetBows && 
                   System.currentTimeMillis() - startTime < maxWaitTime) {
                
                sleep(100);
            }

            int actualBows = InventoryUtils.getBowCount() - initialBows;
            System.out.println("Fletched " + actualBows + " bows (expected " + expectedBows + ")");
            
            return actualBows >= expectedBows;

        } catch (Exception e) {
            System.err.println("Error waiting for fletching completion: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if the fletching interface is currently open
     * @return true if fletching interface is visible
     */
    private static boolean isFletchingInterfaceOpen() {
        // This may need adjustment based on actual RuneLite widget IDs
        return Rs2Widget.getWidget(FLETCHING_INTERFACE_WIDGET_ID, 0) != null;
    }

    /**
     * Get fletching status summary
     * @return formatted string with fletching status
     */
    public static String getFletchingStatus() {
        int logs = InventoryUtils.getLogCount();
        int bows = InventoryUtils.getBowCount();
        int maxPossible = logs + bows;
        
        String fletchingType = config != null ? 
            FletchingItemMapper.getFletchingDescriptionWithShortcut(config.logType(), config.bowType()) : 
            "Yew Longbow (u) (expected key: 3)";
        
        String walkingStatus = isFletchingWhileWalking ? 
            " | Walking: " + targetBowsToFletch + " target" : "";
        
        return String.format("Fletching (%s): Logs=%d, Bows=%d, Max=%d%s [Keyboard optimized]", 
                fletchingType, logs, bows, maxPossible, walkingStatus);
    }

    /**
     * Emergency stop fletching (for error recovery)
     */
    public static void emergencyStopFletching() {
        isFletchingWhileWalking = false;
        targetBowsToFletch = 0;
        
        // Try to close any open interfaces
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        
        System.out.println("Emergency stop fletching executed");
    }

    /**
     * Calculate optimal fletching strategy based on current situation
     * @param bowsNeeded number of bows needed
     * @param isWalking whether the player is currently walking
     * @return recommended fletching approach
     */
    public static FletchingStrategy getOptimalStrategy(int bowsNeeded, boolean isWalking) {
        int currentBows = InventoryUtils.getBowCount();
        int currentLogs = InventoryUtils.getLogCount();
        int deficit = Math.max(0, bowsNeeded - currentBows);
        
        if (deficit == 0) {
            return FletchingStrategy.NO_FLETCHING_NEEDED;
        }
        
        if (currentLogs < deficit) {
            return FletchingStrategy.INSUFFICIENT_LOGS;
        }
        
        if (isWalking && deficit <= 5) {
            return FletchingStrategy.FLETCH_WHILE_WALKING;
        }
        
        return FletchingStrategy.FLETCH_STATIONARY;
    }


    /**
     * Enum for fletching strategies
     */
    public enum FletchingStrategy {
        NO_FLETCHING_NEEDED,
        FLETCH_WHILE_WALKING,
        FLETCH_STATIONARY,
        INSUFFICIENT_LOGS
    }
} 