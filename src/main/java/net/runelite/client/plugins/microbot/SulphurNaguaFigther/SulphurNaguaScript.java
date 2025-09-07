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

import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class SulphurNaguaScript extends Script {

    public static String version = "1.1";

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

    // State variables for the drop/pickup logic
    private WorldPoint dropLocation = null;
    private int potionsToPickup = 0;     // How many potions were intentionally dropped.
    private boolean pickupReady = false;  // Flag to allow pickup only after crafting is finished.
    private boolean isBankingInProgress = false;

    // Item and NPC constants
    private final String NAGUA_NAME = "Sulphur Nagua";
    private final int PESTLE_AND_MORTAR_ID = 233;
    private final int VIAL_OF_WATER_ID = 227;
    private final int MOONLIGHT_GRUB_ID = 29078;
    private final int MOONLIGHT_GRUB_PASTE_ID = 29079;
    private final Set<Integer> MOONLIGHT_POTION_IDS = Set.of(29080, 29081, 29082, 29083);
    private final int SUPPLY_CRATE_ID = 51371;
    private final int GRUB_SAPLING_ID = 51365;

    private NaguaLocation selectedLocation;

    public WorldArea getNaguaCombatArea() {
        return (selectedLocation != null) ? selectedLocation.getCombatArea() : null;
    }

    /**
     * Main script loop that runs every 300ms.
     */
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

    /**
     * The "brain" of the script. Determines the current state based on a priority system.
     */
    private void determineState(SulphurNaguaConfig config) {
        int targetPotions = config.moonlightPotionsMinimum() == 0 ? 27 : config.moonlightPotionsMinimum();
        boolean hasPotionsInInventory = countMoonlightPotions() > 0;
        int totalOwnedPotions = countMoonlightPotions() + potionsToPickup;

        // Priority 1: Banking, if pestle is missing.
        if (!Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID)) {
            resetPreparationState();
            currentState = isAtLocation(selectedLocation.getBankArea()) ? SulphurNaguaState.BANKING : SulphurNaguaState.WALKING_TO_BANK;
            return;
        }

        // Priority 2: Pick up potions if they were dropped and crafting is finished.
        if (potionsToPickup > 0 && pickupReady) {
            currentState = SulphurNaguaState.PICKUP;
            return;
        }

        // Priority 3: Stay in combat as long as potions are available.
        boolean inCombatZone = isAtLocation(selectedLocation.getFightAreaCenter());
        if ((currentState == SulphurNaguaState.FIGHTING || currentState == SulphurNaguaState.WALKING_TO_FIGHT || (currentState == SulphurNaguaState.IDLE && inCombatZone)) && hasPotionsInInventory) {
            currentState = inCombatZone ? SulphurNaguaState.FIGHTING : SulphurNaguaState.WALKING_TO_FIGHT;
            return;
        }

        // Priority 4: Prepare more potions if the target is not met or if potions ran out.
        boolean hasIntermediateIngredients = hasIngredientsToProcess();
        if (totalOwnedPotions < targetPotions || hasIntermediateIngredients) {
            if (currentState == SulphurNaguaState.FIGHTING) { // Ran out of potions during combat
                Rs2Prayer.disableAllPrayers();
                Microbot.log("All potions used. Starting preparation for a new batch.");
            }
            currentState = isAtLocation(selectedLocation.getPrepArea()) ? SulphurNaguaState.PREPARATION : SulphurNaguaState.WALKING_TO_PREP;
            return;
        }

        // Default Action: Go to fight if everything else is done.
        currentState = inCombatZone ? SulphurNaguaState.FIGHTING : SulphurNaguaState.WALKING_TO_FIGHT;
    }

    /**
     * Handles gathering supplies and dropping potions if necessary.
     */
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

    /**
     * Processes all available ingredients. Sets pickupReady flag when done.
     */
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

    /**
     * Gathers supplies (vials or grubs) up to the required amount.
     */
    private void getSupplies(int itemID, int requiredAmount) {
        if (Rs2Inventory.count(itemID) >= requiredAmount) return;

        // Vial logic: Repeatedly interact with the crate/dialogue.
        if (itemID == VIAL_OF_WATER_ID) {
            long startTime = System.currentTimeMillis();
            while (Rs2Inventory.count(itemID) < requiredAmount && System.currentTimeMillis() - startTime < 20000) {
                if (Rs2Inventory.isFull()) break;
                if (Rs2Dialogue.hasDialogueOption("Take herblore supplies.")) {
                    Rs2Dialogue.clickOption("Take herblore supplies.");
                } else if (!Rs2Player.isAnimating()) {
                    Rs2GameObject.interact(SUPPLY_CRATE_ID, "Take-from");
                }
                sleep(400, 600);
            }
            // Grub logic: Interact once and stop gathering when the amount is reached.
        } else {
            if (Rs2Player.isAnimating()) return;
            if (Rs2GameObject.interact(GRUB_SAPLING_ID, "Collect-from")) {
                sleepUntil(() -> Rs2Inventory.count(itemID) >= requiredAmount || Rs2Inventory.isFull(), 15000);
                // Actively stop the gathering animation if we have enough
                if (Rs2Player.isAnimating() && Rs2Inventory.count(itemID) >= requiredAmount) {
                    Rs2Walker.walkTo(Rs2Player.getWorldLocation());
                }
            }
        }
    }

    /**
     * Drops a specific number of potions to make inventory space.
     */
    private void dropPotions(int count) {
        if (count <= 0) return;
        if (dropLocation == null) dropLocation = Rs2Player.getWorldLocation();
        this.potionsToPickup = count;
        this.pickupReady = false; // Forbid pickup until crafting is done

        int dropped = 0;
        while (dropped < count) {
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

    /**
     * Picks up potions one by one from the ground.
     */
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
                break; // Exit after interacting with one potion to re-evaluate next tick.
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

    /**
     * Handles all combat actions.
     */
    private void handleFighting(SulphurNaguaConfig config) {
        int basePrayerLevel = client.getRealSkillLevel(Skill.PRAYER);
        int currentHerbloreLevel = client.getBoostedSkillLevel(Skill.HERBLORE);
        int prayerBasedRestore = (int) floor(basePrayerLevel * 0.25) + 7;
        int herbloreBasedRestore = (int) floor(currentHerbloreLevel * 0.3) + 7;
        int dynamicThreshold = max(prayerBasedRestore, herbloreBasedRestore);

        Rs2Player.drinkPrayerPotionAt(dynamicThreshold);
        sleep(600);

        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        if (config.usePiety() && Rs2Prayer.getBestMeleePrayer() != null) {
            Rs2Prayer.toggle(Rs2Prayer.getBestMeleePrayer(), true);
        }

        if (!Rs2Player.isInCombat()) {
            if (getNaguaCombatArea() != null && getNaguaCombatArea().contains(Rs2Player.getWorldLocation())) {
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

    /**
     * Configures anti-ban settings for more human-like behavior.
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
     * Handles all banking interactions.
     */
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
            isBankingInProgress = false;
        }
    }

    private boolean isAtLocation(WorldPoint worldPoint) {
        return Rs2Player.getWorldLocation().distanceTo(worldPoint) < 10;
    }
}