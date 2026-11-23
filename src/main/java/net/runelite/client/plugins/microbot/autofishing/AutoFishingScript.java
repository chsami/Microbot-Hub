package net.runelite.client.plugins.microbot.autofishing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private volatile boolean outOfBait = false;
    static final int MAX_TACKLE_BOX_CASTS_WITHOUT_XP = 3;
    private int tackleBoxCastsWithoutXp = 0;
    private boolean depositPointChosen;
    private BankLocation depositBank;
    private DepositBoxLocation depositBox;
    private long lastAnimTime;
    static final int FIRE_REACH_DISTANCE = 4;
    static final int MAX_FIRE_LOOKUP_MISSES = 5;
    private int fireLookupMisses = 0;
    private boolean noFireThisTrip = false;

    public boolean run(AutoFishingConfig config) {
        this.config = config;
        this.selectedFish = config.fishToCatch();
        this.selectedHarpoon = config.harpoonSpec();
        this.lastActionTime = System.currentTimeMillis();
        this.outOfBait = false;
        this.tackleBoxCastsWithoutXp = 0;
        clearDepositPoint();
        this.lastAnimTime = 0L;
        this.fireLookupMisses = 0;
        this.noFireThisTrip = false;
        this.currentState = AutoFishingState.IDLE; //reset for startup banking check

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop() {
        try {
            if (!super.run() || !Microbot.isLoggedIn()) return;

            // The level-up pop-up blocks game input until it is dismissed. Without this the
            // script keeps its FISHING state and never clicks another spot after a level.
            if (dismissLevelUpScreen()) return;

            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation == null) return;

            currentState = determineState(playerLocation);
            if (currentState != AutoFishingState.DEPOSITING) {
                clearDepositPoint();
            }

            if (currentState == AutoFishingState.FISHING) {
                String reason = outOfMaterialsReason();
                if (reason != null) {
                    stopWithReason(reason);
                    return;
                }
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
                lastAnimTime = System.currentTimeMillis();
                return;
            }

            switch (currentState) {
                case GETTING_GEAR: handleGettingGear(); break;
                case TRAVELING: handleTraveling(playerLocation); break;
                case FISHING: handleFishing(); break;
                case PROCESSING_FISH: handleProcessingFish(); break;
                case COOKING: handleCooking(); break;
                case DEPOSITING: handleDepositing(playerLocation); break;
                case DROPPING: handleDropping(); break;
                case ERROR_RECOVERY: handleErrorRecovery(); break;
            }
        } catch (Exception ex) {
            log.error("An unexpected error occurred", ex);
            currentState = AutoFishingState.ERROR_RECOVERY;
        }
    }

    private AutoFishingState determineState(WorldPoint playerLocation) {
        //this protects against missing items when started near a bank + close spot
        boolean gettingSetUp = currentState == AutoFishingState.IDLE || currentState == AutoFishingState.GETTING_GEAR;
        boolean finishingGearBank = currentState == AutoFishingState.GETTING_GEAR && Rs2Bank.isOpen();
        if (finishingGearBank || (gettingSetUp && !hasRequiredGear())) {
            return AutoFishingState.GETTING_GEAR;
        }

        if (Rs2Inventory.isFull()) {
            if (isSpecialFish(selectedFish)) return AutoFishingState.PROCESSING_FISH;
            if (config.cookFish() && !noFireThisTrip && !getRawFishInInventory().isEmpty() && canCookAtLeastOneRaw()) return AutoFishingState.COOKING;
            if (config.useBank()) return AutoFishingState.DEPOSITING;
            return AutoFishingState.DROPPING;
        }
        noFireThisTrip = false;

        if (Rs2Bank.isOpen() || (!hasRequiredGear() && findNearestFishingSpot() == null)) {
            return AutoFishingState.GETTING_GEAR;
        }

        if (!isAtFishingLocation(playerLocation)) {
            return AutoFishingState.TRAVELING;
        }

        return AutoFishingState.FISHING;
    }

    /**
     * Handle methods
     */
    private void handleTraveling(WorldPoint playerLocation) {
        if (fishingLocation == null) {
            fishingLocation = selectedFish.getClosestLocation(playerLocation);
        }
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

            boolean gainedXp = Rs2Player.waitForXpDrop(Skill.FISHING, true);

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
                    if (Rs2Player.waitForXpDrop(Skill.FISHING, 10000, true)) {
                        gainedXp = true;
                    }
                }
            }

            countTackleBoxCast(gainedXp);
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
            .filter(name -> !isRequiredItem(selectedFish, name))
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
            if (++fireLookupMisses < MAX_FIRE_LOOKUP_MISSES) {
                return;
            }
            fireLookupMisses = 0;
            noFireThisTrip = true;
            log.info("No fire within 15 tiles, {} the raw fish instead of cooking this trip", config.useBank() ? "banking" : "dropping");
            return;
        }
        fireLookupMisses = 0;

        while (true) {
            if (Thread.currentThread().isInterrupted() || !Microbot.isLoggedIn()) {
                return;
            }

            if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                lastAnimTime = System.currentTimeMillis();
                return;
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
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation == null) {
                return;
            }

            if (playerLocation.distanceTo(firePoint) > FIRE_REACH_DISTANCE) {
                Rs2Walker.walkTo(firePoint, FIRE_REACH_DISTANCE);
                return;
            }

            if (Rs2Player.isAnimating()) {
                lastAnimTime = System.currentTimeMillis();
                return;
            }

            if (System.currentTimeMillis() - lastAnimTime < 4000) {
                return;
            }

            lastAnimTime = System.currentTimeMillis();
            Rs2Inventory.useUnNotedItemOnObject(fishToCook, fireOrRange.getId());
            if (sleepUntil(() -> Rs2Widget.findWidget("How many would you like to cook?", null) != null, 3000)) {
                sleep(75, 250);
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleepUntil(() -> Rs2Player.isAnimating(0));

                if (Rs2Inventory.hasItem(fishToCook, true) || Rs2Player.isAnimating(3500)) { //already within 4 tiles, no ned to distance gate
                    return;
                }

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

    //I would make it deposit items first so you dont run out of space
    private void handleGettingGear() {
        Set<String> keepNames = new HashSet<>(); //these can/should be converted to ItemIDs
        if (Rs2Bank.walkToBankAndUseBank()) {
            for (String tool : selectedFish.getMethod().getRequiredItems()) {
                keepNames.addAll(Arrays.asList(acceptedConsumables(tool)));
            }
            if (selectedHarpoon != HarpoonType.NONE) {
                keepNames.add(selectedHarpoon.getName());
            }

            if (Rs2Bank.depositAllExcept(Rs2ItemModel.matches(false, keepNames.toArray(new String[0]))
                .or(item -> extraKeepItems.contains(item.getId())))) {
                Rs2Inventory.waitForInventoryChanges(1800);
            }

            for (String tool : selectedFish.getMethod().getRequiredItems()) {
                keepNames.addAll(Arrays.asList(acceptedConsumables(tool)));
                if (!hasRequiredItem(tool)) {
                    String bankItem = bankedName(tool);
                    if (Rs2Bank.hasItem(bankItem)) {
                        if (Rs2Inventory.isFull()) {
                            stopWithReason("AutoFishing stopped: no free inventory space for " + bankItem + ". Free some space, then start again.");
                            return;
                        }
                        withdrawAndEquipItem(bankItem);
                        log.info("withdrawing " + bankItem);
                        return;
                    }
                }
            }

            if (selectedHarpoon != HarpoonType.NONE) {
                String harpoonName = selectedHarpoon.getName();
                keepNames.add(harpoonName);
                if (!Rs2Inventory.hasItem(harpoonName) && !Rs2Equipment.isWearing(harpoonName) && Rs2Bank.hasItem(harpoonName)) {
                    if (Rs2Inventory.isFull()) {
                        stopWithReason("AutoFishing stopped: no free inventory space for " + harpoonName + ". Free some space, then start again.");
                        return;
                    }
                    withdrawAndEquipItem(harpoonName);
                    return;
                }
            }

            /*
            if (Rs2Bank.depositAllExcept(Rs2ItemModel.matches(false, keepNames.toArray(new String[0]))
                .or(item -> extraKeepItems.contains(item.getId())))) {
                log.info("Attempting deposit of excess gear");
                Rs2Inventory.waitForInventoryChanges(1800);
            }
             */

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
                List<String> missing = selectedFish.getMethod().getRequiredItems().stream()
                    .filter(tool -> !hasRequiredItem(tool))
                    .collect(Collectors.toCollection(ArrayList::new));
                if (selectedHarpoon != HarpoonType.NONE && !Rs2Inventory.hasItem(selectedHarpoon.getName()) && !Rs2Equipment.isWearing(selectedHarpoon.getName())) {
                    missing.add(selectedHarpoon.getName());
                }
                stopWithReason(missingFromBankReason(missing));
            }
        }
    }

    private void handleDepositing(WorldPoint playerLocation) {
        if (openDepositPoint(playerLocation)) {
            boolean usingDepositBox = depositBox != null;
            // Use API: empty barrel (deposits its fish contents directly when bank open),
            // then deposit everything except the protected tools/harpoon/barrel using ids.
            // This replaces the manual side-slot widget config + locking dance entirely.
            if (!usingDepositBox) {
                Rs2Bank.emptyFishBarrel();
                Rs2Inventory.waitForInventoryChanges(1000);
            }

            Set<Integer> keepIds = new HashSet<>();
            if (selectedHarpoon != HarpoonType.NONE && Rs2Inventory.hasItem(selectedHarpoon.getName())) {
                Rs2ItemModel h = Rs2Inventory.get(selectedHarpoon.getName());
                if (h != null) keepIds.add(h.getId());
            }
            for (String tool : selectedFish.getMethod().getRequiredItems()) {
                for (String name : acceptedConsumables(tool)) {
                    if (Rs2Inventory.hasItem(name)) {
                        Rs2ItemModel t = Rs2Inventory.get(name);
                        if (t != null) keepIds.add(t.getId());
                    }
                }
            }
            if (Rs2Inventory.hasItem(ItemID.TACKLE_BOX)) {
                keepIds.add(ItemID.TACKLE_BOX);
            }
            if (Rs2Inventory.hasItem(ItemID.FISH_BARREL_CLOSED)) {
                keepIds.add(ItemID.FISH_BARREL_CLOSED);
            } else if (Rs2Inventory.hasItem(ItemID.FISH_BARREL_OPEN)) {
                keepIds.add(ItemID.FISH_BARREL_OPEN);
            }

            if (Rs2Inventory.hasItem(ItemID.COINS)) {
                keepIds.add(ItemID.COINS);
            }

            if (usingDepositBox) {
                if (!keepIds.isEmpty()) {
                    Rs2DepositBox.depositAllExcept(keepIds.toArray(new Integer[0]));
                } else {
                    Rs2DepositBox.depositAll();
                }
            } else if (!keepIds.isEmpty()) {
                Rs2Bank.depositAllExcept(keepIds.toArray(new Integer[0]));
            } else {
                Rs2Bank.depositAll();
            }

            Rs2Inventory.waitForInventoryChanges(1800);

            if (Rs2Inventory.isFull()) {
                log.error("Inventory still full after deposit");
                return;
            }

            // Human-like timing + antiban after banking action
            if (usingDepositBox) {
                Rs2DepositBox.closeDepositBox();
            } else {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            }
            clearDepositPoint();
            Rs2Inventory.waitForInventoryChanges(1800);
            try {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            } catch (IllegalArgumentException e) {
                log.warn("Antiban banking cooldown skipped due to invalid bounds: {}", e.getMessage());
            }
        }
    }

    private boolean openDepositPoint(WorldPoint playerLocation) {
        if (!depositPointChosen) {
            chooseDepositPoint(playerLocation);
            depositPointChosen = true;
        }
        if (depositBox != null) {
            return Rs2DepositBox.walkToAndUseDepositBox(depositBox) && Rs2DepositBox.isOpen();
        }
        if (depositBank != null) {
            return Rs2Bank.walkToBankAndUseBank(depositBank);
        }
        return Rs2Bank.walkToBankAndUseBank();
    }

    private void chooseDepositPoint(WorldPoint playerLocation) {
        boolean hasFishBarrel = Rs2Inventory.hasItem(ItemID.FISH_BARREL_CLOSED) || Rs2Inventory.hasItem(ItemID.FISH_BARREL_OPEN);
        Set<BankLocation> banks = accessibleBanks();
        Set<DepositBoxLocation> depositBoxes = hasFishBarrel ? Set.of() : accessibleDepositBoxes();
        Map<WorldPoint, Enum<?>> targets = new HashMap<>();
        depositBoxes.forEach(box -> depositBoxTargets(box.getWorldPoint()).forEach(tile -> targets.putIfAbsent(tile, box)));
        banks.forEach(bank -> targets.put(bank.getWorldPoint(), bank));
        if (targets.isEmpty()) return;

        WorldPoint reached = nearestOnFoot(playerLocation, new HashSet<>(targets.keySet()));
        if (reached == null) {
            log.info(NO_DEPOSIT_POINT_ON_FOOT);
            return;
        }
        Enum<?> chosen = targets.get(reached);
        if (chosen instanceof BankLocation) {
            depositBank = (BankLocation) chosen;
        } else if (chosen instanceof DepositBoxLocation) {
            depositBox = (DepositBoxLocation) chosen;
        }
        if (chosen != null) {
            log.info(depositPointMessage(chosen.name(), depositBox != null, hasFishBarrel));
        }
    }

    static final String NO_DEPOSIT_POINT_ON_FOOT = "No bank or deposit box can be reached on foot or by ship from here, so the nearest bank will be used and this may use a teleport. "
            + "If you fish on Karamja, keep at least 60 coins in your inventory for the ship there and back.";

    static String depositPointMessage(String location, boolean isDepositBox, boolean depositBoxesSkipped) {
        String place = Arrays.stream(location.toLowerCase().split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
        String message = "Depositing at the " + place + (isDepositBox ? " deposit box" : " bank")
                + " (the closest one reachable without teleporting, so the walk back to the fishing spot stays short)";
        if (depositBoxesSkipped) {
            message += " (deposit boxes skipped: a Fish barrel can only be emptied at a bank)";
        }
        return message;
    }

    Set<BankLocation> accessibleBanks() {
        return Arrays.stream(BankLocation.values()).filter(BankLocation::hasRequirements).collect(Collectors.toSet());
    }

    Set<DepositBoxLocation> accessibleDepositBoxes() {
        return Arrays.stream(DepositBoxLocation.values()).filter(DepositBoxLocation::hasRequirements).collect(Collectors.toSet());
    }

    Set<WorldPoint> depositBoxTargets(WorldPoint box) {
        Set<WorldPoint> tiles = new HashSet<>();
        tiles.add(box);
        for (WorldPoint tile : List.of(box.dx(1), box.dx(-1), box.dy(1), box.dy(-1))) {
            if (isWalkable(tile)) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    boolean isWalkable(WorldPoint tile) {
        return Rs2PathApi.hasWalkableTileWithin(tile, 0);
    }

    WorldPoint nearestOnFoot(WorldPoint from, Set<WorldPoint> targets) {
        Rs2RouteResult route = Rs2PathApi.plan(Rs2RouteRequest.toAny(from, targets).withoutTeleports());
        return route == null ? null : route.getReachedTarget(2).orElse(null);
    }

    private void clearDepositPoint() {
        depositPointChosen = false;
        depositBank = null;
        depositBox = null;
    }

    private void handleErrorRecovery() {
        if (Rs2DepositBox.isOpen()) Rs2DepositBox.closeDepositBox();
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        fishAction = "";
    }

    /**
     * The level-up screen takes over the chatbox and no longer accepts world interaction,
     * so the script has to click its "Click here to continue" before it can act again.
     *
     * @return true when a level-up screen was showing and the dismiss click was sent
     */
    private boolean dismissLevelUpScreen() {
        if (!Rs2Widget.isWidgetVisible(InterfaceID.LevelupDisplay.CONTINUE)) {
            return false;
        }
        log.debug("Dismissing level-up screen");
        Rs2Widget.clickWidget(InterfaceID.LevelupDisplay.CONTINUE);
        return true;
    }

    /**
     * Helper methods
     */
    Rs2NpcModel findNearestFishingSpot() {
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
            .filter(name -> !isRequiredItem(selectedFish, name))
            .distinct()
            .collect(Collectors.toList());
    }

    static boolean isRequiredItem(Fish fish, String name) {
        return fish.getRequiredItems().stream().anyMatch(req -> req.equalsIgnoreCase(name));
    }

    static boolean isConsumable(String item) {
        String lower = item.toLowerCase();
        return lower.contains("bait") || lower.contains("feather") || lower.contains("sandworm") || lower.contains("karambwanji");
    }

    Rs2TileObjectModel getNearbyFireOrRange() { //this doesnt do ranges or stoves
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }
        int[] fireIds = {
            ObjectID.FIRE,
            ObjectID.FORESTRY_FIRE,
            ObjectID.EAGLEPEAK_CAMPFIRE_TIDY,
            ObjectID.FIRE_COOK,
            ObjectID.TRAIL_WHITE_FIRE,
            ObjectID.TRAIL_PURPLE_FIRE,
            ObjectID.RED_FIRE,
            ObjectID.GREEN_FIRE,
            ObjectID.BLUE_FIRE,
            ObjectID.FORESTRY_FIRE_RED,
            ObjectID.FORESTRY_FIRE_GREEN,
            ObjectID.FORESTRY_FIRE_BLUE,
            ObjectID.FORESTRY_FIRE_WHITE,
            ObjectID.FORESTRY_FIRE_PURPLE
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
            .allMatch(this::hasRequiredItem);

        if (selectedHarpoon != HarpoonType.NONE) {
            return hasTools && (Rs2Inventory.hasItem(selectedHarpoon.getName()) || Rs2Equipment.isWearing(selectedHarpoon.getName()));
        }
        return hasTools;
    }

    private String outOfMaterialsReason() {
        List<String> consumables = selectedFish.getMethod().getRequiredItems().stream()
            .filter(AutoFishingScript::isConsumable)
            .collect(Collectors.toList());
        if (consumables.isEmpty()) return null;

        String needed = joinNames(displayNames(consumables), "and");
        if (outOfBait) {
            if (Rs2Inventory.hasItem(ItemID.TACKLE_BOX) && consumables.stream().anyMatch(AutoFishingScript::isTackleBoxBait)) {
                String refill = config.useBank()
                    ? "Refill the bait, withdraw " + needed + " from your bank, or put it in your inventory"
                    : "Refill the bait or put it in your inventory";
                return "AutoFishing stopped: out of " + needed + " (your Tackle box is empty too). " + refill + ", then start again.";
            }
            return "AutoFishing stopped: out of " + needed + ". Put more in your inventory, then start again.";
        }

        List<String> missing = consumables.stream()
            .filter(item -> !hasRequiredItem(item))
            .collect(Collectors.toList());
        if (missing.isEmpty()) return null;

        List<String> sources = new ArrayList<>();
        for (String item : missing) {
            sources.addAll(displayNames(Arrays.asList(acceptedConsumables(item))));
            if (isTackleBoxBait(item) && !sources.contains("Tackle box")) {
                sources.add("Tackle box");
            }
        }
        return "AutoFishing stopped: no " + joinNames(sources, "or") + " in your inventory for " + selectedFish.getName()
            + ". Add " + joinNames(displayNames(missing), "and") + " and start again.";
    }

    static String missingFromBankReason(List<String> missing) {
        List<String> names = missing.stream()
            .map(item -> joinNames(displayNames(Arrays.asList(acceptedConsumables(item))), "or"))
            .collect(Collectors.toList());
        boolean single = missing.size() == 1 && !isConsumable(missing.get(0));
        return "AutoFishing stopped: could not find " + joinNames(names, "and") + " in your bank. Put " + (single ? "it" : "them")
            + " in your bank or inventory (buy " + (single ? "one" : "some") + " if you have none), then start again.";
    }

    static List<String> displayNames(List<String> items) {
        return items.stream()
            .map(item -> item.equalsIgnoreCase("Feather") ? "Feathers" : item)
            .collect(Collectors.toList());
    }

    private String bankedName(String item) {
        return Arrays.stream(acceptedConsumables(item))
            .filter(name -> Rs2Bank.hasItem(name))
            .findFirst()
            .orElse(item);
    }

    private void countTackleBoxCast(boolean gainedXp) {
        if (gainedXp || !fishesFromTackleBoxOnly()) {
            tackleBoxCastsWithoutXp = 0;
            return;
        }
        tackleBoxCastsWithoutXp++;
        if (tackleBoxCastsWithoutXp >= MAX_TACKLE_BOX_CASTS_WITHOUT_XP) {
            log.info("{} casts in a row gave no Fishing XP with only a Tackle box for bait, treating it as empty", tackleBoxCastsWithoutXp);
            outOfBait = true;
        }
    }

    private boolean fishesFromTackleBoxOnly() {
        return Rs2Inventory.hasItem(ItemID.TACKLE_BOX) && selectedFish.getMethod().getRequiredItems().stream()
            .anyMatch(item -> isConsumable(item) && isTackleBoxBait(item) && !Rs2Inventory.hasItem(acceptedConsumables(item)));
    }

    private boolean hasRequiredItem(String item) {
        if (isConsumable(item)) {
            return Rs2Inventory.hasItem(acceptedConsumables(item)) || (isTackleBoxBait(item) && Rs2Inventory.hasItem(ItemID.TACKLE_BOX));
        }
        return Rs2Inventory.hasItem(item) || Rs2Equipment.isWearing(item);
    }

    static String[] acceptedConsumables(String item) {
        if (item.equalsIgnoreCase("Fishing bait")) {
            return new String[]{"Fishing bait", "Sandworms"};
        }
        return new String[]{item};
    }

    static boolean isTackleBoxBait(String item) {
        return item.equalsIgnoreCase("Fishing bait") || item.equalsIgnoreCase("Sandworms");
    }

    static boolean isOutOfBaitMessage(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return (lower.contains("have any") || lower.contains("have no")) && isConsumable(lower);
    }

    static String joinNames(List<String> names, String conjunction) {
        if (names.isEmpty()) return "your fishing gear";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " " + conjunction + " " + names.get(names.size() - 1);
    }

    public void onChatMessage(ChatMessage event) {
        if ((event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM) && isOutOfBaitMessage(event.getMessage())) {
            outOfBait = true;
        }
    }

    private void stopWithReason(String reason) {
        log.info(reason);
        Rs2Player.logout();
        shutdown();
        Microbot.showMessage(reason);
    }

    private boolean isAtFishingLocation(WorldPoint playerLocation) {
        if (isWithin(playerLocation, fishingLocation, 5)) {
            return true;
        }
        if (findNearestFishingSpot() == null) {
            return false;
        }
        fishingLocation = playerLocation;
        return true;
    }

    static boolean isWithin(WorldPoint playerLocation, WorldPoint target, int distance) {
        return playerLocation != null && target != null && playerLocation.distanceTo(target) <= distance;
    }

    private boolean canCookAtLeastOneRaw() {
        return getRawFishInInventory().stream().anyMatch(this::hasCookingLevelForRaw);
    }

    private boolean hasCookingLevelForRaw(String rawName) {
        if (rawName == null) return false;
        return Rs2Player.getSkillRequirement(Skill.COOKING, getCookingLevelForRaw(rawName));
    }

    private int getCookingLevelForRaw(String rawName) { //this should just be part of the Fish enum
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

    //used to exclude items from deposit while banking
    //would be cleaner to create a list and dynamically add items based on spot/fish
    private final List<Integer> extraKeepItems = List.of(
        ItemID.TACKLE_BOX,
        ItemID.FISH_BARREL_CLOSED,
        ItemID.FISH_BARREL_OPEN,
        ItemID.COINS
    );

    private void activateSpec() {
        if (selectedHarpoon != HarpoonType.NONE && Rs2Combat.getSpecEnergy() >= 100) {
            Rs2Combat.setSpecState(true, 1000);
            sleepUntil(() -> Rs2Combat.getSpecEnergy() < 100, 2000);
        }
    }

    void withdrawAndEquipItem(String itemName) {
        if (Rs2Bank.hasItem(itemName)) {
            if (isConsumable(itemName)) {
                Rs2Bank.withdrawAll(itemName);
                sleepUntil(() -> Rs2Inventory.hasItem(itemName));
                return;
            }
            Rs2Bank.withdrawOne(itemName);
            if (sleepUntil(() -> Rs2Inventory.hasItem(itemName))) {
                Rs2ItemModel item = Rs2Inventory.get(itemName, true);
                if (item != null && item.getAction("wield") != null) { //this could be expanded if we eve rneed to Wear items too
                    if (Rs2Inventory.wield(itemName)) {
                        sleepUntil(() -> Rs2Equipment.isWearing(itemName));
                    }
                }
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
        if (Rs2DepositBox.isOpen()) Rs2DepositBox.closeDepositBox();
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
        clearDepositPoint();
        this.fishingLocation = null;
        this.fishAction = "";
    }
}