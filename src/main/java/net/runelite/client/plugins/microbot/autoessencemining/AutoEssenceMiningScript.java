package net.runelite.client.plugins.microbot.autoessencemining;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autoessencemining.enums.AutoEssenceMiningState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
public class AutoEssenceMiningScript extends Script {
    private static final WorldPoint AUBURY_LOCATION = new WorldPoint(3253, 3399, 0);
    private static final WorldPoint VARROCK_EAST_BANK_LOCATION = new WorldPoint(3253, 3420, 0);
    private static final int ESSENCE_MINE_REGION = 11595; // Rune essence mine region ID
    private static final String AUBURY_TELEPORT_ACTION = "Teleport";
    private static final long AUBURY_TELEPORT_RETRY_DELAY_MS = 1500;
    private static final long AUBURY_WALK_RETRY_DELAY_MS = 1500;
    private static final long BANK_WALK_RETRY_DELAY_MS = 1500;
    private static final long BUSY_WAIT_LOG_INTERVAL_MS = 5000;
    private static final long STATE_EVALUATION_LOG_INTERVAL_MS = 5000;
    private static final int AUBURY_INTERACTION_DISTANCE = 14;
    private static final int AUBURY_STEP_DISTANCE = 12;
    private static final int BANK_INTERACTION_DISTANCE = 10;
    private static final int BANK_STEP_DISTANCE = 12;
    private static final int PICKAXE_INVENTORY_SLOT = 0;
    private static final int[] RUNECRAFTING_POUCH_IDS = {
            ItemID.RCU_POUCH_SMALL,
            ItemID.RCU_POUCH_MEDIUM,
            ItemID.RCU_POUCH_MEDIUM_DEGRADE,
            ItemID.RCU_POUCH_LARGE,
            ItemID.RCU_POUCH_LARGE_DEGRADE,
            ItemID.RCU_POUCH_GIANT,
            ItemID.RCU_POUCH_GIANT_DEGRADE,
            ItemID.RCU_POUCH_COLOSSAL,
            ItemID.RCU_POUCH_COLOSSAL_DEGRADE,
            ItemID.DEVIOUS_GLOWINGPOUCH_COLOSSAL
    };
    private static final String[] RUNECRAFTING_POUCH_NAMES = {
            "Small pouch",
            "Medium pouch",
            "Large pouch",
            "Giant pouch",
            "Colossal pouch"
    };
    private static final int[] ESSENCE_MINE_EXIT_PORTAL_IDS = {
            NpcID.ESSENCEMINE_PORTAL_1,
            NpcID.ESSENCEMINE_PORTAL_2
    };
    private static final int[] ESSENCE_MINE_EXIT_PORTAL_OBJECT_IDS = {
            ObjectID1.BLANKRUNESTONE_EXIT_PORTAL_2,
            ObjectID1.ESSENCEMINE_PORTAL_1,
            ObjectID1.ESSENCEMINE_PORTAL_2,
            ObjectID1.ESSENCEMINE_PORTAL_3,
            ObjectID1.ESSENCEMINE_PORTAL_4,
            ObjectID1.ESSENCEMINE_PORTAL_5,
            ObjectID1.BLANKRUNESTONE_EXIT_PORTAL
    };
    
