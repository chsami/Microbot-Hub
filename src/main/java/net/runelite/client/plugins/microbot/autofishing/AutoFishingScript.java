package net.runelite.client.plugins.microbot.autofishing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.coords.WorldPoint;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.awt.event.KeyEvent;

@Slf4j
public class AutoFishingScript extends Script {

    private AutoFishingConfig config;
    private Fish selectedFish;
    @Getter
    private HarpoonType selectedHarpoon;
    @Getter
    private AutoFishingState currentState = AutoFishingState.IDLE;
    private WorldPoint fishingLocation;
    private String fishAction = "";
    private long lastActionTime = System.currentTimeMillis();

    public boolean run(AutoFishingConfig config) {
        this.config = config;
        this.selectedFish = config.fishToCatch();
        this.selectedHarpoon = config.harpoonSpec();
        this.lastActionTime = System.currentTimeMillis();

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop() {
        try {
            if (!super.run() || !Microbot.isLoggedIn()) return;

            currentState = determineState();

            if (currentState == AutoFishingState.FISHING && !hasFishingMaterials()) {
                log.info("Out of required fishing materials for {}. Logging out and shutting down script.", selectedFish);
                Rs2Player.logout();
                shutdown();
                return;
            }

            if (Rs2Player.isMoving() && currentState != AutoFishingState.TRAVELING) {
                return;
            }

            if (currentState == AutoFishingState.FISHING && (Rs2Player.isAnimating() || Rs2Player.isInteracting())) {
                if (System.currentTimeMillis() - lastActionTime < 15000) {
                    return;
                }
                log.info("Idle timeout reached in fishing state, re-evaluating...");
            }

            if (currentState == AutoFishingState.COOKING && (Rs2Player.isAnimating() || Rs2Player.isInteracting())) {
                return;
            }

            switch (currentState) {
                case GETTING_GEAR: handleGettingGear(); break;
                case TRAVELING: handleTraveling(); break;
                case FISHING: handleFishing(); break;
                case PROCESSING_FISH: handleProcessingFish(); break;
                case COOKING: handleCooking(); break;
                case DEPOSITING: handleDepositing(); break;
                case DROPPING: handleDropping(); break;
                case ERROR_RECOVERY: handleErrorRecovery(); break;
            }
        } catch (Exception ex) {
            log.error("An unexpected error occurred", ex);
            currentState = AutoFishingState.ERROR_RECOVERY;
        }
    }

    private AutoFishingState determineState() {
        if (Rs2Inventory.isFull()) {
            if (isSpecialFish(selectedFish)) return AutoFishingState.PROCESSING_FISH;
            if (config.cookFish() && !getRawFishInInventory().isEmpty() && canCookAtLeastOneRaw()) return AutoFishingState.COOKING;
            if (config.useBank()) return AutoFishingState.DEPOSITING;
            return AutoFishingState.DROPPING;
        }

        if (!hasRequiredGear()) {
            if (findNearestFishingSpot() == null) {
                return AutoFishingState.GETTING_GEAR;
            }
        }

        if (!isAtFishingLocation()) {
            return AutoFishingState.TRAVELING;
        }

        return AutoFishingState.FISHING;
    }

    /**
     * Handle methods 
     */
    private void handleTraveling() {
        fishingLocation = selectedFish.getClosestLocation(Rs2Player.getWorldLocation());
        if (fishingLocation != null) {
            Rs2Walker.walkTo(fishingLocation);
        }
    }

    private void handleFishing() {
        if (Thread.currentThread().isInterrupted() || !Microbot.isLoggedIn()) {
            return;
        }
        Rs2NpcModel fishingSpot = findNearestFishingSpot();
        if (fishingSpot == null) {
            return;
        }

        Actor interacting = Rs2Player.getInteracting();
        if (interacting instanceof NPC && ((NPC) interacting).getIndex() == fishingSpot.getNpc().getIndex()) {
            if (System.currentTimeMillis() - lastActionTime < 15000) {
                return;
            }
        }

        activateSpec();
        if (fishAction.isEmpty()) {
            fishAction = getAvailableAction(fishingSpot, selectedFish.getActions());
        }

        int clickedSpotIndex = (fishingSpot.getNpc() != null) ? fishingSpot.getNpc().getIndex() : -1;

        if (!fishAction.isEmpty() && fishingSpot.click(fishAction)) {
            lastActionTime = System.currentTimeMillis();

            Rs2Player.waitForXpDrop(Skill.FISHING, true);

            // Wait using repeated xp drop waits until the specific spot despawns or inv is full.
            // This gives one click per spot lifetime and catches full inv during the session.
            if (clickedSpotIndex != -1) {
                while (true) {
                    if (Rs2Inventory.isFull()) {
                        break;
                    }
                    Actor current = Rs2Player.getInteracting();
                    if (current == null || !(current instanceof NPC) || ((NPC) current).getIndex() != clickedSpotIndex) {
                        break;
                    }
                    Rs2Player.waitForXpDrop(Skill.FISHING, 10000, true);
                }
            }

            lastActionTime = System.currentTimeMillis();
            try {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            } catch (IllegalArgumentException e) {
                log.warn("Antiban fishing cooldown skipped due to invalid bounds: {}", e.getMessage());
            }
        }
    }

    private void handleDropping() {
        List<String> itemsToDrop = Rs2Inventory.all().stream()
                .map(Rs2ItemModel::getName)
                .filter(name -> name.startsWith("Raw") || 
                                name.contains("Leaping") || 
                                name.toLowerCase().contains("burnt"))
                .distinct()
                .collect(Collectors.toList());
        
        if (!itemsToDrop.isEmpty()) {
            Rs2Inventory.dropAll(itemsToDrop.toArray(new String[0]));
        } else {
            // Fallback to selected fish item names if broad filter finds nothing
            Rs2Inventory.dropAll(selectedFish.getItemNames().toArray(new String[0]));
        }
    }

    // we process fish that require special handling (cut in inventory)
    private void handleProcessingFish() {
        if (selectedFish == Fish.SACRED_EEL && Rs2Inventory.hasItem("Knife") && Rs2Inventory.hasItem("Sacred eel")) {
            Rs2Inventory.combine(ItemID.KNIFE, ItemID.SNAKEBOSS_EEL);
        } else if (selectedFish == Fish.INFERNAL_EEL && Rs2Inventory.hasItem("Hammer") && Rs2Inventory.hasItem("Infernal eel")) {
            Rs2Inventory.combine(ItemID.HAMMER, ItemID.INFERNAL_EEL);
        }
    }

    private void handleCooking() {
        if (Thread.currentThread().isInterrupted() || !Microbot.isLoggedIn()) {
            return;
        }
        Rs2TileObjectModel fireOrRange = getNearbyFireOrRange();
        if (fireOrRange == null) {
            log.error("There is no fire nearby, shutdown");
            shutdown();
            return;
        }

        while (true) {
            if (Thread.currentThread().isInterrupted() || !Microbot.isLoggedIn()) {
                return;
            }
            if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 15000);
            }

            List<String> raws = getRawFishInInventory();
            if (raws.isEmpty()) {
                break;
            }

            String fishToCook = raws.stream()
                    .filter(this::hasCookingLevelForRaw)
                    .findFirst()
                    .orElse(null);
            if (fishToCook == null) {
                break;
            }

            // Ensure we are close enough to the fire before using item on it.
            // If too far, the use may not open the widget, leading to repeated attempts (spam).
            WorldPoint firePoint = fireOrRange.getWorldLocation();
            if (Rs2Player.getWorldLocation().distanceTo(firePoint) > 4) {
                Rs2Walker.walkTo(firePoint);
                sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.getWorldLocation().distanceTo(firePoint) <= 3, 10000);
                sleepUntil(() -> !Rs2Player.isMoving(), 5000);
            }

