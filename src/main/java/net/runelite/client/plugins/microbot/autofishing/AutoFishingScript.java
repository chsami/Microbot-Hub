package net.runelite.client.plugins.microbot.autofishing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

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

    public boolean run(AutoFishingConfig config) {
        this.config = config;
        this.selectedFish = config.fishToCatch();
        this.selectedHarpoon = config.harpoonSpec();

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop() {
        try {
            boolean isAnimation = sleepUntil(() -> Rs2Player.getAnimation() != -1, 2_000);
            if (!super.run() || !Microbot.isLoggedIn()) return;
            // we wait until one of the actions is completed
            if (isAnimation || (Rs2Player.isMoving() && currentState != AutoFishingState.TRAVELING)) {
                return;
            }

            currentState = determineState();
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
            if (config.cookFish() && !getRawFishInInventory().isEmpty()) return AutoFishingState.COOKING;
            if (config.useBank()) return AutoFishingState.DEPOSITING;
            return AutoFishingState.DROPPING;
        }

        if (!hasRequiredGear()) {
            return AutoFishingState.GETTING_GEAR;
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
        if (fishingLocation == null) {
            fishingLocation = selectedFish.getClosestLocation(Rs2Player.getWorldLocation());
        }
        if (fishingLocation != null) {
            Rs2Walker.walkTo(fishingLocation);
        }
    }

    private void handleFishing() {
        Rs2NpcModel fishingSpot = findNearestFishingSpot();
        if (fishingSpot == null) {
            sleep(1_000, 2_000); // if by chance the same spotfish disappears, we wait to see if it reappears
            return;
        }
        activateSpec();
        if (fishAction.isEmpty()) {
            fishAction = Rs2Npc.getAvailableAction(fishingSpot, selectedFish.getActions());
        }
        if (!fishAction.isEmpty() && Rs2Npc.interact(fishingSpot, fishAction)) {
            Rs2Player.waitForXpDrop(Skill.FISHING);
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
        }
    }

    private void handleDropping() {
        Rs2Inventory.dropAll(selectedFish.getItemNames().toArray(new String[0]));
    }

    // we process fish that require special handling
    private void handleProcessingFish() {
        if (selectedFish == Fish.SACRED_EEL && Rs2Inventory.hasItem("Knife") && Rs2Inventory.hasItem("Sacred eel")) {
            Rs2Inventory.useItemOnObject(ItemID.KNIFE, ItemID.SNAKEBOSS_EEL);
        } else if (selectedFish == Fish.INFERNAL_EEL && Rs2Inventory.hasItem("Hammer") && Rs2Inventory.hasItem("Infernal eel")) {
            Rs2Inventory.useItemOnObject(ItemID.HAMMER, ItemID.INFERNAL_EEL);
        }
    }

    private void handleCooking() {
        TileObject fireOrRange = getNearbyFireOrRange();
        if (fireOrRange == null) {
            log.error("There is no fire nearby, shutdown");
            shutdown();
            return;
        }

        String fishToCook = getRawFishInInventory().stream().findFirst().orElse(null);

        if (fishToCook != null) {
            Rs2Inventory.useUnNotedItemOnObject(fishToCook, fireOrRange);
            if (sleepUntil(() -> Rs2Widget.findWidget("How many would you like to cook?", null) != null)) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleepUntil(() -> Rs2Player.getAnimation() != -1, 3_000);
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
            Rs2Bank.closeBank();
        }
    }

    private void handleDepositing() {
        if (Rs2Bank.walkToBankAndUseBank()) {
            if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) != 1 ||
            Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) != 0) {
                Rs2Widget.clickWidget(InterfaceID.Bankmain.MENU_BUTTON);
                sleepUntil(()->Rs2Widget.isWidgetVisible(InterfaceID.Bankmain.MENU_CONTAINER), 2000);
                Rs2Widget.clickWidget(InterfaceID.Bankmain.LOCKS);
                sleepUntil(()->Rs2Widget.isWidgetVisible(InterfaceID.BankSideLocks.DONE), 2000);
                if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) != 0) {
                    Rs2Widget.clickWidget(InterfaceID.BankSideLocks.IGNORELOCKS);
                    sleepUntil(() -> Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) == 0, 2000);
                }
                if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) != 1){
                    Rs2Widget.clickWidget(InterfaceID.BankSideLocks.EXTRAOPTIONS);
                    sleepUntil(()->Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) == 1, 2000);
                }
                Rs2Widget.clickWidget(InterfaceID.BankSideLocks.DONE);
                sleepUntil(()->!Rs2Widget.isWidgetVisible(InterfaceID.BankSideLocks.DONE), 2000);
                Rs2Widget.clickWidget(InterfaceID.Bankmain.MENU_BUTTON);
            }

            Set<Integer> itemsLock = new HashSet<>();
            if (Rs2Inventory.hasItem(selectedHarpoon.getName())) {
                itemsLock.add(Rs2Inventory.get(selectedHarpoon.getName()).getSlot());
            }
            for (String item : selectedFish.getMethod().getRequiredItems()) {
                if (Rs2Inventory.hasItem(item)) {
                    itemsLock.add(Rs2Inventory.get(item).getSlot());
                }
            }
            if (Rs2Inventory.hasItem(ItemID.FISH_BARREL)) {
                itemsLock.add(Rs2Inventory.get(ItemID.FISH_BARREL).getSlot());
            } else if (Rs2Inventory.hasItem(ItemID.OPEN_FISH_BARREL)) {
                itemsLock.add(Rs2Inventory.get(ItemID.OPEN_FISH_BARREL).getSlot());
            }
            int[] slotsToLock = itemsLock.stream()
                             .mapToInt(Integer::intValue)
                             .toArray();

            sleepUntil(() -> Rs2Bank.lockAllBySlot(slotsToLock));
            Rs2Bank.emptyFishBarrel();
            Rs2Bank.depositAll();
            sleepUntil(() -> !Rs2Inventory.isFull());
            Rs2Bank.toggleAllLocks();
            Rs2Bank.closeBank();
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
        int[] spotIds = selectedFish.getFishingSpot();
        return Rs2Npc.getNpcs(npc -> Arrays.stream(spotIds).anyMatch(id -> npc.getId() == id))
                .findFirst()
                .orElse(null);
    }

    private List<String> getRawFishInInventory() {
        return selectedFish.getItemNames().stream()
                .filter(name -> name.startsWith("Raw") && Rs2Inventory.hasItem(name))
                .collect(Collectors.toList());
    }
    
    private TileObject getNearbyFireOrRange() {
        Integer[] fireIds = {
            ObjectID.FIRE,
            ObjectID.FORESTRY_FIRE,
            ObjectID.EAGLEPEAK_CAMPFIRE_TIDY,
            ObjectID.FIRE_COOK
        };
        return Rs2GameObject.getGameObject(fireIds, 15);
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

    private boolean isAtFishingLocation() {
        if (fishingLocation == null) return false;
        return Rs2Player.getWorldLocation().distanceTo(fishingLocation) <= 5;
    }

    private void activateSpec() {
        if (selectedHarpoon != HarpoonType.NONE && Rs2Combat.getSpecEnergy() >= 100) {
            Rs2Combat.setSpecState(true, 1000);
            sleepUntil(() -> Rs2Combat.getSpecEnergy() < 100, 2000);
        }
    }

    private boolean withdrawAndEquipItem(String itemName) {
        if (Rs2Bank.hasItem(itemName)) {
            Rs2Bank.withdrawOne(itemName);
            if (sleepUntil(() -> Rs2Inventory.hasItem(itemName))) {
                if (itemName.equalsIgnoreCase("Hammer") || itemName.equalsIgnoreCase("Knife")) {
                    return true;
                }
                Rs2Inventory.wield(itemName);
                return sleepUntil(() -> Rs2Equipment.isWearing(itemName));
            }
        } else {
            log.warn("The object '{}' was not found in the bank", itemName);
        }
        return false;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        this.fishingLocation = null;
        this.fishAction = "";
    }
}