    private AutoEssenceMiningState state = AutoEssenceMiningState.WALKING_TO_AUBURY;
    private boolean hasTeleportedWithAubury = false;
    private boolean isInEssenceMine = false;
    private boolean needsToBank = false;
    private long lastAuburyTeleportAttempt = 0;
    private long lastAuburyWalkAttempt = 0;
    private long lastBankWalkAttempt = 0;
    private long lastStateEvaluationLog = 0;
    private long lastBusyWaitLog = 0;
    private long lastAntibanCooldownLog = 0;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private enum PickaxeTool {
        BRONZE(AutoEssenceMiningConfig.PickaxeOverride.BRONZE, ItemID.BRONZE_PICKAXE, "Bronze pickaxe", 1, 1, 10),
        IRON(AutoEssenceMiningConfig.PickaxeOverride.IRON, ItemID.IRON_PICKAXE, "Iron pickaxe", 1, 1, 20),
        STEEL(AutoEssenceMiningConfig.PickaxeOverride.STEEL, ItemID.STEEL_PICKAXE, "Steel pickaxe", 6, 5, 30),
        BLACK(AutoEssenceMiningConfig.PickaxeOverride.BLACK, ItemID.BLACK_PICKAXE, "Black pickaxe", 11, 10, 40),
        MITHRIL(AutoEssenceMiningConfig.PickaxeOverride.MITHRIL, ItemID.MITHRIL_PICKAXE, "Mithril pickaxe", 21, 20, 50),
        ADAMANT(AutoEssenceMiningConfig.PickaxeOverride.ADAMANT, ItemID.ADAMANT_PICKAXE, "Adamant pickaxe", 31, 30, 60),
        RUNE(AutoEssenceMiningConfig.PickaxeOverride.RUNE, ItemID.RUNE_PICKAXE, "Rune pickaxe", 41, 40, 70),
        GILDED(AutoEssenceMiningConfig.PickaxeOverride.GILDED, ItemID.TRAIL_GILDED_PICKAXE, "Gilded pickaxe", 41, 40, 71),
        DRAGON(AutoEssenceMiningConfig.PickaxeOverride.DRAGON, ItemID.DRAGON_PICKAXE, "Dragon pickaxe", 61, 60, 80),
        DRAGON_OR(AutoEssenceMiningConfig.PickaxeOverride.DRAGON_OR, ItemID.DRAGON_PICKAXE_PRETTY, "Dragon pickaxe (or)", 61, 60, 81),
        INFERNAL_EMPTY(AutoEssenceMiningConfig.PickaxeOverride.INFERNAL_EMPTY, ItemID.INFERNAL_PICKAXE_EMPTY, "Infernal pickaxe (uncharged)", 61, 60, 82),
        INFERNAL(AutoEssenceMiningConfig.PickaxeOverride.INFERNAL, ItemID.INFERNAL_PICKAXE, "Infernal pickaxe", 61, 60, 83),
        THIRD_AGE(AutoEssenceMiningConfig.PickaxeOverride.THIRD_AGE, ItemID._3A_PICKAXE, "3rd age pickaxe", 61, 65, 84),
        TRAILBLAZER(AutoEssenceMiningConfig.PickaxeOverride.TRAILBLAZER, ItemID.TRAILBLAZER_PICKAXE, "Trailblazer pickaxe", 61, 60, 85),
        CRYSTAL(AutoEssenceMiningConfig.PickaxeOverride.CRYSTAL, ItemID.CRYSTAL_PICKAXE, "Crystal pickaxe", 71, 70, 90);

        private final AutoEssenceMiningConfig.PickaxeOverride override;
        private final int id;
        private final String itemName;
        private final int miningLevel;
        private final int attackLevel;
        private final int priority;

        PickaxeTool(AutoEssenceMiningConfig.PickaxeOverride override, int id, String itemName, int miningLevel, int attackLevel, int priority) {
            this.override = override;
            this.id = id;
            this.itemName = itemName;
            this.miningLevel = miningLevel;
            this.attackLevel = attackLevel;
            this.priority = priority;
        }
    }

    public boolean run(AutoEssenceMiningConfig config) {
        log.info("Starting essence mining script");
        cancelScheduledTasks();
        stopRequested.set(false);
        resetState();
        initialPlayerLocation = Rs2Player.getWorldLocation();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (shouldStop()) {
                    return;
                }

                if (!super.run()) {
                    log.info("Super.run() returned false, stopping");
                    return;
                }
                if (shouldStop()) {
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    log.info("Not logged in, waiting");
                    return;
                }
                if (Rs2AntibanSettings.actionCooldownActive) {
                    logAntibanCooldownWait();
                    return;
                }

                // track loop performance
                long startTime = System.currentTimeMillis();
                
                // determine current location and state
                isInEssenceMine = isInEssenceMine();
                needsToBank = needsBankingBeforeTrip(config);

                if (shouldStop()) {
                    return;
                }

                if (!isInEssenceMine && !needsToBank && attemptAuburyTeleportIfVisible()) {
                    return;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                    logBusyWait();
                    return;
                }
                
                logStateEvaluation();

                if (isInEssenceMine) {
                    if (!needsToBank) {
                        log.info("In mine with space, switching to MINING_ESSENCE");
                        hasTeleportedWithAubury = true;
                        changeState(AutoEssenceMiningState.MINING_ESSENCE);
                    } else {
                        log.info("In mine but inventory full, switching to USING_PORTAL");
                        changeState(AutoEssenceMiningState.USING_PORTAL);
                    }
                } else {
                    if (needsToBank) {
                        log.info("Need to bank, switching to BANKING");
                        changeState(AutoEssenceMiningState.BANKING);
                    } else {
                        if (Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION) <= 8) {
                            log.info("Near Aubury, switching to TELEPORTING_WITH_AUBURY");
                            if (hasTeleportedWithAubury) {
                                hasTeleportedWithAubury = false;
                            }
                            changeState(AutoEssenceMiningState.TELEPORTING_WITH_AUBURY);
                        } else {
                            log.info("Far from Aubury, switching to WALKING_TO_AUBURY");
                            changeState(AutoEssenceMiningState.WALKING_TO_AUBURY);
                        }
                    }
                }

