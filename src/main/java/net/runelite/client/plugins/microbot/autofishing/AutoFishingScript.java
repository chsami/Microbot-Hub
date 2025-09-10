package net.runelite.client.plugins.microbot.autofishing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.awt.event.KeyEvent;

@Slf4j
public class AutoFishingScript extends Script {
    
    private AutoFishingState state = AutoFishingState.INITIALIZING;
    private AutoFishingConfig config;
    private Fish selectedFish;
    private long stateStartTime = System.currentTimeMillis(); // track state timeout
    @Getter
    private HarpoonType selectedHarpoon;
    private WorldPoint fishingLocation;
    private BankLocation closestBank;
    private int currentLocationIndex = 0;
    private String fishAction = "";
    
    public AutoFishingState getCurrentState() {
        return state;
    }

    public boolean run(AutoFishingConfig config) {
        this.config = config;
        this.selectedFish = config.fishToCatch();
        this.selectedHarpoon = config.harpoonSpec();
        
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (needsToBank()) {
                    state = AutoFishingState.DEPOSITING;
                } else if (state == AutoFishingState.DEPOSITING || state == AutoFishingState.RETURNING) {
                    if (isAtFishingLocation()) {
                        state = AutoFishingState.FISHING;
                    } else {
                        state = AutoFishingState.TRAVELING;
                    }
                } else if (!hasRequiredGear()) {
                    state = AutoFishingState.GETTING_GEAR;
                } else if (!isAtFishingLocation() && state != AutoFishingState.GETTING_GEAR) {
                    state = AutoFishingState.TRAVELING;
                }

                switch (state) {
                    case INITIALIZING:
                        handleInitializing();
                        break;
                    case CHECKING_GEAR:
                        handleCheckingGear();
                        break;
                    case GETTING_GEAR:
                        handleGettingGear();
                        break;
                    case TRAVELING:
                        handleTraveling();
                        break;
                    case FISHING:
                        handleFishing();
                        break;
                    case MANAGING_SPEC:
                        handleManagingSpec();
                        break;
                    case INVENTORY_FULL:
                        handleInventoryFull();
                        break;
                    case DEPOSITING:
                        handleDepositing();
                        break;
                    case RETURNING:
                        handleReturning();
                        break;
                    case ERROR_RECOVERY:
                        handleErrorRecovery();
                        break;
                }
            } catch (Exception ex) {
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }

    private boolean needsToBank() {
        if (!config.useBank()) return false;
        return Rs2Inventory.isFull();
    }

    private boolean hasRequiredGear() {
        if (!selectedFish.getMethod().getRequiredItems().isEmpty()) {
            boolean hasTool = selectedFish.getMethod().getRequiredItems().stream()
                    .anyMatch(tool -> Rs2Equipment.isWearing(tool) || Rs2Inventory.contains(tool));
            if (!hasTool) return false;
        }
        
        if (selectedHarpoon != HarpoonType.NONE) {
            return Rs2Equipment.isWearing(selectedHarpoon.getName()) || 
                   Rs2Inventory.contains(selectedHarpoon.getName());
        }
        
        return true;
    }

    private boolean isAtFishingLocation() {
        if (fishingLocation == null) return false;
        return Rs2Player.getWorldLocation().distanceTo(fishingLocation) <= 5;
    }

