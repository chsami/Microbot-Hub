package net.runelite.client.plugins.microbot.autosmelt;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autosmelt.enums.BarType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.awt.event.KeyEvent;

import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoSmeltScript extends Script {
    private AutoSmeltConfig config;
    private BarType currentBarType;
    
    // Edgeville locations
    private static final WorldPoint EDGEVILLE_BANK = new WorldPoint(3094, 3492, 0);
    private static final WorldPoint EDGEVILLE_FURNACE = new WorldPoint(3109, 3499, 0);
    
    private enum State {
        BANKING,
        WALKING_TO_FURNACE,
        SMELTING,
        WALKING_TO_BANK
    }
    
    private State currentState = State.BANKING;
    
    public boolean run(AutoSmeltConfig config) {
        this.config = config;
        this.currentBarType = config.barType();
        
        log.info("Starting Auto Smelt script for: {}", currentBarType.getDisplayName());
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    return;
                }
                
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                
                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                    return;
                }
                
                switch (currentState) {
                    case BANKING:
                        handleBanking();
                        break;
                    case WALKING_TO_FURNACE:
                        handleWalkingToFurnace();
                        break;
                    case SMELTING:
                        handleSmelting();
                        break;
                    case WALKING_TO_BANK:
                        handleWalkingToBank();
                        break;
                }
                
            } catch (Exception ex) {
                log.error("Error in Auto Smelt script: {}", ex.getMessage(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    private void handleBanking() {
        Microbot.status = "Banking...";
        
        if (!Rs2Bank.isNearBank(10)) {
            if (config.debugMode()) {
                log.info("Not near bank, walking to Edgeville bank");
            }
            Rs2Walker.walkTo(EDGEVILLE_BANK);
            return;
        }
        
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleep(3000);
            return;
        }
        
        // Check if we have enough materials
        if (!hasRequiredMaterials()) {
            if (config.logoutWhenComplete()) {
                log.info("No more materials available, logging out");
                Rs2Player.logout();
                shutdown();
                return;
            } else {
                log.info("No more materials available, stopping script");
                shutdown();
                return;
            }
        }
        
        // Deposit all bars first
        if (Rs2Inventory.hasItem(currentBarType.getProductId())) {
            Rs2Bank.depositAll(currentBarType.getProductId());
            sleep(600, 1000);
            return;
        }
        
        // Withdraw required materials
        int[] items = currentBarType.getRequiredItems();
        int[] quantities = currentBarType.getRequiredQuantities();
        for (int i = 0; i < items.length; i++) {
            int itemId = items[i];
            Rs2Bank.withdrawX(itemId, quantities[i]);
            sleep(300, 600);
        }
        
        // Close bank and go to furnace
        Rs2Bank.closeBank();
        currentState = State.WALKING_TO_FURNACE;
    }
    
    private void handleWalkingToFurnace() {
        Microbot.status = "Walking to furnace...";
        if(Rs2Player.isAnimating())
        {
            return;
        }
        
        if (Rs2Player.getWorldLocation().distanceTo(EDGEVILLE_FURNACE) <= 5) {
            currentState = State.SMELTING;
            return;
        }
        
        Rs2Walker.walkTo(EDGEVILLE_FURNACE);
    }
    
    private void handleSmelting() {
        Microbot.status = "Smelting " + currentBarType.getDisplayName() + "...";

        if(Rs2Player.isAnimating())
        {
            return;
        }

        if(Rs2Inventory.hasItem("bar") && Rs2Inventory.hasItem(currentBarType.getRequiredItems()))
        {
            return;
        }
        
        // Check if we still have materials
        if (!hasAllRequiredItems()) {
            currentState = State.WALKING_TO_BANK;
            return;
        }
        
        GameObject furnace = Rs2GameObject.getGameObject(ObjectID.VARROCK_DIARY_FURNACE); // Edgeville furnace
        if (furnace == null) {
            furnace = Rs2GameObject.getGameObject("Furnace");
        }
        
        if (furnace != null) {
            Rs2GameObject.interact(furnace, "Smelt");

            // Wait for smelting interface
            sleep(5000);

            if (Rs2Widget.getWidget(17694733) != null) {
                // Click the bar type using xy coordinates from BarType
                int keyboardKey = currentBarType.getKeyboardKey();
                Rs2Keyboard.keyPress(keyboardKey);
                Rs2Keyboard.keyRelease(keyboardKey);
                sleep(200, 600);

                // Wait for smelting to complete
                sleep(10000);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        } else {
            log.error("Cannot find furnace at Edgeville");
            sleep(5000);
        }
    }
    
    private void handleWalkingToBank() {
        Microbot.status = "Walking to bank...";
        
        if (Rs2Bank.isNearBank(10)) {
            currentState = State.BANKING;
            return;
        }
        
        Rs2Walker.walkTo(EDGEVILLE_BANK);
    }
    
    private boolean hasRequiredMaterials() {
        for (int itemId : currentBarType.getRequiredItems()) {
            if (!Rs2Bank.hasItem(itemId)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean hasAllRequiredItems() {
        for (int itemId : currentBarType.getRequiredItems()) {
            if (!Rs2Inventory.hasItem(itemId)) {
                return false;
            }
        }
        return true;
    }
    
    // Removed getBarWidgetChild; now using xy coordinates from BarType
    
    @Override
    public void shutdown() {
        log.info("Shutting down Auto Smelt script");
        super.shutdown();
    }
}
