package net.runelite.client.plugins.microbot.SulphurNaguaFigther;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
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


import static java.lang.Math.min;

public class SulphurNaguaScript extends Script {

    @Getter
    @RequiredArgsConstructor
    public enum NaguaLocation {
        CIVITAS_ILLA_FORTIS_WEST("West",
                new WorldArea(1344, 9553, 25, 25, 0),
                new WorldPoint(1376, 9712, 0),
                new WorldPoint(1452, 9568, 1)),

        CIVITAS_ILLA_FORTIS_EAST("East",
                new WorldArea(1371, 9557, 10, 10, 0),
                new WorldPoint(1376, 9712, 0),
                new WorldPoint(1452, 9568, 1));

        private final String name;
        private final WorldArea combatArea;
        private final WorldPoint prepArea;
        private final WorldPoint bankArea;

        @Override
        public String toString() {
            return name;
        }

        public WorldPoint getFightAreaCenter() {
            return new WorldPoint(
                    this.combatArea.getX() + this.combatArea.getWidth() / 2,
                    this.combatArea.getY() + this.combatArea.getHeight() / 2,
                    this.combatArea.getPlane()
            );
        }
    }

    public enum SulphurNaguaState {
        IDLE,
        BANKING,
        WALKING_TO_BANK,
        WALKING_TO_PREP,
        PREPARATION,
        PICKUP,
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
    private int potionsToPickup = 0;
    private boolean pickupReady = false;

    private final int PESTLE_AND_MORTAR_ID = 233;
    private final int VIAL_OF_WATER_ID = 227;
    private final int MOONLIGHT_GRUB_ID = 29078;
    private final int MOONLIGHT_GRUB_PASTE_ID = 29079;
    private final Set<Integer> MOONLIGHT_POTION_IDS = Set.of(29080, 29081, 29082, 29083);

    private NaguaLocation selectedLocation;

    public WorldArea getNaguaCombatArea() {
        return (selectedLocation != null) ? selectedLocation.getCombatArea() : null;
    }

