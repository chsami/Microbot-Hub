package net.runelite.client.plugins.microbot.SulphurNaguaFigther;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
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

public class SulphurNaguaScript extends Script {

    public static String version = "1.5";

    @Getter
    @RequiredArgsConstructor
    public enum NaguaLocation {
        CIVITAS_ILLA_FORTIS_WEST("West", new WorldPoint(1356, 9565, 0), new WorldPoint(1376, 9712, 0), new WorldPoint(1452, 9568, 1)),
        CIVITAS_ILLA_FORTIS_EAST("East", new WorldPoint(1577, 9564, 0), new WorldPoint(1567, 9711, 0), new WorldPoint(1452, 9568, 1));

        private final String name;
        private final WorldPoint fightArea;
        private final WorldPoint prepArea;
        private final WorldPoint bankArea;

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SulphurNaguaState {
        IDLE,
        BANKING,
        WALKING_TO_BANK,
        WALKING_TO_PREP,
        PREPARATION,
        WALKING_TO_FIGHT,
        FIGHTING
    }

    public SulphurNaguaState currentState = SulphurNaguaState.IDLE;
    public int totalNaguaKills = 0;
    @Getter
    private long startTotalExp = 0;
    private boolean hasInitialized = false;

    @Inject
    private Client client;

    private WorldPoint dropLocation = null;
    private boolean pickupPending = false;
    private int droppedPotionCount = 0;
    private boolean isBankingInProgress = false;

    private final String NAGUA_NAME = "Sulphur Nagua";
    private final String POTION_NAME = "Moonlight potion";
    private final int PESTLE_AND_MORTAR_ID = 233;
    private final int VIAL_OF_WATER_ID = 227;
    private final int MOONLIGHT_GRUB_ID = 29078;
    private final int MOONLIGHT_GRUB_PASTE_ID = 29079;
    private final Set<Integer> MOONLIGHT_POTION_IDS = Set.of(29080, 29081, 29082, 29083);
    private final int SUPPLY_CRATE_ID = 51371;
    private final int GRUB_SAPLING_ID = 51365;

    @Getter
    private WorldArea naguaCombatArea;
    private NaguaLocation selectedLocation;

