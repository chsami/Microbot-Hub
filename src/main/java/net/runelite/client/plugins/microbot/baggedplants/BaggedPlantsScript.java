package net.runelite.client.plugins.microbot.baggedplants;

import net.runelite.api.TileObject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.baggedplants.enums.BaggedPlantsState;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

public class BaggedPlantsScript extends Script {

    private static final int DEFAULT_DELAY = 600;
    private BaggedPlantsState state = BaggedPlantsState.IDLE;
    private int totalPlantsPlanted = 0;
    
    // Item IDs
    private static final int COINS = 995;
    private static final int UNNOTED_BAGGED_PLANT = 8431;
    private static final int NOTED_BAGGED_PLANT = 8432;
    private static final int EMPTY_WATERING_CAN = 5331;
    private static final int[] WATERING_CAN_IDS = {5333, 5334, 5335, 5336, 5337, 5338, 5339, 5340};
    
    // Game object IDs
    private static final int HOUSE_PORTAL = 15478;
    private static final int PLANT_SPACE = 15366;
    private static final int BUILT_PLANT = 5134;
    private static final int SINK = 9684;

    public boolean run(BaggedPlantsConfig config) {
        int actionDelay = config.useCustomDelay() ? config.actionDelay() : DEFAULT_DELAY;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                calculateState(config);
                
                switch (state) {
                    case CHECK_INVENTORY:
                        checkInventory();
                        break;
                    case ENTER_HOUSE:
                        enterHouse(config);
                        break;
                    case BUILD_PLANT:
                        buildPlant();
                        break;
                    case REMOVE_PLANT:
                        removePlant();
                        break;
                    case REFILL_SUPPLIES:
                        refillSupplies();
                        break;
                    case LEAVE_HOUSE:
                        leaveHouse();
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Error in BaggedPlants scheduled task: " + ex.getMessage());
            }
        }, 0, actionDelay, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void calculateState(BaggedPlantsConfig config) {
        // Don't override state if we're currently refilling supplies
        if (state == BaggedPlantsState.REFILL_SUPPLIES) {
            return;
        }
        
        boolean inHouse = isInHouse();
        boolean hasRequiredItems = checkRequiredItems();
        boolean hasUnnotedPlants = Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT);
        boolean hasWateringCans = hasUsableWateringCans();
        
        if (!hasRequiredItems) {
            state = BaggedPlantsState.CHECK_INVENTORY;
            return;
        }
        
        if ((!hasWateringCans || !hasUnnotedPlants) && !inHouse) {
            state = BaggedPlantsState.REFILL_SUPPLIES;
            return;
        }
        
        if ((!hasWateringCans || !hasUnnotedPlants) && inHouse) {
            state = BaggedPlantsState.LEAVE_HOUSE;
            return;
        }
        
        if (hasUnnotedPlants && !inHouse) {
            state = BaggedPlantsState.ENTER_HOUSE;
            return;
        }
        