    public boolean run(SulphurNaguaConfig config) {
        Microbot.enableAutoRunOn = true;
        currentState = SulphurNaguaState.IDLE;
        selectedLocation = config.naguaLocation();

        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                if (!hasInitialized) {
                    startTotalExp = Microbot.getClient().getOverallExperience();
                    if (startTotalExp > 0) hasInitialized = true;
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
                    case PICKUP:
                        pickupDroppedPotions();
                        break;
                    case WALKING_TO_FIGHT:
                        Rs2Walker.walkTo(selectedLocation.getFightAreaCenter());
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                    case IDLE:
                        // Do nothing
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

    private void determineState(SulphurNaguaConfig config) {
        int targetPotions = config.moonlightPotionsMinimum() == 0 ? 27 : config.moonlightPotionsMinimum();
        boolean hasPotionsInInventory = countMoonlightPotions() > 0;
        int totalOwnedPotions = countMoonlightPotions() + potionsToPickup;

        if (!Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID)) {
            resetPreparationState();
            currentState = isAtLocation(selectedLocation.getBankArea()) ? SulphurNaguaState.BANKING : SulphurNaguaState.WALKING_TO_BANK;
            return;
        }

        if (potionsToPickup > 0 && pickupReady) {
            currentState = SulphurNaguaState.PICKUP;
            return;
        }

        boolean inCombatZone = isAtLocation(selectedLocation.getFightAreaCenter());
        if ((currentState == SulphurNaguaState.FIGHTING || currentState == SulphurNaguaState.WALKING_TO_FIGHT || (currentState == SulphurNaguaState.IDLE && inCombatZone)) && hasPotionsInInventory) {
            currentState = inCombatZone ? SulphurNaguaState.FIGHTING : SulphurNaguaState.WALKING_TO_FIGHT;
            return;
        }

        boolean hasIntermediateIngredients = hasIngredientsToProcess();
        if (totalOwnedPotions < targetPotions || hasIntermediateIngredients) {
            if (currentState == SulphurNaguaState.FIGHTING) { // Ran out of potions during combat
                Rs2Prayer.disableAllPrayers();
                Microbot.log("All potions used. Starting preparation for a new batch.");
            }
            currentState = isAtLocation(selectedLocation.getPrepArea()) ? SulphurNaguaState.PREPARATION : SulphurNaguaState.WALKING_TO_PREP;
            return;
        }

        currentState = inCombatZone ? SulphurNaguaState.FIGHTING : SulphurNaguaState.WALKING_TO_FIGHT;
    }

    private void handlePreparation(SulphurNaguaConfig config) {
        int targetPotions = config.moonlightPotionsMinimum() == 0 ? 27 : config.moonlightPotionsMinimum();
        if (targetPotions > 27) targetPotions = 27;

        if (hasIngredientsToProcess()) {
            processAllIngredients();
            return;
        }

        int currentPotions = countMoonlightPotions();
        if (currentPotions >= targetPotions) {
            cleanupLeftoverIngredients();
            return;
        }

        final int phase1Max = 13;
        final int phase2Max = 20;

        // Phase 1: Up to 13 potions (or target, if lower)
        if (currentPotions < min(phase1Max, targetPotions)) {
            int needed = min(phase1Max, targetPotions) - currentPotions;
            if (Rs2Inventory.count(VIAL_OF_WATER_ID) < needed) {
                getSupplies(VIAL_OF_WATER_ID, needed);
                return;
            }
            if (Rs2Inventory.count(MOONLIGHT_GRUB_ID) < needed) {
                getSupplies(MOONLIGHT_GRUB_ID, needed);
            }
        }
        // Phase 2: Up to 20 potions (or target, if lower)
        else if (currentPotions < min(phase2Max, targetPotions)) {
            int needed = min(phase2Max, targetPotions) - currentPotions;
            if (Rs2Inventory.count(VIAL_OF_WATER_ID) < needed) {
                getSupplies(VIAL_OF_WATER_ID, needed);
                return;
            }
            if (Rs2Inventory.count(MOONLIGHT_GRUB_ID) < needed) {
                getSupplies(MOONLIGHT_GRUB_ID, needed);
            }
        }
        // Phase 3: From 20 up to the final target (handles dropping)
        else {
            int needed = targetPotions - currentPotions;

            if (Rs2Inventory.count(VIAL_OF_WATER_ID) < needed) {
                getSupplies(VIAL_OF_WATER_ID, needed);
                return;
            }

            int grubsToGet = needed - Rs2Inventory.count(MOONLIGHT_GRUB_ID);
            if (grubsToGet > 0) {
                int freeSlots = Rs2Inventory.emptySlotCount();
                if (freeSlots < grubsToGet) {
                    int potionsToDrop = grubsToGet - freeSlots;
                    if (potionsToDrop > 0) {
                        dropPotions(potionsToDrop);
                        return;
                    }
                }
                if (Rs2Inventory.count(MOONLIGHT_GRUB_ID) < needed) {
                    getSupplies(MOONLIGHT_GRUB_ID, needed);
                }
            }
        }
    }

    private void processAllIngredients() {
        if (Rs2Player.isAnimating() || Microbot.isGainingExp) {
            return;
        }
        if (Rs2Inventory.hasItem(MOONLIGHT_GRUB_ID)) {
            Microbot.log("Grinding all available grubs...");
            Rs2Inventory.use(PESTLE_AND_MORTAR_ID);
            sleep(100, 150);
            Rs2Inventory.use(MOONLIGHT_GRUB_ID);
            sleepUntil(() -> !Rs2Inventory.hasItem(MOONLIGHT_GRUB_ID) || Rs2Dialogue.isInDialogue(), 18000);
            sleep(600, 1000);
            return;
        }
        if (Rs2Inventory.hasItem(MOONLIGHT_GRUB_PASTE_ID) && Rs2Inventory.hasItem(VIAL_OF_WATER_ID)) {
            Microbot.log("Mixing all available paste...");
            Rs2Inventory.use(MOONLIGHT_GRUB_PASTE_ID);
            sleep(100, 150);
            Rs2Inventory.use(VIAL_OF_WATER_ID);
            sleepUntil(() -> !Rs2Inventory.hasItem(MOONLIGHT_GRUB_PASTE_ID) || Rs2Dialogue.isInDialogue(), 18000);
            sleep(600, 1000);
        }

        // After processing is fully complete, allow picking up dropped potions.
        if (!hasIngredientsToProcess()) {
            pickupReady = true;
        }
    }

    private void getSupplies(int itemID, int requiredAmount) {
        if (Rs2Inventory.count(itemID) >= requiredAmount) return;

        if (itemID == VIAL_OF_WATER_ID) {
            long startTime = System.currentTimeMillis();
            while (Rs2Inventory.count(itemID) < requiredAmount && System.currentTimeMillis() - startTime < 20000) {
                if (Rs2Inventory.isFull()) break;
                if (Rs2Dialogue.hasDialogueOption("Take herblore supplies.")) {
                    Rs2Dialogue.clickOption("Take herblore supplies.");
                } else if (!Rs2Player.isAnimating()) {
                    int SUPPLY_CRATE_ID = 51371;
                    Rs2GameObject.interact(SUPPLY_CRATE_ID, "Take-from");
                }
                sleep(400, 600);
            }
        } else {
            if (Rs2Player.isAnimating()) return;
            int GRUB_SAPLING_ID = 51365;
            if (Rs2GameObject.interact(GRUB_SAPLING_ID, "Collect-from")) {
                sleepUntil(() -> Rs2Inventory.count(itemID) >= requiredAmount || Rs2Inventory.isFull(), 15000);
                // Actively stop the gathering animation if we have enough
                if (Rs2Player.isAnimating() && Rs2Inventory.count(itemID) >= requiredAmount) {
                    Rs2Walker.walkTo(Rs2Player.getWorldLocation());
                }
            }
        }
    }

    private void dropPotions(int count) {
        if (count <= 0) return;
        if (dropLocation == null) dropLocation = Rs2Player.getWorldLocation();
        this.potionsToPickup = count;
        this.pickupReady = false;

        int dropped = 0;
        while (true) {
            boolean droppedThisRound = false;
            for (int potionId : MOONLIGHT_POTION_IDS) {
                if (Rs2Inventory.hasItem(potionId)) {
                    Rs2Inventory.drop(potionId);
                    sleep(250, 450);
                    dropped++;
                    droppedThisRound = true;
                    if (dropped >= count) break;
                }
            }
            if (!droppedThisRound || dropped >= count) break;
        }
        Microbot.log("Dropped " + dropped + " potions at " + dropLocation);
    }

    private void pickupDroppedPotions() {
        if (Rs2Inventory.isFull()) {
            Microbot.log("Inventory is full, cannot pick up.");
            return;
        }
        if (potionsToPickup <= 0) {
            resetPreparationState();
            return;
        }

        boolean foundPotion = false;
        for (int potionId : MOONLIGHT_POTION_IDS) {
            if (Rs2GroundItem.exists(potionId, 15)) {
                foundPotion = true;
                int potionsBefore = countMoonlightPotions();
                if (Rs2GroundItem.interact(potionId, "Take", 15)) {
                    if (sleepUntil(() -> countMoonlightPotions() > potionsBefore, 3000)) {
                        potionsToPickup--;
                    }
                }
                break;
            }
        }

        if (potionsToPickup <= 0) {
            Microbot.log("Finished picking up all potions.");
            resetPreparationState();
        } else if (!foundPotion) {
            Microbot.log("Could not find any more dropped potions. Resetting.");
            resetPreparationState();
        }
    }

    private int countMoonlightPotions() {
        return MOONLIGHT_POTION_IDS.stream().mapToInt(Rs2Inventory::count).sum();
    }

    private boolean hasIngredientsToProcess() {
        return Rs2Inventory.hasItem(MOONLIGHT_GRUB_ID) ||
                (Rs2Inventory.hasItem(MOONLIGHT_GRUB_PASTE_ID) && Rs2Inventory.hasItem(VIAL_OF_WATER_ID));
    }

    private void cleanupLeftoverIngredients() {
        Microbot.log("Cleaning up leftover ingredients...");
        if (Rs2Inventory.hasItem("Vial")) Rs2Inventory.dropAll("Vial");
        sleep(400, 600);
        if (Rs2Inventory.hasItem(VIAL_OF_WATER_ID)) Rs2Inventory.dropAll(VIAL_OF_WATER_ID);
        sleep(400, 600);
        if (Rs2Inventory.hasItem(MOONLIGHT_GRUB_PASTE_ID)) Rs2Inventory.dropAll(MOONLIGHT_GRUB_PASTE_ID);
        sleep(400, 600);
        if (Rs2Inventory.hasItem(MOONLIGHT_GRUB_ID)) Rs2Inventory.dropAll(MOONLIGHT_GRUB_ID);
    }

    private void resetPreparationState() {
        dropLocation = null;
        potionsToPickup = 0;
        pickupReady = false;
    }

    private void handleFighting(SulphurNaguaConfig config) {
        int basePrayerLevel = client.getRealSkillLevel(Skill.PRAYER);
        int currentHerbloreLevel = client.getBoostedSkillLevel(Skill.HERBLORE);

        int prayerBasedRestore = (int) Math.floor(basePrayerLevel * 0.25) + 7;
        int herbloreBasedRestore = (int) Math.floor(currentHerbloreLevel * 0.3) + 7;
        int dynamicThreshold = Math.max(prayerBasedRestore, herbloreBasedRestore);


        Rs2Player.drinkPrayerPotionAt(dynamicThreshold);
        sleep(600);


        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);


        if (config.useOffensivePrayers() && Rs2Player.isInCombat()) {
            var bestMeleePrayer = Rs2Prayer.getBestMeleePrayer();
            if (bestMeleePrayer != null) {
                Rs2Prayer.toggle(bestMeleePrayer, true);
            }
        }

        if (!Rs2Player.isInCombat()) {
            if (getNaguaCombatArea() != null && getNaguaCombatArea().contains(Rs2Player.getWorldLocation())) {
                String NAGUA_NAME = "Sulphur Nagua";
                if (Rs2Npc.attack(NAGUA_NAME)) {
                    sleepUntil(Rs2Player::isInCombat, 5000);
                    totalNaguaKills++;
                }
            } else {
                Microbot.log("Outside combat zone, walking back to center...");
                Rs2Walker.walkTo(selectedLocation.getFightAreaCenter());
                sleep(600, 1200);
            }
        }
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

    private void handleBanking(SulphurNaguaConfig config) {
        try {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
                    return;
                }
            }

            InventorySetup setupData = config.useInventorySetup() ? config.inventorySetup() : null;

            if (setupData != null) {
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
                        shutdown();
                        return;
                    }
                }
            } else {
                Rs2Bank.depositAll();
                sleep(300, 600);
                Rs2Bank.withdrawItem(PESTLE_AND_MORTAR_ID);
                if (!sleepUntil(() -> Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID), 2000)) {
                    shutdown();
                    return;
                }
            }

            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            }

            if (setupData != null) {
                new Rs2InventorySetup(setupData, mainScheduledFuture).wearEquipment();
            }

            resetPreparationState();
        } finally {
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
            }
        }
    }

    private boolean isAtLocation(WorldPoint worldPoint) {
        return Rs2Player.getWorldLocation().distanceTo(worldPoint) < 10;
    }
}