    public boolean run(SulphurNaguaConfig config) {
        Microbot.enableAutoRunOn = true;
        currentState = SulphurNaguaState.IDLE;
        selectedLocation = config.naguaLocation();

        int combatRadius = 12;
        this.naguaCombatArea = new WorldArea(selectedLocation.getFightArea().dx(-combatRadius).dy(-combatRadius), (combatRadius * 2) + 1, (combatRadius * 2) + 1);

        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                // Initialize starting XP only once the player is logged in.
                if (!hasInitialized) {
                    startTotalExp = Microbot.getClient().getOverallExperience();
                    if (startTotalExp > 0) {
                        hasInitialized = true;
                    }
                    return;
                }

                Rs2Antiban.takeMicroBreakByChance();
                determineState(config);

                switch (currentState) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case WALKING_TO_BANK:
                        Rs2Walker.walkTo(selectedLocation.getBankArea());
                        break;
                    case WALKING_TO_PREP:
                        Rs2Walker.walkTo(selectedLocation.getPrepArea());
                        break;
                    case PREPARATION:
                        handlePreparation(config);
                        break;
                    case WALKING_TO_FIGHT:
                        Rs2Walker.walkTo(selectedLocation.getFightArea());
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                    case IDLE:
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

    private void handleFighting(SulphurNaguaConfig config) {
        // Calculate dynamic prayer restore threshold and drink potions
        int basePrayerLevel = client.getRealSkillLevel(Skill.PRAYER);
        int currentHerbloreLevel = client.getBoostedSkillLevel(Skill.HERBLORE);
        int prayerBasedRestore = (int) floor(basePrayerLevel * 0.25) + 7;
        int herbloreBasedRestore = (int) floor(currentHerbloreLevel * 0.3) + 7;
        int dynamicThreshold = max(prayerBasedRestore, herbloreBasedRestore);
        Rs2Player.drinkPrayerPotionAt(dynamicThreshold);
        sleep(600);

        // Activate prayers
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        if (config.usePiety() && Rs2Prayer.getBestMeleePrayer() != null) {
            Rs2Prayer.toggle(Rs2Prayer.getBestMeleePrayer(), true);
        }

        // Attack Naguas
        if (!Rs2Player.isInCombat()) {
            if (naguaCombatArea.contains(Rs2Player.getWorldLocation())) {
                if (Rs2Npc.attack(NAGUA_NAME)) {
                    sleepUntil(Rs2Player::isInCombat, 5000);
                    totalNaguaKills++;
                }
            } else {
                Microbot.log("Outside combat zone, walking back to center...");
                Rs2Walker.walkTo(selectedLocation.getFightArea());
                sleep(600, 1200);
            }
        }
    }

    private void determineState(SulphurNaguaConfig config) {
        // High-priority checks to transition to FIGHTING state immediately.
        // Fixes bugs when starting script in combat or arriving at destination.
        if ((currentState == SulphurNaguaState.IDLE && Rs2Player.isInCombat()) ||
                (currentState == SulphurNaguaState.WALKING_TO_FIGHT && isAtLocation(selectedLocation.getFightArea()))) {
            currentState = SulphurNaguaState.FIGHTING;
            return;
        }

        // Don't re-evaluate state if we are already in combat.
        if (currentState == SulphurNaguaState.FIGHTING && Rs2Player.isInCombat()) {
            return;
        }

        if (isBankingInProgress) {
            return;
        }

        boolean hasPestle = Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID);
        int minPotions = config.moonlightPotionsMinimum();
        if (minPotions == 0) minPotions = 27;

        int currentPotions = countMoonlightPotions() + droppedPotionCount;
        boolean needsMorePotions;
        if (currentState == SulphurNaguaState.FIGHTING || currentState == SulphurNaguaState.WALKING_TO_FIGHT) {
            needsMorePotions = currentPotions < 1;
        } else {
            needsMorePotions = currentPotions < minPotions;
        }

        if (!needsMorePotions && droppedPotionCount > 0) {
            pickupPending = true;
        }

        if (!needsMorePotions && hasLeftoverIngredients()) {
            currentState = SulphurNaguaState.PREPARATION;
            return;
        }

        if (!hasPestle) {
            dropLocation = null;
            pickupPending = false;
            droppedPotionCount = 0;
            currentState = isAtLocation(selectedLocation.getBankArea()) ? SulphurNaguaState.BANKING : SulphurNaguaState.WALKING_TO_BANK;
            return;
        }

        if (needsMorePotions || pickupPending) {
            if (currentState != SulphurNaguaState.WALKING_TO_PREP) {
                Microbot.log("Conserving prayer, deactivating prayers...");
                Rs2Prayer.disableAllPrayers();
            }
            currentState = isAtLocation(selectedLocation.getPrepArea()) ? SulphurNaguaState.PREPARATION : SulphurNaguaState.WALKING_TO_PREP;
            return;
        }

        currentState = isAtLocation(selectedLocation.getFightArea()) ? SulphurNaguaState.FIGHTING : SulphurNaguaState.WALKING_TO_FIGHT;
    }

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

    private boolean hasLeftoverIngredients() {
        return Rs2Inventory.hasItem(VIAL_OF_WATER_ID) ||
                Rs2Inventory.hasItem(MOONLIGHT_GRUB_ID) ||
                Rs2Inventory.hasItem(MOONLIGHT_GRUB_PASTE_ID);
    }

    private void handleBanking(SulphurNaguaConfig config) {
        isBankingInProgress = true;
        try {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
                    isBankingInProgress = false;
                    return;
                }
            }

            // Get the inventory setup data object.
            InventorySetup setupData = config.useInventorySetup() ? config.inventorySetup() : null;