        if (inHouse && hasUnnotedPlants) {
            // Check if there's a built plant to remove first
            TileObject builtPlant = Rs2GameObject.findObjectById(BUILT_PLANT);
            if (builtPlant != null) {
                state = BaggedPlantsState.REMOVE_PLANT;
            } else {
                // No built plant, check for empty plant space to build on
                TileObject plantSpace = Rs2GameObject.findObjectById(PLANT_SPACE);
                if (plantSpace != null) {
                    state = BaggedPlantsState.BUILD_PLANT;
                }
            }
        }
    }

    private boolean checkRequiredItems() {
        if (!Rs2Inventory.hasItem(COINS)) {
            Microbot.showMessage("Missing item Coins, go back and get it!");
            shutdown();
            return false;
        }
        
        // Check for exactly 3 watering cans (any combination of filled/empty)
        int totalWateringCans = 0;
        for (int wateringCanId : WATERING_CAN_IDS) {
            totalWateringCans += Rs2Inventory.count(wateringCanId);
        }
        totalWateringCans += Rs2Inventory.count(EMPTY_WATERING_CAN);
        
        if (totalWateringCans < 3) {
            Microbot.showMessage("3 watering cans required - go back and get them!");
            shutdown();
            return false;
        }
        
        // Only shut down if we have NEITHER unnoted NOR noted plants
        if (!Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT) && !Rs2Inventory.hasItem(NOTED_BAGGED_PLANT)) {
            Microbot.showMessage("No more plants, shutting down");
            shutdown();
            return false;
        }
        
        return true;
    }

    private boolean hasUsableWateringCans() {
        for (int wateringCanId : WATERING_CAN_IDS) {
            if (Rs2Inventory.hasItem(wateringCanId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInHouse() {
        // Similar to gilded altar script - check if Phials NPC is not present
        return Rs2Npc.getNpc("Phials") == null;
    }

    private void checkInventory() {
        if (!checkRequiredItems()) {
            return;
        }
        state = BaggedPlantsState.ENTER_HOUSE;
    }

    private void enterHouse(BaggedPlantsConfig config) {
        TileObject housePortal = Rs2GameObject.findObjectById(HOUSE_PORTAL);
        if (housePortal != null) {
            if (Rs2GameObject.interact(housePortal, "Build mode")) {
                System.out.println("Entered house in build mode");
                // Wait the configured time
                int waitTime = Rs2Random.between(config.minWaitTime() * 1000, config.maxWaitTime() * 1000);
                sleep(waitTime);
                state = BaggedPlantsState.BUILD_PLANT;
            }
        } else {
            Microbot.showMessage("Can't find house portal!");
            shutdown();
        }
    }

    private void buildPlant() {
        TileObject plantSpace = Rs2GameObject.findObjectById(PLANT_SPACE);
        if (plantSpace != null) {
            if (Rs2GameObject.interact(plantSpace, "Build")) {
                System.out.println("Interacted with plant space to build");
                sleepUntilOnClientThread(this::hasBuildInterfaceOpen, 2500);
                Rs2Keyboard.keyPress('1'); // Select first option
                sleepUntilOnClientThread(() -> !hasBuildInterfaceOpen(), 2500);
                System.out.println("Built plant");
                totalPlantsPlanted++; // Increment counter
                
                // After building, check if we still have watering cans
                if (!hasUsableWateringCans()) {
                    state = BaggedPlantsState.LEAVE_HOUSE;
                } else {
                    state = BaggedPlantsState.REMOVE_PLANT;
                }
            }
        }
    }

    private void removePlant() {
        TileObject builtPlant = Rs2GameObject.findObjectById(BUILT_PLANT);
        if (builtPlant != null) {
            if (Rs2GameObject.interact(builtPlant, "Remove")) {
                System.out.println("Interacted with plant to remove");
                sleepUntilOnClientThread(this::hasRemoveInterfaceOpen, 2500);
                Rs2Keyboard.keyPress('1'); // Confirm removal
                sleepUntilOnClientThread(() -> !hasRemoveInterfaceOpen(), 2500);
                System.out.println("Removed plant");
                
                // Check if we still have unnoted plants and watering cans
                if (!Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT)) {
                    state = BaggedPlantsState.LEAVE_HOUSE;
                } else if (!hasUsableWateringCans()) {
                    state = BaggedPlantsState.LEAVE_HOUSE;
                } else {
                    state = BaggedPlantsState.BUILD_PLANT;
                }
            }
        }
    }

    private void refillSupplies() {
        // Always handle inventory refill with Phials if we need unnoted plants
        if (!Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT) && Rs2Inventory.hasItem(NOTED_BAGGED_PLANT)) {
            if (Rs2Widget.getWidget(14352385) == null) {
                if (!Rs2Inventory.isItemSelected()) {
                    Rs2Inventory.use(NOTED_BAGGED_PLANT);
                } else {
                    Rs2Npc.interact("Phials", "Use");
                    Rs2Player.waitForWalking();
                }
                return; // Wait for dialogue to open
            } else if (Rs2Widget.getWidget(14352385) != null) {
                Rs2Keyboard.keyPress('3'); // Exchange notes for items
                Rs2Inventory.waitForInventoryChanges(2000);
                
                // Check if we have no more plants at all
                if (!Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT) && !Rs2Inventory.hasItem(NOTED_BAGGED_PLANT)) {
                    Microbot.showMessage("No more plants, shutting down");
                    shutdown();
                    return;
                }
                
                System.out.println("Plants unnoted successfully");
                // Continue to watering can check
            }
        }
        
        // Check if we have exactly 3 fully filled watering cans (ID 5340)
        int fullWateringCans = Rs2Inventory.count(5340);
        
        if (fullWateringCans < 3) {
            // Find any watering can that isn't ID 5340 and refill it
            int wateringCanToRefill = -1;
            
            // Check for empty cans first
            if (Rs2Inventory.hasItem(EMPTY_WATERING_CAN)) {
                wateringCanToRefill = EMPTY_WATERING_CAN;
            } else {
                // Check for partially filled cans
                for (int wateringCanId : WATERING_CAN_IDS) {
                    if (wateringCanId != 5340 && Rs2Inventory.hasItem(wateringCanId)) {
                        wateringCanToRefill = wateringCanId;
                        break;
                    }
                }
            }
            
            if (wateringCanToRefill != -1) {
                TileObject well = Rs2GameObject.findObjectById(SINK);
                if (well != null) {
                    System.out.println("Refilling watering can ID: " + wateringCanToRefill + " (Full cans: " + fullWateringCans + "/3)");
                    final int canToRefill = wateringCanToRefill;
                    Rs2Inventory.useItemOnObject(canToRefill, well.getId());
                    
                    // Wait until this specific can changes to ID 5340
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    sleepUntil(() -> !Rs2Inventory.hasItem(canToRefill), 3000);
                    sleep(1000); // Extra delay to ensure inventory updates
                    
                    System.out.println("Watering can refilled. Full cans now: " + Rs2Inventory.count(5340) + "/3");
                    // Stay in REFILL_SUPPLIES state to continue checking
                    return;
                } else {
                    Microbot.showMessage("Can't find well to refill watering cans!");
                    shutdown();
                    return;
                }
            } else {
                Microbot.showMessage("Can't find watering can to refill!");
                shutdown();
                return;
            }
        }
        
        // Verify we have everything we need before proceeding
        boolean hasPlants = Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT);
        boolean hasFullCans = Rs2Inventory.count(5340) >= 3;
        
        if (hasPlants && hasFullCans) {
            System.out.println("All supplies ready - Plants: " + hasPlants + ", Full watering cans: " + Rs2Inventory.count(5340) + "/3");
            state = BaggedPlantsState.ENTER_HOUSE;
        } else {
            System.out.println("Still missing supplies - Plants: " + hasPlants + ", Full watering cans: " + Rs2Inventory.count(5340) + "/3");
            // Stay in refill state to continue
        }
    }

    private void leaveHouse() {
        // Use settings menu to leave house (similar to gilded altar script)
        Rs2Tab.switchTo(InterfaceTab.SETTINGS);
        sleep(1200);

        // Check if house options button is visible
        String[] actions = Rs2Widget.getWidget(7602235).getActions();
        boolean isControlsInterfaceVisible = actions != null && actions.length == 0;
        if (!isControlsInterfaceVisible) {
            Rs2Widget.clickWidget(7602235);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(7602207));
        }

        // Click House Options
        if (Rs2Widget.clickWidget(7602207)) {
            sleep(1200);
        } else {
            System.out.println("House Options button not found.");
            return;
        }

        // Click Leave House
        if (Rs2Widget.clickWidget(24248341)) {
            sleep(3000);
            System.out.println("Left house");
            
            // Determine next state based on what we need
            if (!hasUsableWateringCans() || !Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT)) {
                state = BaggedPlantsState.REFILL_SUPPLIES;
            } else {
                state = BaggedPlantsState.ENTER_HOUSE;
            }
        } else {
            System.out.println("Leave House button not found.");
        }
    }

    private boolean hasBuildInterfaceOpen() {
        // Check for furniture/build interface similar to construction2
        Widget buildWidget = Rs2Widget.findWidget("Build", null);
        return buildWidget != null;
    }

    private boolean hasRemoveInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public BaggedPlantsState getState() {
        return state;
    }
    
    public int getTotalPlantsPlanted() {
        return totalPlantsPlanted;
    }
}

