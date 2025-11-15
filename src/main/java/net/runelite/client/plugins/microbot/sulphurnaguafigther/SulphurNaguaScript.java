package net.runelite.client.plugins.microbot.sulphurnaguafigther;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SulphurNaguaScript extends Script {

    @Getter
    @RequiredArgsConstructor
    public enum NaguaLocation {
        CIVITAS_ILLA_FORTIS_WEST("West",
                new WorldArea(1344, 9553, 25, 25, 0),
                new WorldPoint(1376, 9712, 0)),

        CIVITAS_ILLA_FORTIS_EAST("East",
                new WorldArea(1371, 9557, 10, 10, 0),
                new WorldPoint(1376, 9712, 0));

        private final String name;
        private final WorldArea combatArea;
        private final WorldPoint prepArea;

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
        FIGHTING,
        LOOTING,
        GETTING_RUNE_CRAFTING_XP
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
    private final int SULPHUR_BLADE_ID = 29084;

    private final int SULPHUROUS_ESSENCE_ID = 29087;
    private final int EYTALLALI_ID = 12870;

    private final WorldPoint EYTALLALI_LOCATION = new WorldPoint(1521, 9577, 0);

    private Set<Integer> dynamicLootIds = new HashSet<>();


    private final int MOONLIGHT_GRUB_ID = 29078;
    private final int MOONLIGHT_GRUB_PASTE_ID = 29079;
    private final Set<Integer> MOONLIGHT_POTION_IDS = Set.of(29080, 29081, 29082, 29083);

    private NaguaLocation selectedLocation;

    public WorldArea getNaguaCombatArea() {
        return (selectedLocation != null) ? selectedLocation.getCombatArea() : null;
    }


    private void updateDynamicLootIds(SulphurNaguaConfig config) {
        dynamicLootIds.clear();

        if (config.lootFireRunes()) dynamicLootIds.add(554);
        if (config.lootChaosRunes()) dynamicLootIds.add(562);
        if (config.lootNatureRunes()) dynamicLootIds.add(561);
        if (config.lootDeathRunes()) dynamicLootIds.add(560);
        if (config.lootIronOre()) dynamicLootIds.add(441);
        if (config.lootCoal()) dynamicLootIds.add(454);
        if (config.lootCopperOre()) dynamicLootIds.add(437);
        if (config.lootTinOre()) dynamicLootIds.add(439);
        if (config.lootMithrilOre()) dynamicLootIds.add(448);
        if (config.lootSilverOre()) dynamicLootIds.add(443);
        if (config.lootSulphurousEssence()) dynamicLootIds.add(29087);
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
                determineState(config);

                switch (currentState) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case WALKING_TO_BANK:
                        Rs2Bank.walkToBank();
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
                    case LOOTING:
                        handleLooting();
                        break;
                    case GETTING_RUNE_CRAFTING_XP:
                        handleGettingRunecraftingXp(config);
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

    private void determineState(SulphurNaguaConfig config) {

        updateDynamicLootIds(config);

        int fixedItems = countFixedItems();
        int maxPossiblePotions = 28 - fixedItems;
        int targetPotions = Math.min(config.moonlightPotionsMinimum(), maxPossiblePotions);

        boolean hasPotionsInInventory = countMoonlightPotions() > 0;
        int totalOwnedPotions = countMoonlightPotions() + potionsToPickup;

        if (!Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID)) {
            resetPreparationState();
            currentState = Rs2Bank.isNearBank(10) ? SulphurNaguaState.BANKING : SulphurNaguaState.WALKING_TO_BANK;
            return;
        }
        boolean inCombatZone = isAtLocation(selectedLocation.getFightAreaCenter());

        if (potionsToPickup > 0 && pickupReady) {
            if (Rs2Inventory.isFull()) {
                Microbot.log("Inventory is full, cannot pick up remaining potions. Starting to fight.");

                resetPreparationState();
                currentState = inCombatZone ? SulphurNaguaState.FIGHTING : SulphurNaguaState.WALKING_TO_FIGHT;
            } else {

                currentState = SulphurNaguaState.PICKUP;
            }
            return;
        }

        boolean isAvailableForAction = !Rs2Player.isInCombat() || Rs2Player.getInteracting() == null || Rs2Player.getInteracting().isDead();

        if (!dynamicLootIds.isEmpty() && isAvailableForAction && !Rs2Inventory.isFull() && isStackableLootNearby()) {
            currentState = SulphurNaguaState.LOOTING;
            return;
        }

        if (config.lootSulphurousBlades() && isAvailableForAction && !Rs2Inventory.isFull() && isSulphurBladeNearby()) {
            currentState = SulphurNaguaState.LOOTING;
            return;
        }


        if ((currentState == SulphurNaguaState.FIGHTING || currentState == SulphurNaguaState.WALKING_TO_FIGHT || (currentState == SulphurNaguaState.IDLE && inCombatZone) || currentState == SulphurNaguaState.LOOTING) && hasPotionsInInventory) {
            currentState = inCombatZone ? SulphurNaguaState.FIGHTING : SulphurNaguaState.WALKING_TO_FIGHT;
            return;
        }

        boolean hasIntermediateIngredients = hasIngredientsToProcess();
        if (totalOwnedPotions < targetPotions || hasIntermediateIngredients) {
            if (currentState == SulphurNaguaState.FIGHTING) {
                Rs2Prayer.disableAllPrayers();
                Microbot.log("All potions used. Starting preparation for a new batch.");
            }

            if (config.changeInSulphurousEssence() && Rs2Inventory.hasItem(SULPHUROUS_ESSENCE_ID) && totalOwnedPotions == 0) {
                Microbot.log("No potions left. Exchanging Sulphurous Essence for XP.");
                currentState = SulphurNaguaState.GETTING_RUNE_CRAFTING_XP;
                return;
            }
            currentState = isAtLocation(selectedLocation.getPrepArea()) ? SulphurNaguaState.PREPARATION : SulphurNaguaState.WALKING_TO_PREP;
            return;
        }

        currentState = inCombatZone ? SulphurNaguaState.FIGHTING : SulphurNaguaState.WALKING_TO_FIGHT;
    }

    private void handlePreparation(SulphurNaguaConfig config) {
        if (hasIngredientsToProcess()) {
            processAllIngredients();
            return;
        }

        int targetPotions = config.moonlightPotionsMinimum();
        int currentPotions = countMoonlightPotions();
        int totalOwnedPotions = currentPotions + potionsToPickup;

        if (totalOwnedPotions >= targetPotions) {
            cleanupLeftoverIngredients();
            if (potionsToPickup > 0) {
                pickupReady = true;
            }
            return;
        }

        int neededPotionsTotal = targetPotions - totalOwnedPotions;
        int freeSlots = Rs2Inventory.emptySlotCount();
        int vialsInInv = Rs2Inventory.count(VIAL_OF_WATER_ID);
        int grubsInInv = Rs2Inventory.count(MOONLIGHT_GRUB_ID);


        if (vialsInInv > 0) {
            int grubsToGet = vialsInInv;

            if (freeSlots >= grubsToGet) {
                getSupplies(MOONLIGHT_GRUB_ID, grubsInInv + grubsToGet);
            } else {
                if (freeSlots > 0) {
                    getSupplies(MOONLIGHT_GRUB_ID, grubsInInv + freeSlots);
                } else {

                    int potionsToDrop = Math.min(grubsToGet, currentPotions);
                    if (potionsToDrop <= 0) {
                        Microbot.log("Stuck: 0 free slots, 0 potions to drop, but need grubs.");
                        return;
                    }
                    Microbot.log("Dropping " + potionsToDrop + " potions to make space for grubs.");
                    dropPotions(potionsToDrop);
                }
            }
            return;
        }


        if (freeSlots > 0) {
            int vialsToGet = Math.min(neededPotionsTotal, freeSlots);
            getSupplies(VIAL_OF_WATER_ID, vialsInInv + vialsToGet);
        } else {
            int potionsToDrop = Math.min(neededPotionsTotal, currentPotions);
            if (potionsToDrop <= 0 && neededPotionsTotal > 0) {
                Microbot.log("Stuck: 0 free slots, 0 vials, 0 potions to drop, but need more potions.");
                return;
            }

            Microbot.log("Dropping " + potionsToDrop + " potions to make space for vials.");
            dropPotions(potionsToDrop);
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
                    Rs2GameObject.interact(SUPPLY_CRATE_ID, "Take herblore supplies");
                }
                sleep(300, 500);
            }
        } else {
            if (Rs2Player.isAnimating()) return;
            int GRUB_SAPLING_ID = 51365;
            if (Rs2GameObject.interact(GRUB_SAPLING_ID, "Collect-from")) {
                sleepUntil(() -> Rs2Inventory.count(itemID) >= requiredAmount || Rs2Inventory.isFull(), 15000);
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
            if (Rs2GroundItem.exists(potionId, 8)) {
                foundPotion = true;
                int potionsBefore = countMoonlightPotions();
                if (Rs2GroundItem.interact(potionId, "Take", 8)) {
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

    private void handleGettingRunecraftingXp(SulphurNaguaConfig config) {
        if (Rs2Dialogue.isInDialogue()) {
            Microbot.log("Handling dialogue...");
            Rs2Dialogue.clickContinue();
            sleepUntil(() -> !Rs2Dialogue.isInDialogue() || !Rs2Inventory.hasItem(SULPHUROUS_ESSENCE_ID), 3000);
            return;
        }

        if (!Rs2Inventory.hasItem(SULPHUROUS_ESSENCE_ID)) {
            Microbot.log("No essence to exchange. Returning to preparation.");
            currentState = isAtLocation(selectedLocation.getPrepArea()) ? SulphurNaguaState.PREPARATION : SulphurNaguaState.WALKING_TO_PREP;
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(EYTALLALI_LOCATION) > 5) {
            Rs2Walker.walkTo(EYTALLALI_LOCATION);
            sleep(400, 800);
            return;
        }

        var eytallali = Rs2Npc.getNpc(EYTALLALI_ID);
        if (eytallali == null) {
            Microbot.log("Waiting for Eytallali to appear...");
            sleep(600, 1000);
            return;
        }

        if (Rs2Player.isAnimating() || Microbot.isGainingExp) {
            Microbot.log("Waiting for action to complete...");
            return;
        }

        if (Rs2Inventory.useItemOnNpc(SULPHUROUS_ESSENCE_ID, eytallali)) {
            Microbot.log("Exchanging essence...");
            sleepUntil(Rs2Dialogue::isInDialogue, 5000);
        }
    }

    private void handleFighting(SulphurNaguaConfig config) {
        int basePrayerLevel = client.getRealSkillLevel(Skill.PRAYER);
        int currentHerbloreLevel = client.getBoostedSkillLevel(Skill.HERBLORE);

        int prayerBasedRestore = (int) Math.floor(basePrayerLevel * 0.25) + 7;
        int herbloreBasedRestore = (int) Math.floor(currentHerbloreLevel * 0.3) + 7;
        int dynamicThreshold = Math.max(prayerBasedRestore, herbloreBasedRestore);

        Rs2Player.drinkPrayerPotionAt(dynamicThreshold);
        sleep(300, 600);

        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);

        if (config.useOffensivePrayers()) {
            var bestMeleePrayer = Rs2Prayer.getBestMeleePrayer();
            if (bestMeleePrayer != null) {
                Rs2Prayer.toggle(bestMeleePrayer, true);
            }
        }

        boolean needsNewTarget = !Rs2Player.isInCombat() || Rs2Player.getInteracting() == null;

        if (needsNewTarget) {
            if (getNaguaCombatArea() != null && getNaguaCombatArea().contains(Rs2Player.getWorldLocation())) {
                var nagua = Rs2Npc.getNpcs("Sulphur Nagua")
                        .filter(n -> !n.isDead())
                        .findFirst()
                        .orElse(null);

                if (nagua != null) {
                    if (Rs2Npc.attack(nagua)) {
                        sleepUntil(Rs2Player::isInCombat, 3000);
                        totalNaguaKills++;
                    }
                }
            } else {
                Microbot.log("Outside combat zone, walking back to center...");
                Rs2Walker.walkTo(selectedLocation.getFightAreaCenter());
                sleep(400, 800);
            }
        }
    }


    private void applyAntiBanSettings() {
        Rs2AntibanSettings.actionCooldownActive = true;
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.takeMicroBreaks = true;
        Rs2AntibanSettings.microBreakDurationLow = 2;
        Rs2AntibanSettings.microBreakDurationHigh = 5;
        Rs2AntibanSettings.actionCooldownChance = 0.05;
        Rs2AntibanSettings.microBreakChance = 0.08;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.08;
        Rs2AntibanSettings.moveMouseOffScreenChance = 0.05;


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


    private boolean isSulphurBladeNearby() {
        return Rs2GroundItem.exists(SULPHUR_BLADE_ID, 8);
    }

    private boolean isStackableLootNearby() {
        for (int itemId : dynamicLootIds) {
            if (Rs2GroundItem.exists(itemId, 8)) {
                return true;
            }
        }
        return false;
    }


    private void handleLooting() {
        Microbot.log("Looting items...");

        if (isSulphurBladeNearby()) {
            int itemsBefore = Rs2Inventory.count(SULPHUR_BLADE_ID);
            if (Rs2GroundItem.interact(SULPHUR_BLADE_ID, "Take", 8)) {
                sleepUntil(() -> Rs2Inventory.count(SULPHUR_BLADE_ID) > itemsBefore, 3000);
            }
            return;
        }


        for (int itemId : dynamicLootIds) {
            if (Rs2GroundItem.exists(itemId, 8)) {
                int itemsBefore = Rs2Inventory.count(itemId);
                if (Rs2GroundItem.interact(itemId, "Take", 8)) {
                    sleepUntil(() -> Rs2Inventory.count(itemId) > itemsBefore, 3000);
                }
                return;
            }
        }
    }

    private int countFixedItems() {
        int fixedItemCount = 0;

        if (Rs2Inventory.hasItem(PESTLE_AND_MORTAR_ID)) {
            fixedItemCount++;
        }

        for (int lootId : dynamicLootIds) {
            if (Rs2Inventory.hasItem(lootId)) {
                fixedItemCount++;
            }
        }

        fixedItemCount += Rs2Inventory.count(SULPHUR_BLADE_ID);

        return fixedItemCount;
    }
}