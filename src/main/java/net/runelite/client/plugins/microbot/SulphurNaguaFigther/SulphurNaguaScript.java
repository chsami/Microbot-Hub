package net.runelite.client.plugins.microbot.SulphurNaguaFigther;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.floor;
import static java.lang.Math.max;

/**
 * This is the main script logic for the Sulphur Nagua fighter.
 * It handles all actions like banking, potion making, and combat.
 */
public class SulphurNaguaScript extends Script {

    public static String version = "1.0"; // Pickup logic corrected to use item names

    /**
     * Defines the possible states for the script.
     */
    public enum SulphurNaguaState {
        IDLE,
        BANKING,
        WALKING_TO_BANK,
        WALKING_TO_PREP,
        PREPARATION,
        WALKING_TO_FIGHT,
        FIGHTING
    }

    // --- Script State & Variables ---
    public SulphurNaguaState currentState = SulphurNaguaState.IDLE;
    public int totalNaguaKills = 0;

    @Inject
    private Client client;

    // Potion dropping/picking up logic
    private WorldPoint dropLocation = null;
    private boolean pickupPending = false;
    private int droppedPotionCount = 0;
    private boolean isBankingInProgress = false;

    // Timer for potion effect
    private long lastPotionDrinkTime = 0;

    // --- Constants ---
    private final String NAGUA_NAME = "Sulphur Nagua";
    private final String POTION_NAME = "Moonlight potion";
    private final int PESTLE_AND_MORTAR_ID = 233;
    private final int VIAL_OF_WATER_ID = 227;
    private final int MOONLIGHT_GRUB_ID = 29078;
    private final int MOONLIGHT_GRUB_PASTE_ID = 29079;
    private final Set<Integer> MOONLIGHT_POTION_IDS = Set.of(29080, 29081, 29082, 29083); // 4, 3, 2, 1 doses
    private final int SUPPLY_CRATE_ID = 51371;
    private final int GRUB_SAPLING_ID = 51365;

    // --- Key Locations ---
    final WorldPoint BANK_AREA = new WorldPoint(1452, 9568, 1);
    final WorldPoint PREPARATION_AREA = new WorldPoint(1376, 9712, 0);
    final WorldPoint FIGHT_AREA = new WorldPoint(1356, 9565, 0);

    @Getter
    private WorldArea naguaCombatArea; // Defines the valid combat area for Naguas

    /**
     * Main entry point for the script. Sets up the main loop.
     * @param config The user configuration.
     * @return true if the script started successfully.
     */
    public boolean run(SulphurNaguaConfig config) {
        Microbot.enableAutoRunOn = true;
        currentState = SulphurNaguaState.IDLE;

        // Define the combat area
        int combatRadius = 12;
        this.naguaCombatArea = new WorldArea(FIGHT_AREA.dx(-combatRadius).dy(-combatRadius), (combatRadius * 2) + 1, (combatRadius * 2) + 1);

        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);

        // Main script loop
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                Rs2Antiban.takeMicroBreakByChance();
                determineState(config); // Determine the next action