            Rs2Inventory.useUnNotedItemOnObject(fishToCook, fireOrRange.getId());
            if (sleepUntil(() -> Rs2Widget.findWidget("How many would you like to cook?", null) != null, 3000)) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

                Rs2Player.waitForXpDrop(Skill.COOKING, true);
                sleepUntil(() -> Rs2Player.getAnimation() != AnimationID.IDLE);

                String currentRaw = fishToCook;
                sleepUntilTrue(() ->
                                !Rs2Inventory.hasItem(currentRaw) &&
                                !Rs2Player.isAnimating(3500),
                        500, 150000);

                try {
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                } catch (IllegalArgumentException e) {
                    log.warn("Antiban cooking cooldown skipped due to invalid bounds: {}", e.getMessage());
                }
            } else {
                log.warn("Cooking widget did not appear for {}, delaying to avoid spam clicks.", fishToCook);
                sleep(1500, 3000);
                break;
            }
        }
    }

    private void handleGettingGear() {
        if (Rs2Bank.walkToBankAndUseBank()) {
            for (String tool : selectedFish.getMethod().getRequiredItems()) {
                if (!Rs2Inventory.hasItem(tool) && !Rs2Equipment.isWearing(tool)) {
                    withdrawAndEquipItem(tool);
                }
            }
            if (selectedHarpoon != HarpoonType.NONE) {
                String harpoonName = selectedHarpoon.getName();
                if (!Rs2Inventory.hasItem(harpoonName) && !Rs2Equipment.isWearing(harpoonName)) {
                    withdrawAndEquipItem(harpoonName);
                }
            }
            // Human delay + ensure cache updates before closing to prevent rapid re-open
            sleep(400, 900);
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            Rs2Inventory.waitForInventoryChanges(1800);
            try {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            } catch (IllegalArgumentException e) {
                log.warn("Antiban banking cooldown skipped due to invalid bounds: {}", e.getMessage());
            }

            if (!hasRequiredGear()) {
                log.info("Could not obtain required gear/materials from bank. Logging out and shutting down script.");
                Rs2Player.logout();
                shutdown();
                return;
            }
        }
    }

    private void handleDepositing() {
        if (Rs2Bank.walkToBankAndUseBank()) {
            // Use API: empty barrel (deposits its fish contents directly when bank open),
            // then deposit everything except the protected tools/harpoon/barrel using ids.
            // This replaces the manual side-slot widget config + locking dance entirely.
            Rs2Bank.emptyFishBarrel();
            Rs2Inventory.waitForInventoryChanges(1000);

            Set<Integer> keepIds = new HashSet<>();
            if (selectedHarpoon != HarpoonType.NONE && Rs2Inventory.hasItem(selectedHarpoon.getName())) {
                Rs2ItemModel h = Rs2Inventory.get(selectedHarpoon.getName());
                if (h != null) keepIds.add(h.getId());
            }
            for (String tool : selectedFish.getMethod().getRequiredItems()) {
                if (Rs2Inventory.hasItem(tool)) {
                    Rs2ItemModel t = Rs2Inventory.get(tool);
                    if (t != null) keepIds.add(t.getId());
                }
            }
            if (Rs2Inventory.hasItem(ItemID.FISH_BARREL_CLOSED)) {
                keepIds.add(ItemID.FISH_BARREL_CLOSED);
            } else if (Rs2Inventory.hasItem(ItemID.FISH_BARREL_OPEN)) {
                keepIds.add(ItemID.FISH_BARREL_OPEN);
            }

            if (!keepIds.isEmpty()) {
                Rs2Bank.depositAllExcept(keepIds.toArray(new Integer[0]));
            } else {
                Rs2Bank.depositAll();
            }

            Rs2Inventory.waitForInventoryChanges(1800);
            sleepUntil(() -> !Rs2Inventory.isFull(), 5000);

            // Human-like timing + antiban after banking action
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            Rs2Inventory.waitForInventoryChanges(1800);
            try {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            } catch (IllegalArgumentException e) {
                log.warn("Antiban banking cooldown skipped due to invalid bounds: {}", e.getMessage());
            }
        }
    }

    private void handleErrorRecovery() {
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        fishAction = "";
        fishingLocation = null;
    }

    /**
     * Helper methods
     */
    private Rs2NpcModel findNearestFishingSpot() {
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }
        int[] spotIds = selectedFish.getFishingSpot();
        try {
            return Microbot.getRs2NpcCache().query()
                    .where(npc -> Arrays.stream(spotIds).anyMatch(id -> npc.getId() == id))
                    .nearestOnClientThread();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("interrupted waiting for client thread")) {
                Thread.currentThread().interrupt();
                return null;
            }
            return null;
        }
    }

    private List<String> getRawFishInInventory() {
        return Rs2Inventory.all().stream()
                .map(Rs2ItemModel::getName)
                .filter(name -> name.startsWith("Raw"))
                .distinct()
                .collect(Collectors.toList());
    }
    
    private Rs2TileObjectModel getNearbyFireOrRange() {
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }
        int[] fireIds = {
            ObjectID.FIRE,
            ObjectID.FORESTRY_FIRE,
            ObjectID.EAGLEPEAK_CAMPFIRE_TIDY,
            ObjectID.FIRE_COOK
        };
        try {
            return Microbot.getRs2TileObjectCache().query().withIds(fireIds).within(15).nearestOnClientThread();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("interrupted waiting for client thread")) {
                Thread.currentThread().interrupt();
                return null;
            }
            log.debug("Non-fatal error querying for fire: {}", e.getMessage());
            return null;
        }
    }

    private boolean isSpecialFish(Fish fish) {
        return fish == Fish.SACRED_EEL || fish == Fish.INFERNAL_EEL;
    }

    private boolean hasRequiredGear() {
        boolean hasTools = selectedFish.getMethod().getRequiredItems().stream()
                .allMatch(tool -> Rs2Inventory.hasItem(tool) || Rs2Equipment.isWearing(tool));

        if (selectedHarpoon != HarpoonType.NONE) {
            return hasTools && (Rs2Inventory.hasItem(selectedHarpoon.getName()) || Rs2Equipment.isWearing(selectedHarpoon.getName()));
        }
        return hasTools;
    }

    private boolean hasFishingMaterials() {
        // Consumables that deplete while fishing (baits, feathers, etc.). Tools like rods/nets/pots are separate.
        List<String> required = selectedFish.getMethod().getRequiredItems();
        for (String item : required) {
            String lower = item.toLowerCase();
            if (lower.contains("bait") || lower.contains("feather") || lower.contains("sandworm") || lower.contains("karambwanji")) {
                if (!Rs2Inventory.hasItem(item)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isAtFishingLocation() {
        if (fishingLocation != null) {
            if (Rs2Player.getWorldLocation().distanceTo(fishingLocation) <= 5) {
                return true;
            }
        }
        // Bootstrap: if no target yet or far from it, treat as at location if a spot for this fish is visible.
        return findNearestFishingSpot() != null;
    }

    private boolean canCookAtLeastOneRaw() {
        return getRawFishInInventory().stream().anyMatch(this::hasCookingLevelForRaw);
    }

    private boolean hasCookingLevelForRaw(String rawName) {
        if (rawName == null) return false;
        return Rs2Player.getSkillRequirement(Skill.COOKING, getCookingLevelForRaw(rawName));
    }

    private int getCookingLevelForRaw(String rawName) {
        if (rawName == null) return 99;
        String lower = rawName.toLowerCase();
        if (lower.contains("shrimp") || lower.contains("anchovies") || lower.contains("sardine")) return 1;
        if (lower.contains("herring")) return 5;
        if (lower.contains("mackerel")) return 10;
        if (lower.contains("trout")) return 15;
        if (lower.contains("cod")) return 18;
        if (lower.contains("pike")) return 20;
        if (lower.contains("salmon")) return 25;
        if (lower.contains("tuna") || lower.contains("karambwan")) return 30;
        if (lower.contains("lobster")) return 40;
        if (lower.contains("bass")) return 43;
        if (lower.contains("swordfish")) return 45;
        if (lower.contains("monkfish")) return 62;
        if (lower.contains("shark")) return 80;
        if (lower.contains("anglerfish")) return 84;
        if (lower.contains("dark crab")) return 90;
        if (lower.contains("cave eel")) return 38;
        if (lower.contains("lava eel")) return 53;
        // sacred/infernal are processed, not standard cooked
        return 1;
    }

    private void activateSpec() {
        if (selectedHarpoon != HarpoonType.NONE && Rs2Combat.getSpecEnergy() >= 100) {
            Rs2Combat.setSpecState(true, 1000);
            sleepUntil(() -> Rs2Combat.getSpecEnergy() < 100, 2000);
        }
    }

    private void withdrawAndEquipItem(String itemName) {
        if (Rs2Bank.hasItem(itemName)) {
            Rs2Bank.withdrawOne(itemName);
            if (sleepUntil(() -> Rs2Inventory.hasItem(itemName))) {
                if (itemName.equalsIgnoreCase("Hammer") || itemName.equalsIgnoreCase("Knife")) {
                    return;
                }
                Rs2Inventory.wield(itemName);
                sleepUntil(() -> Rs2Equipment.isWearing(itemName));
            }
        } else {
            log.warn("The object '{}' was not found in the bank", itemName);
        }
    }

    private String getAvailableAction(Rs2NpcModel npcModel, List<String> actions) {
        if (npcModel == null || npcModel.getNpc() == null || actions == null) return "";
        NPCComposition composition = npcModel.getNpc().getTransformedComposition();
        if (composition == null || composition.getActions() == null) return "";
        return Arrays.stream(composition.getActions())
                .filter(action -> action != null && actions.stream().anyMatch(a -> a.equalsIgnoreCase(action)))
                .findFirst()
                .orElse("");
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        this.fishingLocation = null;
        this.fishAction = "";
    }
}