                switch (state) {
                    case WALKING_TO_AUBURY:
                        handleWalkingToAubury();
                        break;
                    case TELEPORTING_WITH_AUBURY:
                        handleTeleportingWithAubury();
                        break;
                    case MINING_ESSENCE:
                        handleMiningEssence();
                        break;
                    case USING_PORTAL:
                        handleUsingPortal();
                        break;
                    case BANKING:
                        handleBanking(config);
                        break;
                }
                
                // track loop performance
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                log.info("Total time for loop: {}ms", totalTime);
                
            } catch (Exception ex) {
                if (isClientThreadInterrupted(ex)) {
                    log.info("Essence mining loop interrupted during shutdown");
                    return;
                }

                log.error("Error in main essence mining loop: {}", ex.getMessage(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleWalkingToAubury() {
        if (shouldStop()) {
            return;
        }

        log.info("State: WALKING_TO_AUBURY");
        Microbot.status = "Walking to Aubury";

        if (attemptAuburyTeleportIfVisible()) {
            return;
        }
        
        // validate current distance before walking
        int currentDistance = Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION);
        log.info("Current distance to Aubury: {} tiles", currentDistance);
        
        if (currentDistance <= AUBURY_INTERACTION_DISTANCE) {
            log.info("Already near Aubury, no need to walk");
            return;
        }
        
        if (walkTowardAuburyNonBlocking()) {
            log.info("Issued short walk step toward Aubury");
        } else {
            log.info("Failed short Aubury step, will retry next loop");
        }
    }

    private void handleTeleportingWithAubury() {
        if (shouldStop()) {
            return;
        }

        log.info("State: TELEPORTING_WITH_AUBURY");
        Microbot.status = "Teleporting with Aubury";

        // find Aubury NPC
        Rs2NpcModel aubury = Microbot.getRs2NpcCache().query().withName("Aubury").nearestOnClientThread();
        if (aubury != null) {
            teleportWithAubury(aubury);
        } else {
            log.info("Aubury NPC not found nearby");
        }
    }

    private void handleMiningEssence() {
        if (shouldStop()) {
            return;
        }

        log.info("State: MINING_ESSENCE");
        Microbot.status = "Mining essence";
        
        // validate we're in the essence mine
        if (Rs2Player.getWorldLocation().getRegionID() != ESSENCE_MINE_REGION) {
            log.info("Not in essence mine, current region: {}", Rs2Player.getWorldLocation().getRegionID());
            return;
        }
        
        // check if inventory is full before mining
        if (Rs2Inventory.isFull()) {
            log.info("Inventory full, cannot mine more essence");
            return;
        }
        
        // find essence rock to mine
        var essenceRock = Microbot.getRs2TileObjectCache().query().withName("Rune Essence").nearestOnClientThread();

        if (essenceRock != null) {
            log.info("Found rune essence rock, attempting to mine");
            if (essenceRock.click("Mine")) {
                log.info("Started mining essence, waiting for XP drop");
                boolean xpGained = Rs2Player.waitForXpDrop(Skill.MINING, true);
                if (xpGained) {
                    log.info("Gained mining XP from essence");
                } else {
                    log.info("No mining XP gained within timeout");
                }
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            } else {
                log.info("Failed to interact with essence rock");
            }
        } else {
            log.info("No rune essence rocks found nearby");
            log.info("Current region: {}, expected: {}", Rs2Player.getWorldLocation().getRegionID(), ESSENCE_MINE_REGION);
        }
    }

    private void handleUsingPortal() {
        if (shouldStop()) {
            return;
        }

        log.info("State: USING_PORTAL");
        Microbot.status = "Using portal to exit";
        
        // validate we're in the essence mine before looking for portal
        if (Rs2Player.getWorldLocation().getRegionID() != ESSENCE_MINE_REGION) {
            log.info("Not in essence mine, cannot use portal");
            return;
        }

        if (interactWithPortalObject(null) || interactWithPortalObject("Use") || interactWithPortalObject("Exit")) {
            return;
        }

        // Some clients expose these as NPCs; keep this as a fallback after object lookup.
        Rs2NpcModel portal = Microbot.getRs2NpcCache().query().withIds(ESSENCE_MINE_EXIT_PORTAL_IDS).nearestOnClientThread();
        if (portal == null) {
            portal = Microbot.getRs2NpcCache().query().withName("Portal").nearestOnClientThread();
        }

        if (portal != null) {
            log.info("Found portal, attempting to use it");
            if (portal.click() && sleepUntil(() -> !isInEssenceMine(), 5000)) {
                log.info("Successfully used portal to exit essence mine");
                hasTeleportedWithAubury = false;
            } else {
                log.info("Failed to interact with portal");
            }
        } else {
            log.info("Portal not found in essence mine");
        }
    }

    private boolean interactWithPortalObject(String action) {
        String actionDescription = action == null || action.isBlank() ? "default" : action;
        log.info("Attempting portal object interaction with {} action", actionDescription);

        if (Rs2GameObject.interact(ESSENCE_MINE_EXIT_PORTAL_OBJECT_IDS, action) && sleepUntil(() -> !isInEssenceMine(), 5000)) {
            log.info("Successfully used portal object with {} action", actionDescription);
            hasTeleportedWithAubury = false;
            return true;
        }

        return false;
    }


    private void handleBanking(AutoEssenceMiningConfig config) {
        if (shouldStop()) {
            return;
        }

        log.info("State: BANKING");
        Microbot.status = "Banking at Varrock East";
        
        int distanceToBank = Rs2Player.getWorldLocation().distanceTo(VARROCK_EAST_BANK_LOCATION);
        
        log.info("=== Banking State Check ===");
        log.info("Distance to bank: {} tiles", distanceToBank);
        log.info("Bank open: {}", Rs2Bank.isOpen());
        log.info("Inventory full: {}", Rs2Inventory.isFull());
        
        // walk to bank if too far
        if (!Rs2Bank.isOpen()) {
            if (distanceToBank > BANK_INTERACTION_DISTANCE) {
                log.info("Too far from bank, walking there");
                walkTowardBankNonBlocking();
                return;
            }
            
            // attempt to open bank
            log.info("Near bank, attempting to open");
            if (!Rs2Bank.openBank()) {
                log.info("Failed to open bank");
                walkTowardBankNonBlocking();
                return;
            }
        }

        // perform banking operations
        if (Rs2Bank.isOpen()) {
            if (prepareInventoryForMining(config)) {
                hasTeleportedWithAubury = false;
            }
        }
    }

    private boolean prepareInventoryForMining(AutoEssenceMiningConfig config) {
        if (shouldStop()) {
            return false;
        }

        log.info("Bank is open, preparing mining inventory");

        Rs2Bank.depositAllExcept(this::isAllowedDuringInitialBankCleanup);
        sleepUntil(this::inventoryOnlyHasInitialBankAllowedItems, 3000);

        Rs2Bank.depositAll(this::isEssenceItem);
        sleepUntil(() -> Rs2Inventory.items().noneMatch(this::isEssenceItem), 3000);

        Optional<PickaxeTool> selectedPickaxe = selectPickaxe(config);
        if (selectedPickaxe.isEmpty()) {
            Microbot.showMessage("No usable pickaxe found for Auto Essence Mining.");
            log.info("No usable pickaxe found in bank, inventory, or equipment");
            return false;
        }

        PickaxeTool pickaxe = selectedPickaxe.get();
        log.info("Selected pickaxe: {}", pickaxe.itemName);

        if (!ensurePickaxeAvailable(pickaxe)) {
            Microbot.showMessage("Could not withdraw selected pickaxe: " + pickaxe.itemName);
            log.info("Failed to make selected pickaxe available: {}", pickaxe.itemName);
            return false;
        }

        cleanInventoryBeforeMining(pickaxe);

        if (canWieldPickaxe(pickaxe)) {
            if (!equipPickaxe(pickaxe)) {
                Microbot.showMessage("Could not equip selected pickaxe: " + pickaxe.itemName);
                log.info("Failed to equip pickaxe: {}", pickaxe.itemName);
                return false;
            }
            cleanInventoryBeforeMining(pickaxe);
        }

        if (!finalInventoryReady(pickaxe)) {
            log.info("Final inventory check failed after bank prep");
            return false;
        }

        log.info("Closing bank after inventory prep");
        if (!Rs2Bank.closeBank()) {
            log.info("Failed to close bank after inventory prep");
            return false;
        }

        if (!canWieldPickaxe(pickaxe)) {
            moveInventoryPickaxeToSlotOne(pickaxe);
        }

        return true;
    }

    private Optional<PickaxeTool> selectPickaxe(AutoEssenceMiningConfig config) {
        AutoEssenceMiningConfig.PickaxeOverride override = config.manualPickaxeOverride();
        if (override != AutoEssenceMiningConfig.PickaxeOverride.AUTO) {
            return Arrays.stream(PickaxeTool.values())
                    .filter(pickaxe -> pickaxe.override == override)
                    .filter(this::canUsePickaxe)
                    .filter(this::hasPickaxeAvailable)
                    .findFirst();
        }

        return Arrays.stream(PickaxeTool.values())
                .filter(this::canUsePickaxe)
                .filter(this::hasPickaxeAvailable)
                .max(Comparator.comparingInt(pickaxe -> pickaxe.priority));
    }

    private boolean ensurePickaxeAvailable(PickaxeTool pickaxe) {
        if (Rs2Equipment.isWearing(pickaxe.id) || Rs2Inventory.hasItem(pickaxe.id)) {
            return true;
        }

        if (!Rs2Bank.hasItem(pickaxe.id)) {
            return false;
        }

        Rs2Bank.withdrawItem(pickaxe.id);
        return sleepUntil(() -> Rs2Inventory.hasItem(pickaxe.id), 3000);
    }

    private boolean equipPickaxe(PickaxeTool pickaxe) {
        if (Rs2Equipment.isWearing(pickaxe.id)) {
            return true;
        }

        if (!Rs2Inventory.hasItem(pickaxe.id) && !ensurePickaxeAvailable(pickaxe)) {
            return false;
        }

        Rs2Inventory.wield(pickaxe.id);
        return sleepUntil(() -> Rs2Equipment.isWearing(pickaxe.id), 3000);
    }

    private void cleanInventoryBeforeMining(PickaxeTool selectedPickaxe) {
        Rs2Bank.depositAllExcept(this::isAllowedDuringInitialBankCleanup);
        sleepUntil(this::inventoryOnlyHasInitialBankAllowedItems, 3000);
        Rs2Bank.depositAll(this::isEssenceItem);
        sleepUntil(() -> Rs2Inventory.items().noneMatch(this::isEssenceItem), 3000);
        depositUnselectedInventoryPickaxes(selectedPickaxe);
    }

    private void depositUnselectedInventoryPickaxes(PickaxeTool selectedPickaxe) {
        Rs2Bank.depositAll(item -> isPickaxeItem(item) && item.getId() != selectedPickaxe.id);
        sleepUntil(() -> Rs2Inventory.items().noneMatch(item -> isPickaxeItem(item) && item.getId() != selectedPickaxe.id), 3000);
    }

    private void moveInventoryPickaxeToSlotOne(PickaxeTool pickaxe) {
        Rs2ItemModel inventoryPickaxe = Rs2Inventory.get(pickaxe.id);
        if (inventoryPickaxe == null || inventoryPickaxe.getSlot() == PICKAXE_INVENTORY_SLOT) {
            return;
        }

        log.info("Moving {} to inventory slot 1", pickaxe.itemName);
        Rs2Inventory.moveItemToSlot(inventoryPickaxe, PICKAXE_INVENTORY_SLOT);
        sleepUntil(() -> {
            Rs2ItemModel movedPickaxe = Rs2Inventory.get(pickaxe.id);
            return movedPickaxe != null && movedPickaxe.getSlot() == PICKAXE_INVENTORY_SLOT;
        }, 3000);
    }

    private boolean finalInventoryReady(PickaxeTool pickaxe) {
        return Rs2Inventory.items().allMatch(item -> isRunecraftingPouch(item)
                || (!canWieldPickaxe(pickaxe) && item.getId() == pickaxe.id));
    }

    private boolean needsBankingBeforeTrip(AutoEssenceMiningConfig config) {
        if (Rs2Inventory.isFull()) {
            return true;
        }

        if (isInEssenceMine) {
            return false;
        }

        if (inventoryHasItemsToBankBeforeTrip()) {
            return true;
        }

        return !hasUsablePickaxeOnPlayer(config);
    }

    private boolean inventoryHasItemsToBankBeforeTrip() {
        return Rs2Inventory.items().anyMatch(item -> isEssenceItem(item)
                || (!isRunecraftingPouch(item) && !isPickaxeItem(item)));
    }

    private boolean hasUsablePickaxeOnPlayer(AutoEssenceMiningConfig config) {
        AutoEssenceMiningConfig.PickaxeOverride override = config.manualPickaxeOverride();
        if (override != AutoEssenceMiningConfig.PickaxeOverride.AUTO) {
            return Arrays.stream(PickaxeTool.values())
                    .filter(pickaxe -> pickaxe.override == override)
                    .anyMatch(pickaxe -> canUsePickaxe(pickaxe)
                            && (Rs2Equipment.isWearing(pickaxe.id) || Rs2Inventory.hasItem(pickaxe.id)));
        }

        return Arrays.stream(PickaxeTool.values())
                .anyMatch(pickaxe -> canUsePickaxe(pickaxe)
                        && (Rs2Equipment.isWearing(pickaxe.id) || Rs2Inventory.hasItem(pickaxe.id)));
    }

    private boolean inventoryOnlyHasInitialBankAllowedItems() {
        return Rs2Inventory.items().allMatch(this::isAllowedDuringInitialBankCleanup);
    }

    private boolean isAllowedDuringInitialBankCleanup(Rs2ItemModel item) {
        return isEssenceItem(item) || isRunecraftingPouch(item) || isPickaxeItem(item);
    }

    private boolean isEssenceItem(Rs2ItemModel item) {
        if (item == null || item.getName() == null) {
            return false;
        }

        String name = item.getName().toLowerCase();
        return name.equals("rune essence") || name.equals("pure essence");
    }

    private boolean isRunecraftingPouch(Rs2ItemModel item) {
        if (item == null || item.getName() == null) {
            return false;
        }

        return Arrays.stream(RUNECRAFTING_POUCH_IDS).anyMatch(pouchId -> pouchId == item.getId())
                || Arrays.stream(RUNECRAFTING_POUCH_NAMES)
                .anyMatch(pouchName -> pouchName.equalsIgnoreCase(item.getName()));
    }

    private boolean isPickaxeItem(Rs2ItemModel item) {
        if (item == null || item.getName() == null) {
            return false;
        }

        return Arrays.stream(PickaxeTool.values()).anyMatch(pickaxe -> pickaxe.id == item.getId())
                || item.getName().toLowerCase().contains("pickaxe");
    }

    private boolean hasPickaxeAvailable(PickaxeTool pickaxe) {
        return Rs2Equipment.isWearing(pickaxe.id)
                || Rs2Inventory.hasItem(pickaxe.id)
                || Rs2Bank.hasItem(pickaxe.id);
    }

    private boolean canUsePickaxe(PickaxeTool pickaxe) {
        return Rs2Player.getSkillRequirement(Skill.MINING, pickaxe.miningLevel);
    }

    private boolean canWieldPickaxe(PickaxeTool pickaxe) {
        return Rs2Player.getSkillRequirement(Skill.ATTACK, pickaxe.attackLevel);
    }

    // helper method to change state with timeout reset
    private void changeState(AutoEssenceMiningState newState) {
        if (newState != state) {
            log.info("State change: {} -> {}", state, newState);
            state = newState;
        }
    }

    private void resetState() {
        state = AutoEssenceMiningState.WALKING_TO_AUBURY;
        hasTeleportedWithAubury = false;
        isInEssenceMine = false;
        needsToBank = false;
        lastAuburyTeleportAttempt = 0;
        lastAuburyWalkAttempt = 0;
        lastBankWalkAttempt = 0;
        lastStateEvaluationLog = 0;
        lastBusyWaitLog = 0;
        lastAntibanCooldownLog = 0;
    }

    private boolean isClientThreadInterrupted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }

            String message = current.getMessage();
            if (message != null && message.contains("Interrupted waiting for client thread")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isInEssenceMine() {
        return Rs2Player.getWorldLocation().getRegionID() == ESSENCE_MINE_REGION;
    }

    private boolean waitUntilInEssenceMine() {
        return sleepUntil(() -> shouldStop() || isInEssenceMine(), 5000) && !shouldStop();
    }

    private boolean attemptAuburyTeleportIfVisible() {
        if (shouldStop()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastAuburyTeleportAttempt < AUBURY_TELEPORT_RETRY_DELAY_MS) {
            return false;
        }

        Rs2NpcModel aubury = Microbot.getRs2NpcCache().query().withName("Aubury").nearestOnClientThread();
        if (aubury == null) {
            return false;
        }

        lastAuburyTeleportAttempt = now;
        changeState(AutoEssenceMiningState.TELEPORTING_WITH_AUBURY);
        teleportWithAubury(aubury);
        return true;
    }

    private boolean walkTowardAuburyNonBlocking() {
        long now = System.currentTimeMillis();
        if (now - lastAuburyWalkAttempt < AUBURY_WALK_RETRY_DELAY_MS) {
            return true;
        }

        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        WorldPoint nextStep = stepToward(currentLocation, AUBURY_LOCATION, AUBURY_STEP_DISTANCE);
        lastAuburyWalkAttempt = now;

        log.info("Walking toward Aubury with short step: {}", nextStep);
        return !shouldStop() && Rs2Walker.walkMiniMap(nextStep);
    }

    private boolean walkTowardBankNonBlocking() {
        long now = System.currentTimeMillis();
        if (now - lastBankWalkAttempt < BANK_WALK_RETRY_DELAY_MS) {
            return true;
        }

        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        WorldPoint nextStep = stepToward(currentLocation, VARROCK_EAST_BANK_LOCATION, BANK_STEP_DISTANCE);
        lastBankWalkAttempt = now;

        log.info("Walking toward bank with short step: {}", nextStep);
        return !shouldStop() && Rs2Walker.walkMiniMap(nextStep);
    }

    private WorldPoint stepToward(WorldPoint currentLocation, WorldPoint targetLocation, int maxStepDistance) {
        int deltaX = targetLocation.getX() - currentLocation.getX();
        int deltaY = targetLocation.getY() - currentLocation.getY();
        int stepX = Math.min(Math.abs(deltaX), maxStepDistance) * Integer.signum(deltaX);
        int stepY = Math.min(Math.abs(deltaY), maxStepDistance) * Integer.signum(deltaY);

        return new WorldPoint(
                currentLocation.getX() + stepX,
                currentLocation.getY() + stepY,
                currentLocation.getPlane()
        );
    }

    private void logBusyWait() {
        long now = System.currentTimeMillis();
        if (now - lastBusyWaitLog < BUSY_WAIT_LOG_INTERVAL_MS) {
            return;
        }

        lastBusyWaitLog = now;
        log.info("Player is moving or animating, waiting");
    }

    private void logAntibanCooldownWait() {
        long now = System.currentTimeMillis();
        if (now - lastAntibanCooldownLog < BUSY_WAIT_LOG_INTERVAL_MS) {
            return;
        }

        lastAntibanCooldownLog = now;
        log.info("Antiban cooldown active, waiting");
    }

    private void logStateEvaluation() {
        long now = System.currentTimeMillis();
        if (now - lastStateEvaluationLog < STATE_EVALUATION_LOG_INTERVAL_MS) {
            return;
        }

        lastStateEvaluationLog = now;
        log.info("=== State Evaluation ===");
        log.info("In essence mine: {}", isInEssenceMine);
        log.info("Needs banking: {}", needsToBank);
        log.info("Inventory full: {}", Rs2Inventory.isFull());
        log.info("Distance to Aubury: {}", Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION));
    }

    private void teleportWithAubury(Rs2NpcModel aubury) {
        if (shouldStop()) {
            return;
        }

        log.info("Found Aubury, attempting right-click teleport");
        if (aubury.click(AUBURY_TELEPORT_ACTION) && waitUntilInEssenceMine()) {
            log.info("Teleported to essence mine with Aubury");
            hasTeleportedWithAubury = true;
            return;
        }

        log.info("Right-click teleport did not complete, will retry");
    }

    @Override
    public void shutdown() {
        log.info("Shutting down essence mining script");
        stopRequested.set(true);
        cancelScheduledTasks();
        ScriptHeartbeatRegistry.remove(getClass().getName());
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        log.info("Essence mining script shutdown complete");
    }

    private boolean shouldStop() {
        return stopRequested.get() || Thread.currentThread().isInterrupted();
    }

    private void cancelScheduledTasks() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(false);
        }

        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
    }
}