                // Execute action based on the current state
                switch (currentState) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case WALKING_TO_BANK:
                        Rs2Walker.walkTo(BANK_AREA);
                        break;
                    case WALKING_TO_PREP:
                        Rs2Walker.walkTo(PREPARATION_AREA);
                        break;
                    case PREPARATION:
                        handlePreparation(config);
                        break;
                    case WALKING_TO_FIGHT:
                        Rs2Walker.walkTo(FIGHT_AREA);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                    case IDLE:
                        // Do nothing, wait for the next state change
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        currentState = SulphurNaguaState.IDLE;
        Rs2Antiban.resetAntibanSettings();
    }

    /**
     * Handles all combat-related logic.
     */
    private void handleFighting(SulphurNaguaConfig config) {
        // --- Potion Drinking Logic ---
        int basePrayerLevel = client.getRealSkillLevel(Skill.PRAYER);
        int currentHerbloreLevel = client.getBoostedSkillLevel(Skill.HERBLORE);
        int currentPrayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);
        // Calculate dynamic prayer point threshold for drinking
        int prayerBasedRestore = (int) floor(basePrayerLevel * 0.25) + 7;
        int herbloreBasedRestore = (int) floor(currentHerbloreLevel * 0.3) + 7;
        int dynamicThreshold = max(prayerBasedRestore, herbloreBasedRestore);
        boolean shouldDrink = currentPrayerPoints < dynamicThreshold || !isMoonlightPotionActive();

        if (countMoonlightPotions() > 0 && shouldDrink) {
            drinkMoonlightPotion();
            sleep(1200);
        }

        // --- Prayer Activation ---
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        }
        if (config.usePiety() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        }

        // --- Attacking Logic ---
        if (!Rs2Player.isInCombat()) {
            if (naguaCombatArea.contains(Rs2Player.getWorldLocation())) {
                if (Rs2Npc.interact(NAGUA_NAME, "Attack")) {
                    sleepUntil(Rs2Player::isInCombat, 5000);
                    // Wait for the current combat to end (successful kill)
                    if (sleepUntil(() -> !Rs2Player.isInCombat(), 60000)) {
                        totalNaguaKills++;
                    }
                }
            } else {
                // If outside the combat area, walk back
                Microbot.log("Outside combat zone, walking back to center...");
                Rs2Walker.walkTo(FIGHT_AREA);
                sleep(600, 1200);
            }
        }
    }

    /**
     * This is the state machine. It determines what the script should be doing.
     */
    /**
     * This is the state machine. It determines what the script should be doing.
     */
    /**
     * This is the state machine. It determines what the script should be doing.
     */
    private void determineState(SulphurNaguaConfig config) {
        // Don't change state while in combat or during a bank operation
        if (Rs2Player.isInCombat() || isBankingInProgress) {
            return;
        }

        // --- SOLUTION ---
        // High-priority check: If the goal was to walk to the fight area and we have arrived,
        // immediately switch to FIGHTING. This prevents getting stuck in the WALK state.
        if (currentState == SulphurNaguaState.WALKING_TO_FIGHT && at(FIGHT_AREA)) {
            currentState = SulphurNaguaState.FIGHTING;
            return;
        }
        // --- END OF SOLUTION ---

        boolean hasPestle = Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID);
        int minPotions = config.moonlightPotionsMinimum();
        if (minPotions == 0) minPotions = 27; // 0 means fill inventory

        int currentPotions = countMoonlightPotions() + droppedPotionCount;
        boolean needsMorePotions;

        // Use a more critical threshold when already fighting
        if (currentState == SulphurNaguaState.FIGHTING || currentState == SulphurNaguaState.WALKING_TO_FIGHT) {
            needsMorePotions = currentPotions < 1;
        } else {
            needsMorePotions = currentPotions < minPotions;
        }

        if (!needsMorePotions && droppedPotionCount > 0) {
            pickupPending = true; // Enough potions, but need to pick up dropped ones
        }

        if (!needsMorePotions && hasLeftoverIngredients()) {
            currentState = SulphurNaguaState.PREPARATION; // Finish making potions with leftover items
            return;
        }

        // If we don't have a pestle, we must bank
        if (!hasPestle) {
            dropLocation = null;
            pickupPending = false;
            droppedPotionCount = 0;
            if (at(BANK_AREA)) {
                currentState = SulphurNaguaState.BANKING;
            } else {
                currentState = SulphurNaguaState.WALKING_TO_BANK;
            }
            return;
        }

        // If we need potions or need to pick some up, go to the prep area
        if (needsMorePotions || pickupPending) {
            if (at(PREPARATION_AREA)) {
                currentState = SulphurNaguaState.PREPARATION;
            } else {
                // Turn off prayers to save points when walking
                if (currentState != SulphurNaguaState.WALKING_TO_PREP) {
                    deactivateAllPrayers();
                }
                currentState = SulphurNaguaState.WALKING_TO_PREP;
            }
            return;
        }

        // If we have everything we need, go fight
        if (at(FIGHT_AREA)) {
            currentState = SulphurNaguaState.FIGHTING;
        } else {
            currentState = SulphurNaguaState.WALKING_TO_FIGHT;
        }
    }

    /**
     * Applies the recommended antiban settings for a combat script.
     */
    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
    }

    /**
     * Checks for any leftover potion ingredients in the inventory.
     */
    private boolean hasLeftoverIngredients() {
        return Rs2Inventory.hasItem(VIAL_OF_WATER_ID) ||
                Rs2Inventory.hasItem(MOONLIGHT_GRUB_ID) ||
                Rs2Inventory.hasItem(MOONLIGHT_GRUB_PASTE_ID);
    }

    /**
     * Handles all banking logic, using inventory setups if configured.
     */
    private void handleBanking(SulphurNaguaConfig config) {
        isBankingInProgress = true;
        try {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
                    isBankingInProgress = false; // Failed to open bank
                    return;
                }
            }

            InventorySetup setup = config.useInventorySetup() ? config.inventorySetup() : null;
            if (setup != null) {
                Microbot.log("Using Inventory Setup: " + setup.getName());
                Rs2Bank.depositAll();
                sleepUntil(Rs2Inventory::isEmpty, 2000);
                Rs2Bank.depositEquipment();
                Rs2Random.wait(300, 600);

                // Withdraw equipment
                if (setup.getEquipment() != null) {
                    for (InventorySetupsItem item : setup.getEquipment()) {
                        Rs2Bank.withdrawItem(item.getId());
                    }
                }

                // Withdraw inventory
                if (setup.getInventory() != null) {
                    for (InventorySetupsItem item : setup.getInventory()) {
                        final int currentAmountInInv = Rs2Inventory.count(item.getId());
                        final int requiredAmount = item.getQuantity();
                        if (currentAmountInInv < requiredAmount) {
                            Rs2Bank.withdrawX(item.getId(), requiredAmount - currentAmountInInv);
                        }
                    }
                }

                // Ensure we have a pestle and mortar
                if (!Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID)) {
                    Rs2Bank.withdrawItem(PESTLE_AND_MORTAR_ID);
                    if (!sleepUntil(() -> Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID), 2000)) {
                        Microbot.showMessage("Pestle and mortar not in bank. Stopping script.");
                        shutdown();
                        return;
                    }
                }
            } else {
                // Default banking without setup
                Rs2Bank.depositAll();
                sleep(300, 600);
                Rs2Bank.withdrawItem(PESTLE_AND_MORTAR_ID);
                if (!sleepUntil(() -> Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID), 2000)) {
                    Microbot.showMessage("Pestle and mortar not in bank. Stopping script.");
                    shutdown();
                    return;
                }
            }

            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            }

            // Equip items after closing the bank
            if (setup != null && setup.getEquipment() != null) {
                for (InventorySetupsItem item : setup.getEquipment()) {
                    Rs2Inventory.wear(item.getId());
                    sleepUntil(() -> Rs2Equipment.isWearing(item.getId()), 2000);
                    Rs2Random.wait(200, 400);
                }
            }
        } finally {
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank(); // Ensure bank is closed
            }
            isBankingInProgress = false; // Release the lock
        }
    }

    /**
     * Drops any leftover potion ingredients.
     */
    private void cleanupInventory() {
        Microbot.log("Cleaning up leftover ingredients from inventory...");
        Set<Integer> itemsToKeep = new java.util.HashSet<>(MOONLIGHT_POTION_IDS);
        itemsToKeep.add(PESTLE_AND_MORTAR_ID);

        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item != null && !itemsToKeep.contains(item.getId())) {
                Rs2Inventory.drop(item.getId());
                sleep(300, 500);
            }
        }
    }

    /**
     * Manages potion preparation, including picking up dropped potions and creating new ones.
     */
    private void handlePreparation(SulphurNaguaConfig config) {
        // --- Pick up dropped potions first ---
        if (pickupPending) {
            // Loop as long as there is space in the inventory.
            while (!Rs2Inventory.isFull()) {
                // Walk to drop location if far away
                if (dropLocation != null && Rs2Player.getWorldLocation().distanceTo(dropLocation) > 5) {
                    Rs2Walker.walkTo(dropLocation);
                    sleep(600, 1000);
                    continue;
                }

                int invCountBefore = Rs2Inventory.count();

                if (Rs2GroundItem.interact(POTION_NAME, "Take")) {
                    // Wait for the inventory count to change, confirming the pickup
                    if (sleepUntil(() -> Rs2Inventory.count() > invCountBefore, 3000)) {
                        continue; // Successfully picked up, get the next one
                    }
                }
                // No more potions to pick up or pickup failed, so exit loop
                Microbot.log("No more potions to pick up or pickup failed.");
                break;
            }

            Microbot.log("Pickup complete.");
            pickupPending = false;
            dropLocation = null;
            droppedPotionCount = 0;
            return;
        }

        int minPotions = config.moonlightPotionsMinimum() == 0 ? 27 : config.moonlightPotionsMinimum();
        int potionsStillNeeded = minPotions - (countMoonlightPotions() + droppedPotionCount);

        if (potionsStillNeeded <= 0) {
            cleanupInventory(); // We have enough, clean up and leave
            return;
        }

        // --- Drop potions to make space for ingredients ---
        boolean needsToMakeSpace = Rs2Inventory.emptySlotCount() < 2;
        if (needsToMakeSpace && countMoonlightPotions() > 0 && dropLocation == null) {
            Microbot.log("Not enough space. Dropping potions until 2 slots are free.");
            dropLocation = Rs2Player.getWorldLocation();

            while (Rs2Inventory.emptySlotCount() < 2) {
                int potionToDrop = MOONLIGHT_POTION_IDS.stream()
                        .filter(Rs2Inventory::hasItem)
                        .findFirst()
                        .orElse(-1);

                if (potionToDrop != -1) {
                    Rs2Inventory.drop(potionToDrop);
                    droppedPotionCount++;
                    sleep(400, 600);
                } else {
                    break; // No more potions to drop
                }
            }
            return;
        }

        // --- Make new potions ---
        makePotionBatch(config);
    }

    /**
     * Handles a single cycle of potion creation (get ingredients, combine).
     */
    private void makePotionBatch(SulphurNaguaConfig config) {
        // If we have grubs, grind them
        if (Rs2Inventory.hasItem(MOONLIGHT_GRUB_ID)) {
            Rs2Inventory.use(PESTLE_AND_MORTAR_ID);
            sleep(50, 150);
            Rs2Inventory.use(MOONLIGHT_GRUB_ID);
            sleep(600);
            return;
        }
        // If we have paste and vials, combine them
        if (Rs2Inventory.hasItem(MOONLIGHT_GRUB_PASTE_ID) && Rs2Inventory.hasItem(VIAL_OF_WATER_ID)) {
            Rs2Inventory.use(MOONLIGHT_GRUB_PASTE_ID);
            sleep(50, 150);
            Rs2Inventory.use(VIAL_OF_WATER_ID);
            sleep(600);
            return;
        }

        int minPotions = config.moonlightPotionsMinimum() == 0 ? 27 : config.moonlightPotionsMinimum();
        int totalPotionsSoFar = countMoonlightPotions() + droppedPotionCount;
        if ((minPotions - totalPotionsSoFar) <= 0) {
            if (dropLocation != null) {
                pickupPending = true; // Finished making, now pick them up
            }
            return;
        }

        // --- Gather ingredients ---
        // Get Vials of Water
        int targetIngredients = Math.min(minPotions - totalPotionsSoFar, Rs2Inventory.emptySlotCount() / 2);
        if (targetIngredients > 0 && Rs2Inventory.count(VIAL_OF_WATER_ID) < targetIngredients) {
            int initialVialCount = Rs2Inventory.count(VIAL_OF_WATER_ID);
            if (Rs2GameObject.interact(SUPPLY_CRATE_ID, "Take-from")) {
                if (sleepUntil(() -> Rs2Dialogue.hasDialogueOption("Take herblore supplies."), 5000)) {
                    Rs2Dialogue.clickOption("Take herblore supplies.");
                    sleepUntil(() -> Rs2Inventory.count(VIAL_OF_WATER_ID) > initialVialCount, 3000);
                }
            }
            return;
        }

        // Get Moonlight Grubs
        int vialsWeHave = Rs2Inventory.count(VIAL_OF_WATER_ID);
        if (vialsWeHave > 0 && Rs2Inventory.count(MOONLIGHT_GRUB_ID) < vialsWeHave) {
            if (Rs2GameObject.interact(GRUB_SAPLING_ID, "Collect-from")) {
                // Stop collecting as soon as we have enough grubs for our vials
                boolean collectedEnough = sleepUntil(() -> Rs2Inventory.count(MOONLIGHT_GRUB_ID) >= vialsWeHave || Rs2Inventory.isFull(), 8000);
                if (collectedEnough && Rs2Player.isAnimating()) {
                    Microbot.log("Collected enough grubs, interrupting action.");
                    Rs2Walker.walkTo(Rs2Player.getWorldLocation()); // Click to stop animation
                    sleep(200, 400);
                }
            }
        }
    }

    /**
     * Drinks the first available Moonlight Potion from the inventory.
     */
    private void drinkMoonlightPotion() {
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item != null && MOONLIGHT_POTION_IDS.contains(item.getId())) {
                if (Rs2Inventory.interact(item.getId(), "Drink")) {
                    lastPotionDrinkTime = System.currentTimeMillis();
                    break;
                }
            }
        }
    }

    /**
     * Turns off active combat prayers to conserve prayer points.
     */
    private void deactivateAllPrayers() {
        Microbot.log("No potions left, deactivating all prayers...");
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
            sleep(150, 300); // Short delay between actions
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, false);
            sleep(150, 300);
        }
    }

    /**
     * Checks if the player is within a 10-tile radius of a WorldPoint.
     * @param worldPoint The center point to check against.
     * @return true if the player is nearby.
     */
    private boolean at(WorldPoint worldPoint) {
        return Rs2Player.getWorldLocation().distanceTo(worldPoint) < 10;
    }

    /**
     * Counts all doses of Moonlight potions in the inventory.
     */
    private int countMoonlightPotions() {
        return MOONLIGHT_POTION_IDS.stream().mapToInt(Rs2Inventory::count).sum();
    }

    /**
     * Checks if the Moonlight Potion effect is likely active (lasts 5 minutes).
     * @return true if a potion was drunk within the last 5 minutes.
     */
    private boolean isMoonlightPotionActive() {
        return (System.currentTimeMillis() - lastPotionDrinkTime) < 300000; // 300,000 ms = 5 minutes
    }
}