            if (setupData != null) {
                Microbot.log("Using Inventory Setup: " + setupData.getName());
                Rs2Bank.depositAll();
                sleepUntil(Rs2Inventory::isEmpty, 2000);
                Rs2Bank.depositEquipment();
                Rs2Random.wait(300, 600);

                if (setupData.getEquipment() != null) {
                    for (InventorySetupsItem item : setupData.getEquipment()) {
                        Rs2Bank.withdrawItem(item.getId());
                    }
                }

                if (setupData.getInventory() != null) {
                    for (InventorySetupsItem item : setupData.getInventory()) {
                        final int currentAmountInInv = Rs2Inventory.count(item.getId());
                        final int requiredAmount = item.getQuantity();
                        if (currentAmountInInv < requiredAmount) {
                            Rs2Bank.withdrawX(item.getId(), requiredAmount - currentAmountInInv);
                        }
                    }
                }

                if (!Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID)) {
                    Rs2Bank.withdrawItem(PESTLE_AND_MORTAR_ID);
                    if (!sleepUntil(() -> Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID), 2000)) {
                        Microbot.showMessage("Pestle and mortar not in bank. Stopping script.");
                        shutdown();
                        return;
                    }
                }
            } else {
                // Default banking without an inventory setup.
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

            // Create the setup manager utility and execute wearing equipment.
            if (setupData != null) {
                Rs2InventorySetup setupManager = new Rs2InventorySetup(setupData, mainScheduledFuture);
                setupManager.wearEquipment();
            }

        } finally {
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
            }
            isBankingInProgress = false;
        }
    }

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

    private void handlePreparation(SulphurNaguaConfig config) {
        if (pickupPending) {
            if (dropLocation != null && !isAtLocation(dropLocation)) {
                Rs2Walker.walkTo(dropLocation);
                sleep(600, 1000);
                return;
            }

            while (!Rs2Inventory.isFull() && isRunning()) {
                int invCountBefore = Rs2Inventory.count();
                if (Rs2GroundItem.interact(POTION_NAME, "Take")) {
                    if (!sleepUntil(() -> Rs2Inventory.count() > invCountBefore, 3000)) {
                        break;
                    }
                } else {
                    break;
                }
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
            cleanupInventory();
            return;
        }

        boolean needsToMakeSpace = Rs2Inventory.emptySlotCount() < 2;
        if (needsToMakeSpace && countMoonlightPotions() > 0 && dropLocation == null) {
            Microbot.log("Not enough space. Dropping potions until 2 slots are free.");
            dropLocation = Rs2Player.getWorldLocation();

            while (Rs2Inventory.emptySlotCount() < 2 && isRunning()) {
                int potionToDrop = MOONLIGHT_POTION_IDS.stream()
                        .filter(Rs2Inventory::hasItem)
                        .findFirst()
                        .orElse(-1);

                if (potionToDrop != -1) {
                    Rs2Inventory.drop(potionToDrop);
                    droppedPotionCount++;
                    sleep(400, 600);
                } else {
                    break;
                }
            }
            return;
        }

        makePotionBatch(config);
    }

    private void makePotionBatch(SulphurNaguaConfig config) {
        if (Rs2Inventory.hasItem(MOONLIGHT_GRUB_ID)) {
            Rs2Inventory.use(PESTLE_AND_MORTAR_ID);
            sleep(50, 150);
            Rs2Inventory.use(MOONLIGHT_GRUB_ID);
            sleep(600);
            return;
        }
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
                pickupPending = true;
            }
            return;
        }

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

        int vialsWeHave = Rs2Inventory.count(VIAL_OF_WATER_ID);
        if (vialsWeHave > 0 && Rs2Inventory.count(MOONLIGHT_GRUB_ID) < vialsWeHave) {
            if (Rs2GameObject.interact(GRUB_SAPLING_ID, "Collect-from")) {
                boolean collectedEnough = sleepUntil(() -> Rs2Inventory.count(MOONLIGHT_GRUB_ID) >= vialsWeHave || Rs2Inventory.isFull(), 8000);
                if (collectedEnough && Rs2Player.isAnimating()) {
                    Microbot.log("Collected enough grubs, interrupting action.");
                    Rs2Walker.walkTo(Rs2Player.getWorldLocation());
                    sleep(200, 400);
                }
            }
        }
    }

    private boolean isAtLocation(WorldPoint worldPoint) {
        return Rs2Player.getWorldLocation().distanceTo(worldPoint) < 10;
    }

    private int countMoonlightPotions() {
        return MOONLIGHT_POTION_IDS.stream().mapToInt(Rs2Inventory::count).sum();
    }
}