    private Rs2NpcModel getFishingSpot() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return Arrays.stream(selectedFish.getFishingSpot())
                .mapToObj(Rs2Npc::getNpc)
                .filter(Objects::nonNull)
                .filter(npc -> npc.getWorldLocation().distanceTo(playerLocation) <= 10)
                .findFirst()
                .orElse(null);
    }

    private void handleInitializing() {
        Microbot.status = "Initializing...";
        state = AutoFishingState.CHECKING_GEAR;
    }

    private void handleCheckingGear() {
        Microbot.status = "Checking equipment...";
        
        if (!hasRequiredGear()) {
            state = AutoFishingState.GETTING_GEAR;
            return;
        }
        
        if (fishingLocation == null) {
            // Nueva lógica: determina fishingLocation según la posición del jugador y NPCs disponibles
            selectFishingLocationBasedOnPlayer();
            // fallback: si no hay NPC cercano, usa la lista de localizaciones conocidas del Fish
            if (fishingLocation == null) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                fishingLocation = selectedFish.getClosestLocation(playerLocation);
            }
        }
        
        if (!isAtFishingLocation()) {
            state = AutoFishingState.TRAVELING;
            return;
        }
        
        state = AutoFishingState.FISHING;
    }

    private void handleGettingGear() {
        Microbot.status = "Getting equipment from bank...";
        
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.walkToBankAndUseBank()) {
                state = AutoFishingState.ERROR_RECOVERY;
                return;
            }
            return;
        }
        
        // withdraw required fishing tools with validation
        if (!selectedFish.getMethod().getRequiredItems().isEmpty()) {
            for (String tool : selectedFish.getMethod().getRequiredItems()) {
                // pre-condition validation
                if (!Rs2Equipment.isWearing(tool) && !Rs2Inventory.contains(tool)) {
                    if (!Rs2Bank.hasItem(tool)) {
                        log.info("Required tool {} not found in bank", tool);
                        continue;
                    }
                    
                    // perform action
                    log.info("Withdrawing required tool: {}", tool);
                    Rs2Bank.withdrawOne(tool);
                    
                    // post-condition validation
                    boolean success = sleepUntil(() -> Rs2Inventory.contains(tool), 3000);
                    if (success) {
                        log.info("Successfully withdrew tool: {}", tool);
                    } else {
                        log.info("Failed to withdraw tool {} within timeout", tool);
                    }
                    // small delay to prevent spam clicking
                    sleepUntil(() -> true, 600);
                    break;
                }
            }
        }
        
        // withdraw harpoon if needed with validation
        if (selectedHarpoon != HarpoonType.NONE) {
            if (!Rs2Equipment.isWearing(selectedHarpoon.getName()) && !Rs2Inventory.contains(selectedHarpoon.getName())) {
                if (!Rs2Bank.hasItem(selectedHarpoon.getName())) {
                    log.info("Required harpoon {} not found in bank", selectedHarpoon.getName());
                } else {
                    // perform action
                    log.info("Withdrawing harpoon: {}", selectedHarpoon.getName());
                    Rs2Bank.withdrawOne(selectedHarpoon.getName());
                    
                    // post-condition validation
                    boolean success = sleepUntil(() -> Rs2Inventory.contains(selectedHarpoon.getName()), 3000);
                    if (success) {
                        log.info("Successfully withdrew harpoon: {}", selectedHarpoon.getName());
                    } else {
                        log.info("Failed to withdraw harpoon {} within timeout", selectedHarpoon.getName());
                    }
                    // small delay after withdrawal
                    sleepUntil(() -> true, 600);
                }
            }
        }
        
        log.info("Closing bank after gear withdrawal");
        Rs2Bank.closeBank();
        
        // equip withdrawn tools with validation
        for (String tool : selectedFish.getMethod().getRequiredItems()) {
            if (Rs2Inventory.contains(tool)) {
                // pre-condition validation
                if (Rs2Equipment.isWearing(tool)) {
                    log.info("Tool {} already equipped", tool);
                    continue;
                }
                
                // perform action
                log.info("Equipping tool: {}", tool);
                Rs2Inventory.wield(tool);
                
                // post-condition validation
                boolean success = sleepUntil(() -> Rs2Equipment.isWearing(tool), 3000);
                if (success) {
                    log.info("Successfully equipped tool: {}", tool);
                } else {
                    log.info("Failed to equip tool {} within timeout", tool);
                }
                // small delay after equipping
                sleepUntil(() -> true, 600);
            }
        }
        
        // equip harpoon if withdrawn with validation
        if (selectedHarpoon != HarpoonType.NONE && Rs2Inventory.contains(selectedHarpoon.getName())) {
            // pre-condition validation
            if (!Rs2Equipment.isWearing(selectedHarpoon.getName())) {
                // perform action
                log.info("Equipping harpoon: {}", selectedHarpoon.getName());
                Rs2Inventory.wield(selectedHarpoon.getName());
                
                // post-condition validation
                boolean success = sleepUntil(() -> Rs2Equipment.isWearing(selectedHarpoon.getName()), 3000);
                if (success) {
                    log.info("Successfully equipped harpoon: {}", selectedHarpoon.getName());
                } else {
                    log.info("Failed to equip harpoon {} within timeout", selectedHarpoon.getName());
                }
                // small delay after equipping harpoon
                sleepUntil(() -> true, 600);
            } else {
                log.info("Harpoon {} already equipped", selectedHarpoon.getName());
            }
        }
        
        log.info("Gear setup complete, rechecking gear");
        changeState(AutoFishingState.CHECKING_GEAR);
    }

    private void handleTraveling() {
        Microbot.status = "Traveling to fishing location...";
        
        if (fishingLocation == null) {
            selectFishingLocationBasedOnPlayer();
            if (fishingLocation == null) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                WorldPoint closestLocation = selectedFish.getClosestLocation(playerLocation);
                if (closestLocation != null) {
                    fishingLocation = closestLocation;
                } else {
                    fishingLocation = selectedFish.getClosestLocation(playerLocation);
                }
            }
        }
        
        if (isAtFishingLocation()) {
            state = AutoFishingState.FISHING;
            return;
        }
        
        Rs2Walker.walkTo(fishingLocation);
    }

    private void handleFishing() {
        Microbot.status = "Fishing " + selectedFish.getName() + "...";
        
        if (Rs2Inventory.isFull()) {
            state = AutoFishingState.INVENTORY_FULL;
            return;
        }
        
        if (Rs2Player.isAnimating()) {
            return;
        }
        
        Rs2NpcModel fishingSpot = getFishingSpot();
        if (fishingSpot == null) {
            state = AutoFishingState.TRAVELING;
            return;
        }
        
        if (fishAction.isEmpty()) {
            fishAction = Rs2Npc.getAvailableAction(fishingSpot, selectedFish.getActions());
            if (fishAction.isEmpty()) {
                state = AutoFishingState.ERROR_RECOVERY;
                return;
            }
        }
        
        if (selectedFish.equals(Fish.KARAMBWAN)) {
            handleKarambwanLogic();
        }
        
        if (Rs2Npc.interact(fishingSpot, fishAction)) {
            Rs2Player.waitForXpDrop(Skill.FISHING, true);
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
        }
    }

    private void handleManagingSpec() {
        Microbot.status = "Activating special attack...";
        
        if (selectedHarpoon != HarpoonType.NONE && Rs2Combat.getSpecEnergy() >= 100) {
            Rs2Combat.setSpecState(true, 1000);
            // wait for special attack to activate
            sleepUntil(() -> Rs2Combat.getSpecEnergy() < 100, 3000);
        }
        
        state = AutoFishingState.FISHING;
    }

    private void handleInventoryFull() {
        Microbot.status = "Inventory full, managing items...";
        // If it's activated, we cook and then bank/drop
        if (config.cookFish() && hasRawFishToCook()) {
            tryCookNearby();
        }
        
        if (config.useBank()) {
            WorldPoint currentLocation = Rs2Player.getWorldLocation();
            closestBank = Rs2Bank.getNearestBank(currentLocation);
            if (closestBank == null) {
                state = AutoFishingState.ERROR_RECOVERY;
                return;
            }
            state = AutoFishingState.DEPOSITING;
        } else {
            // create a list and add raw and cooked fish
            List<String> allFishToDrop = new ArrayList<>();
            
            allFishToDrop.addAll(selectedFish.getRawNames());
            for (String rawName : selectedFish.getRawNames()) {
                allFishToDrop.add(rawName.replace("Raw ", ""));
            }

            if (Rs2Inventory.dropAll(allFishToDrop.toArray(new String[0]))) {
                state = AutoFishingState.FISHING;
            } else {
                state = AutoFishingState.ERROR_RECOVERY;
            }
        }
    }

    private void handleDepositing() {
        Microbot.status = "Depositing items...";
        
        if (!Rs2Bank.isOpen()) {
            if (closestBank != null) {
                if (!Rs2Bank.walkToBankAndUseBank(closestBank)) {
                    state = AutoFishingState.ERROR_RECOVERY;
                    return;
                }
            } else {
                if (!Rs2Bank.walkToBankAndUseBank()) {
                    state = AutoFishingState.ERROR_RECOVERY;
                    return;
                }
            }
            return;
        }
        
        for (String fishName : selectedFish.getRawNames()) {
            if (Rs2Inventory.contains(fishName)) {
                Rs2Bank.depositAll(fishName);
                // wait for deposit to complete
                sleepUntil(() -> !Rs2Inventory.contains(fishName), 2000);
            }
        }
        
        Rs2Bank.closeBank();
        closestBank = null;
        state = AutoFishingState.RETURNING;
    }

    private void handleReturning() {
        Microbot.status = "Returning to fishing location...";
        
        if (fishingLocation == null) {
            state = AutoFishingState.TRAVELING;
            return;
        }
        
        if (isAtFishingLocation()) {
            state = AutoFishingState.FISHING;
            return;
        }
        
        Rs2Walker.walkTo(fishingLocation);
    }

    private void handleErrorRecovery() {
        Microbot.status = "Recovering from error...";
        
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        if (Rs2DepositBox.isOpen()) {
            Rs2DepositBox.closeDepositBox();
        }
        
        fishAction = "";
        // wait for any animations to complete during recovery
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 3000);
        
        changeState(AutoFishingState.CHECKING_GEAR);
    }

    private void handleKarambwanLogic() {
        if (Rs2Inventory.hasItem(ItemID.TBWT_RAW_KARAMBWANJI) && Rs2Inventory.hasItem(ItemID.TBWT_KARAMBWAN_VESSEL)) {
            Rs2Inventory.combineClosest(ItemID.TBWT_RAW_KARAMBWANJI, ItemID.TBWT_KARAMBWAN_VESSEL);
            // wait for karambwanji combination to complete
            sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.TBWT_RAW_KARAMBWANJI) || Rs2Player.isAnimating(), 3000);
        }
    }

    /**
     * Select a fishing location and try to find the nearest fishing spot (NPC) based on the player's location.
     * This method prefers an actual NPC spot near the player (within a small radius) and falls back to the
     * fish's known closest location list.
     */
    private void selectFishingLocationBasedOnPlayer() {
        currentLocationIndex = 0;

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        // try to find a nearby fishing NPC that matches the selected fish
        WorldPoint nearestNpcPoint = findNearestFishingNpcPoint(playerLocation, 10);
        if (nearestNpcPoint != null) {
            fishingLocation = nearestNpcPoint;
            return;
        }

        // fallback: use the fish's known closest location
        fishingLocation = selectedFish.getClosestLocation(playerLocation);
    }

    /**
     * Search for the nearest NPC that corresponds to the selected fish's fishing spot IDs.
     * Returns the NPC's WorldPoint if one is within maxDistance tiles from playerLocation, otherwise null.
     */
    private WorldPoint findNearestFishingNpcPoint(WorldPoint playerLocation, int maxDistance) {
        if (selectedFish == null || selectedFish.getFishingSpot() == null) return null;

        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int npcId : selectedFish.getFishingSpot()) {
            Rs2NpcModel npc = Rs2Npc.getNpc(npcId);
            if (npc == null) continue;
            WorldPoint loc = npc.getWorldLocation();
            int d = loc.distanceTo(playerLocation);
            if (d < bestDist && d <= maxDistance) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    // helper method to change state with timeout reset
    private void changeState(AutoFishingState newState) {
        if (newState != state) {
            log.info("State change: {} -> {}", state, newState);
            state = newState;
            stateStartTime = System.currentTimeMillis();
        }
    }

    private boolean hasRawFishToCook() {
        for (String rawName : selectedFish.getRawNames()) {
            if (Rs2Inventory.contains(rawName)) {
                return true;
            }
        }
        return false;
    }

    private TileObject getNearbyFireOrRange() {
        Integer[] fireAndRangeIds = {
            ObjectID.FIRE,
            ObjectID.CAMPFIRE,
            ObjectID.BONFIRE,
            ObjectID.FIRE_43475
        };
        return Rs2GameObject.getGameObject(fireAndRangeIds, 15);
    }

    private boolean cookAllRawFish(TileObject fireOrRange) {
        boolean cookedAny = false;
        for (String rawName : selectedFish.getRawNames()) {
            if (!Rs2Inventory.contains(rawName)) continue;
            Rs2Inventory.useUnNotedItemOnObject(rawName, fireOrRange);
            boolean dialogAppeared = sleepUntil(() -> !Rs2Player.isMoving() && Rs2Widget.findWidget("How many would you like to cook?", null, false) != null);
            if (dialogAppeared) Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();

            sleepUntil(() -> !hasRawFishToCook() && !Rs2Player.isAnimating(), 3_000);
            cookedAny = true;
        }
        return cookedAny;
    }

    private boolean tryCookNearby() {
        if (!config.cookFish() || !hasRawFishToCook()) return false;
        TileObject fireOrRange = getNearbyFireOrRange();
        if (fireOrRange != null) {
            Microbot.status = "Cooking";
            return cookAllRawFish(fireOrRange);
        }
        return false;
    }

    @Override
    public void shutdown() {
        log.info("Shutting down auto fishing script");
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        
        // cleanup any open interfaces
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        if (Rs2DepositBox.isOpen()) {
            Rs2DepositBox.closeDepositBox();
        }
        
        // reset all state variables
        closestBank = null;
        fishingLocation = null;
        currentLocationIndex = 0;
        fishAction = "";
        log.info("Auto fishing script shutdown complete");
    }
}