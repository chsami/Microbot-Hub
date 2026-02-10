package net.runelite.client.plugins.microbot.slayer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.TileObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2Cannon;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.MonsterLocation;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.slayer.profile.SlayerProfileManager;
import net.runelite.client.plugins.microbot.slayer.profile.SlayerTaskProfileJson;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
public class SlayerScript extends Script {

    private SlayerConfig config;
    private WorldPoint taskDestination = null;
    private String taskLocationName = "";
    private boolean initialSetupDone = false;
    private long lastLootTime = 0;
    private static final int ARRIVAL_DISTANCE = 10;
    private static final int LOOT_DELAY_MS = 600;
    private static final int SLAYER_REWARD_POINTS_VARBIT = 4068;
    private static final int SKIP_TASK_COST = 30;
    private static final int MAX_SKIP_ATTEMPTS = 10;
    private int skipAttemptCounter = 0;
    private String taskBeingSkipped = "";

    // Block task state tracking
    private static final int BLOCK_TASK_COST = 100;
    private static final int MAX_BLOCK_ATTEMPTS = 10;
    private int blockAttemptCounter = 0;
    private String taskBeingBlocked = "";

    // POH Constants
    private static final int HOUSE_PORTAL_ID = 4525;
    private static final int[] REJUVENATION_POOL_IDS = {29241, 29240, 29239, 29238, 29237};
    // Portal nexus IDs (Basic, Marble, Gilded)
    private static final int[] PORTAL_NEXUS_IDS = {33366, 33408, 33410};
    // Mounted glory IDs (various tiers)
    private static final int[] MOUNTED_GLORY_IDS = {13523, 13524, 13525, 13526};
    // Mounted ring of wealth
    private static final int MOUNTED_WEALTH_ID = 29156;

    // POH state tracking
    private SlayerState stateAfterPoh = SlayerState.BANKING; // What to do after POH restoration
    private boolean pohTeleportAttempted = false;

    // Spellbook swap retry tracking
    private int spellbookSwapAttempts = 0;
    private static final int MAX_SPELLBOOK_SWAP_ATTEMPTS = 5;

    // Break handler resume tracking
    private boolean wasLoggedOut = false;

    // Cannon state tracking
    private boolean isUsingCannon = false;
    private boolean cannonPlaced = false;
    private boolean cannonFired = false;
    private WorldPoint cannonSpot = null;

    // Crash detection / world hop tracking
    private long crashDetectionStartTime = 0;
    private long lastWorldHopTime = 0;
    private boolean isSearchingForTargets = false;
    private static final int CRASH_HOP_DELAY_MS = 20000; // 20 seconds before hopping
    private static final int WORLD_HOP_COOLDOWN_MS = 60000; // 1 minute cooldown between hops

    // Task completion loot delay
    private long taskCompletedTime = 0;
    private boolean taskCompletedLooting = false;
    private static final int TASK_COMPLETE_LOOT_DELAY_MS = 5000; // 5 seconds to loot after task complete

    // JSON profile tracking
    private SlayerProfileManager profileManager = new SlayerProfileManager();
    private SlayerTaskProfileJson activeJsonProfile = null;

    // All superior slayer monster names (requires "Bigger and Badder" unlock)
    private static final Set<String> SUPERIOR_MONSTERS = new HashSet<>(Arrays.asList(
            // Regular superiors
            "Crushing hand",           // Crawling hand
            "Chasm Crawler",           // Cave crawler
            "Screaming banshee",       // Banshee
            "Screaming twisted banshee", // Twisted banshee
            "Giant rockslug",          // Rockslug
            "Cockathrice",             // Cockatrice
            "Flaming pyrelord",        // Pyrefiend
            "Monstrous basilisk",      // Basilisk
            "Malevolent Mage",         // Infernal Mage
            "Insatiable Bloodveld",    // Bloodveld
            "Insatiable mutated Bloodveld", // Mutated Bloodveld
            "Vitreous Jelly",          // Jelly
            "Vitreous warped Jelly",   // Warped Jelly
            "Cave abomination",        // Cave horror
            "Abhorrent spectre",       // Aberrant spectre
            "Repugnant spectre",       // Deviant spectre
            "Choke devil",             // Dust devil
            "King kurask",             // Kurask
            "Marble gargoyle",         // Gargoyle
            "Nechryarch",              // Nechryael
            "Greater abyssal demon",   // Abyssal demon
            "Night beast",             // Dark beast
            "Nuclear smoke devil",     // Smoke devil
            // Kebos/newer superiors
            "Shadow Wyrm",             // Wyrm
            "Guardian Drake",          // Drake
            "Colossal Hydra",          // Hydra
            "Basilisk Sentinel"        // Basilisk Knight
    ));

    @Inject
    public SlayerScript() {
    }

    public boolean run(SlayerConfig config) {
        this.config = config;
        SlayerPlugin.setState(SlayerState.IDLE);
        initialSetupDone = false;

        // Load NPC location data required for Rs2Slayer.getSlayerTaskLocation()
        try {
            Rs2NpcManager.loadJson();
            log.info("NPC location data loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load NPC location data: ", e);
        }

        // Load JSON profiles from file
        profileManager.loadProfiles();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    wasLoggedOut = true;
                    return;
                }
                if (!super.run()) return;
                if (!config.enablePlugin()) return;

                // Handle resume after break/logout
                if (wasLoggedOut) {
                    wasLoggedOut = false;
                    SlayerState currentState = SlayerPlugin.getState();
                    // If we were in a transient state, reset to re-evaluate
                    if (currentState != SlayerState.IDLE &&
                        currentState != SlayerState.BANKING) {
                        log.info("Resuming after logout - resetting to DETECTING_TASK to re-evaluate");
                        SlayerPlugin.setState(SlayerState.DETECTING_TASK);
                    }
                }

                // Read slayer points
                int slayerPoints = Microbot.getVarbitValue(SLAYER_REWARD_POINTS_VARBIT);
                SlayerPlugin.setSlayerPoints(slayerPoints);

                // Detect task
                boolean hasTask = Rs2Slayer.hasSlayerTask();
                String taskName = "";
                int remaining = 0;

                if (hasTask) {
                    taskName = Rs2Slayer.getSlayerTask();
                    remaining = Rs2Slayer.getSlayerTaskSize();
                }

                SlayerPlugin.updateTaskInfo(hasTask, taskName, remaining);

                // State machine
                SlayerState currentState = SlayerPlugin.getState();

                // Update break handler lock state - only allow breaks during safe states
                updateBreakHandlerLock(currentState);

                switch (currentState) {
                    case IDLE:
                        handleIdleState(hasTask, taskName);
                        break;
                    case GETTING_TASK:
                        handleGettingTaskState(hasTask, taskName);
                        break;
                    case SKIPPING_TASK:
                        handleSkippingTaskState();
                        break;
                    case BLOCKING_TASK:
                        handleBlockingTaskState();
                        break;
                    case DETECTING_TASK:
                        handleDetectingTaskState(hasTask);
                        break;
                    case RESTORING_AT_POH:
                        handleRestoringAtPohState();
                        break;
                    case BANKING:
                        handleBankingState();
                        break;
                    case SWAPPING_SPELLBOOK:
                        handleSwappingSpellbookState();
                        break;
                    case TRAVELING:
                        handleTravelingState();
                        break;
                    case AT_LOCATION:
                        handleAtLocationState(hasTask, remaining);
                        break;
                    case FIGHTING:
                        handleFightingState(hasTask, remaining);
                        break;
                }

            } catch (Exception ex) {
                log.error("Slayer script error: ", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleIdleState(boolean hasTask, String taskName) {
        if (hasTask) {
            // Check block/skip lists before proceeding with an existing task
            if (shouldBlockTask(taskName)) {
                log.info("Existing task '{}' is on block list, transitioning to BLOCKING_TASK", taskName);
                SlayerPlugin.setState(SlayerState.BLOCKING_TASK);
            } else if (isTaskOnBlockList(taskName)) {
                log.warn("Existing task '{}' is on block list but cannot afford to block, checking skip", taskName);
                if (shouldSkipTask(taskName)) {
                    log.info("Falling back to skipping existing task '{}'", taskName);
                    SlayerPlugin.setState(SlayerState.SKIPPING_TASK);
                } else {
                    log.info("Cannot afford to block/skip task '{}', proceeding with task", taskName);
                    SlayerPlugin.setState(SlayerState.DETECTING_TASK);
                }
            } else if (shouldSkipTask(taskName)) {
                log.info("Existing task '{}' is on skip list, transitioning to SKIPPING_TASK", taskName);
                SlayerPlugin.setState(SlayerState.SKIPPING_TASK);
            } else {
                log.info("Task '{}' detected, transitioning to DETECTING_TASK", taskName);
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
        } else if (config.getNewTask()) {
            log.info("No task, transitioning to GETTING_TASK");
            SlayerPlugin.setState(SlayerState.GETTING_TASK);
        }
    }

    private void handleGettingTaskState(boolean hasTask, String taskName) {
        // Handle dialogue if we're in one
        if (Rs2Dialogue.isInDialogue()) {
            handleSlayerDialogue();
            return;
        }

        // If we now have a task, check if we should block or skip it
        // Block takes priority over skip since it's permanent
        if (hasTask) {
            if (shouldBlockTask(taskName)) {
                // Can afford to block - go block it
                log.info("Task '{}' is on block list, transitioning to BLOCKING_TASK", taskName);
                SlayerPlugin.setState(SlayerState.BLOCKING_TASK);
            } else if (isTaskOnBlockList(taskName)) {
                // Task is on block list but can't afford to block - try to skip instead
                log.warn("Task '{}' is on block list but cannot afford to block, checking skip option", taskName);
                if (shouldSkipTask(taskName)) {
                    log.info("Falling back to skipping task '{}'", taskName);
                    SlayerPlugin.setState(SlayerState.SKIPPING_TASK);
                } else if (isTaskOnSkipList(taskName)) {
                    // Also on skip list but can't afford that either
                    log.error("Task '{}' cannot be blocked or skipped - insufficient points!", taskName);
                    Microbot.showMessage("Cannot block/skip task '" + taskName + "' - insufficient slayer points. Logging out.");
                    stopAndLogout();
                } else {
                    // Can't block, not on skip list - just do the task
                    log.info("Cannot afford to block task '{}', proceeding with task", taskName);
                    SlayerPlugin.setState(SlayerState.DETECTING_TASK);
                }
            } else if (shouldSkipTask(taskName)) {
                log.info("Task '{}' is on skip list, transitioning to SKIPPING_TASK", taskName);
                SlayerPlugin.setState(SlayerState.SKIPPING_TASK);
            } else if (isTaskOnSkipList(taskName)) {
                // Task is on skip list but we can't afford to skip (insufficient points)
                log.error("Task '{}' is on skip list but cannot skip - insufficient points!", taskName);
                Microbot.showMessage("Cannot skip task '" + taskName + "' - insufficient slayer points. Logging out.");
                stopAndLogout();
            } else {
                log.info("Task '{}' received, transitioning to DETECTING_TASK", taskName);
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        SlayerMaster master = config.slayerMaster();
        WorldPoint masterLocation = master.getLocation();
        SlayerPlugin.setCurrentLocation(master.getName());

        // Check if we're close to the master
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        int distance = playerLocation.distanceTo(masterLocation);

        if (distance <= 5) {
            // We're at the master, interact to get task
            Rs2NpcModel masterNpc = Rs2Npc.getNpc(master.getName());
            if (masterNpc != null) {
                if (Rs2Npc.interact(masterNpc, "Assignment")) {
                    log.info("Requesting task from {}", master.getName());
                    sleepUntil(Rs2Dialogue::isInDialogue, 3000);
                }
            } else {
                log.warn("Could not find slayer master: {}", master.getName());
            }
        } else {
            // Walk to the master
            if (!Rs2Player.isMoving()) {
                log.info("Walking to slayer master: {} at {}", master.getName(), masterLocation);
                Rs2Walker.walkTo(masterLocation);
            }
        }
    }

    private void handleSlayerDialogue() {
        // Boss task handling is disabled - users should disable "Like a Boss" perk for full AFK
        // The boss amount input dialogue ("How many would you like to slay?") will not be auto-handled
        // If you see this dialogue, you have "Like a Boss" enabled which is not supported
        // if (isBossAmountInputOpen()) {
        //     handleBossAmountInput();
        //     return;
        // }

        // Click through continue prompts
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            sleep(300, 600);
            return;
        }

        // Look for assignment-related options
        if (Rs2Dialogue.hasDialogueOption("I need another assignment")) {
            Rs2Dialogue.clickOption("I need another assignment");
            sleep(300, 600);
            return;
        }

        // Some masters have different dialogue options
        if (Rs2Dialogue.hasDialogueOption("assignment")) {
            Rs2Dialogue.clickOption("assignment", false);
            sleep(300, 600);
            return;
        }

        // Handle "got any easier tasks?" type options if present
        if (Rs2Dialogue.hasDialogueOption("easier")) {
            Rs2Dialogue.clickOption("easier", false);
            sleep(300, 600);
            return;
        }

        // Handle the post-task dialogue options (after receiving a task)
        // Options are typically: "Got any tips?", "Can I cancel that task?", "Okay great!"
        if (Rs2Dialogue.hasDialogueOption("Okay")) {
            log.info("Clicking 'Okay' to dismiss post-task dialogue");
            Rs2Dialogue.clickOption("Okay", false);
            sleep(300, 600);
            return;
        }

        // Alternative: some dialogues might say "great" or similar
        if (Rs2Dialogue.hasDialogueOption("great")) {
            log.info("Clicking 'great' to dismiss post-task dialogue");
            Rs2Dialogue.clickOption("great", false);
            sleep(300, 600);
            return;
        }

        // If we have a task and we're stuck in dialogue, try pressing escape to close
        if (Rs2Slayer.hasSlayerTask() && Rs2Dialogue.isInDialogue()) {
            log.info("Have task but stuck in dialogue, pressing escape to close");
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(300, 600);
            return;
        }
    }

    /**
     * Checks if the boss task amount input dialogue is open.
     * This is specifically for when selecting how many boss kills for a boss slayer task.
     * The dialogue asks "How many would you like to slay?" or "How many would you like to hunt?" with a number input.
     */
    private boolean isBossAmountInputOpen() {
        try {
            // Check for the specific chatbox input widget that appears for number entry
            // Widget 162, 42 is the chatbox input title
            String chatText = Rs2Widget.getChildWidgetText(162, 42);
            if (chatText == null) {
                return false;
            }

            // Only trigger for specific boss task amount prompts
            // The prompt varies: "How many would you like to slay?" or "How many would you like to hunt?"
            // NOT for general "Enter amount" or dialogue that just mentions "boss"
            String lowerText = chatText.toLowerCase();
            return lowerText.contains("how many would you like to slay") ||
                   lowerText.contains("how many would you like to hunt");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Handles the boss task amount selection by entering a value in the valid range
     * Boss tasks typically allow selecting between 3-55, we pick a low number to complete quickly
     */
    private void handleBossAmountInput() {
        log.info("Boss task amount input detected, entering minimum amount");
        // Enter the minimum amount (3 is typical minimum for boss tasks)
        // We use the minimum so the task is quick to skip
        Rs2Keyboard.typeString("3");
        sleep(200, 400);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        sleep(600, 1000);
        log.info("Boss task amount entered, task will be assigned then skipped");
    }

    private void handleSkippingTaskState() {
        // Handle dialogue or rewards interface if open
        if (Rs2Dialogue.isInDialogue() || Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            skipAttemptCounter++;
            handleSkipDialogue();
            return;
        }

        // Check if we still have a task (skip might have completed)
        String currentTask = Rs2Slayer.getSlayerTask();
        if (!Rs2Slayer.hasSlayerTask()) {
            log.info("Task '{}' skipped successfully, transitioning to GETTING_TASK", taskBeingSkipped);
            resetSkipState();
            SlayerPlugin.setState(SlayerState.GETTING_TASK);
            return;
        }

        // Check if we got a different task (skip worked and we auto-got new task)
        if (!taskBeingSkipped.isEmpty() && !taskBeingSkipped.equalsIgnoreCase(currentTask)) {
            log.info("Task changed from '{}' to '{}' - skip successful", taskBeingSkipped, currentTask);
            resetSkipState();
            // Check if new task should also be skipped
            if (shouldSkipTask(currentTask)) {
                log.info("New task '{}' is also on skip list", currentTask);
                taskBeingSkipped = currentTask;
            } else {
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        // Track which task we're trying to skip
        if (taskBeingSkipped.isEmpty()) {
            taskBeingSkipped = currentTask;
            log.info("Starting skip process for task: {}", taskBeingSkipped);
        }

        // Check if we've exceeded max attempts
        if (skipAttemptCounter >= MAX_SKIP_ATTEMPTS) {
            log.warn("Failed to skip task after {} attempts, proceeding with task instead", MAX_SKIP_ATTEMPTS);
            resetSkipState();
            SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            return;
        }

        // Check if we have enough points to skip
        int slayerPoints = SlayerPlugin.getSlayerPoints();
        if (slayerPoints < SKIP_TASK_COST || slayerPoints < config.minPointsToSkip()) {
            log.error("CANNOT SKIP TASK - Not enough slayer points! Have: {}, Need: {}, Min reserve: {}",
                    slayerPoints, SKIP_TASK_COST, config.minPointsToSkip());
            log.error("Task '{}' is on skip list but cannot be skipped. Stopping plugin and logging out.", taskBeingSkipped);
            Microbot.showMessage("Cannot skip task '" + taskBeingSkipped + "' - insufficient slayer points. Logging out.");
            resetSkipState();
            stopAndLogout();
            return;
        }

        SlayerMaster master = config.slayerMaster();
        WorldPoint masterLocation = master.getLocation();
        SlayerPlugin.setCurrentLocation(master.getName() + " (Skipping)");

        // Check if we're close to the master
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        int distance = playerLocation.distanceTo(masterLocation);

        if (distance <= 5) {
            // We're at the master, interact to open rewards
            Rs2NpcModel masterNpc = Rs2Npc.getNpc(master.getName());
            if (masterNpc != null) {
                if (Rs2Npc.interact(masterNpc, "Rewards")) {
                    log.info("Opening rewards menu to skip task (attempt {})", skipAttemptCounter + 1);
                    sleepUntil(() -> Rs2Dialogue.isInDialogue() || Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0), 3000);
                }
            } else {
                log.warn("Could not find slayer master: {}", master.getName());
                skipAttemptCounter++;
            }
        } else {
            // Walk to the master
            if (!Rs2Player.isMoving()) {
                log.info("Walking to slayer master to skip task: {}", master.getName());
                Rs2Walker.walkTo(masterLocation);
            }
        }
    }

    private void resetSkipState() {
        skipAttemptCounter = 0;
        taskBeingSkipped = "";
    }

    // ==================== POH Methods ====================

    private void handleRestoringAtPohState() {
        SlayerPlugin.setCurrentLocation("POH");

        // Check if we're in the house (house portal exists)
        boolean inHouse = isInPoh();

        if (!inHouse) {
            // Need to teleport to house
            if (!pohTeleportAttempted) {
                if (teleportToHouse()) {
                    pohTeleportAttempted = true;
                    sleepUntil(this::isInPoh, 5000);
                } else {
                    log.warn("Failed to teleport to house, falling back to banking");
                    resetPohState();
                    SlayerPlugin.setState(stateAfterPoh);
                }
            } else {
                // Teleport was attempted but we're not in house - wait a bit more
                sleep(600, 1000);
                if (!isInPoh()) {
                    log.warn("Still not in house after teleport, falling back");
                    resetPohState();
                    SlayerPlugin.setState(stateAfterPoh);
                }
            }
            return;
        }

        // We're in the house - find and use the pool
        if (!Rs2Player.isFullHealth() || !isFullPrayer() || Rs2Player.getRunEnergy() < 100) {
            if (useRejuvenationPool()) {
                log.info("Using rejuvenation pool");
                sleepUntil(() -> Rs2Player.isFullHealth() && isFullPrayer(), 5000);
            } else {
                log.warn("Could not find rejuvenation pool in house");
            }
        }

        // Check if we're fully restored
        if (Rs2Player.isFullHealth() && isFullPrayer()) {
            log.info("Fully restored at POH, leaving house");
            leaveHouse();
            sleepUntil(() -> !isInPoh(), 3000);
            resetPohState();

            // Transition to next state
            log.info("Transitioning to {}", stateAfterPoh);
            SlayerPlugin.setState(stateAfterPoh);
        }
    }

    private boolean isInPoh() {
        return Rs2GameObject.findObjectById(HOUSE_PORTAL_ID) != null;
    }

    private boolean isFullPrayer() {
        int currentPrayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
        return currentPrayer >= maxPrayer;
    }

    private boolean teleportToHouse() {
        PohTeleportMethod method = config.pohTeleportMethod();
        log.info("Teleporting to house using: {}", method.getDisplayName());

        switch (method) {
            case SPELL:
                if (Rs2Magic.canCast(MagicAction.TELEPORT_TO_HOUSE)) {
                    Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
                    return true;
                }
                log.warn("Cannot cast Teleport to House spell");
                break;

            case HOUSE_TAB:
                if (Rs2Inventory.hasItem("Teleport to house")) {
                    Rs2Inventory.interact("Teleport to house", "Break");
                    return true;
                }
                log.warn("No house tabs in inventory");
                break;

            case CONSTRUCTION_CAPE:
                if (Rs2Inventory.hasItem("Construct. cape") || Rs2Inventory.hasItem("Construct. cape(t)")) {
                    String capeName = Rs2Inventory.hasItem("Construct. cape(t)") ? "Construct. cape(t)" : "Construct. cape";
                    Rs2Inventory.interact(capeName, "Tele to POH");
                    return true;
                }
                // Also check if worn
                // For now, just check inventory
                log.warn("No construction cape in inventory");
                break;

            case MAX_CAPE:
                if (Rs2Inventory.hasItem("Max cape")) {
                    Rs2Inventory.interact("Max cape", "Tele to POH");
                    return true;
                }
                log.warn("No max cape in inventory");
                break;
        }

        // Fallback to house tab if primary method fails
        if (method != PohTeleportMethod.HOUSE_TAB && Rs2Inventory.hasItem("Teleport to house")) {
            log.info("Falling back to house tab");
            Rs2Inventory.interact("Teleport to house", "Break");
            return true;
        }

        return false;
    }

    private boolean useRejuvenationPool() {
        for (int poolId : REJUVENATION_POOL_IDS) {
            if (Rs2GameObject.exists(poolId)) {
                return Rs2GameObject.interact(poolId, "Drink");
            }
        }
        return false;
    }

    private void leaveHouse() {
        // Try to use portal nexus to teleport to Grand Exchange (near bank)
        if (teleportViaPortalNexus()) {
            log.info("Leaving house via portal nexus to Grand Exchange");
            return;
        }

        // Try mounted glory to Edgeville (near GE)
        if (teleportViaMountedGlory()) {
            log.info("Leaving house via mounted glory to Edgeville");
            return;
        }

        // Try mounted ring of wealth to GE
        if (teleportViaMountedWealth()) {
            log.info("Leaving house via mounted ring of wealth to Grand Exchange");
            return;
        }

        // Fallback to house portal exit
        if (Rs2GameObject.interact(HOUSE_PORTAL_ID, "Enter")) {
            log.info("Leaving house via portal (no teleport options found)");
        }
    }

    /**
     * Attempts to teleport to the Grand Exchange via the portal nexus
     * @return true if interaction was successful, false if nexus not found
     */
    private boolean teleportViaPortalNexus() {
        // Try to find any tier of portal nexus
        for (int nexusId : PORTAL_NEXUS_IDS) {
            if (Rs2GameObject.exists(nexusId)) {
                // Try direct Grand Exchange teleport option first
                if (Rs2GameObject.interact(nexusId, "Grand Exchange")) {
                    return true;
                }
                // Some nexus configurations might use different option names
                if (Rs2GameObject.interact(nexusId, "Varrock Grand Exchange")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to teleport via mounted glory to Edgeville (close to GE)
     * @return true if interaction was successful, false if mounted glory not found
     */
    private boolean teleportViaMountedGlory() {
        for (int gloryId : MOUNTED_GLORY_IDS) {
            if (Rs2GameObject.exists(gloryId)) {
                if (Rs2GameObject.interact(gloryId, "Edgeville")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to teleport via mounted ring of wealth to Grand Exchange
     * @return true if interaction was successful, false if mounted wealth not found
     */
    private boolean teleportViaMountedWealth() {
        if (Rs2GameObject.exists(MOUNTED_WEALTH_ID)) {
            if (Rs2GameObject.interact(MOUNTED_WEALTH_ID, "Grand Exchange")) {
                return true;
            }
        }
        return false;
    }

    private void resetPohState() {
        pohTeleportAttempted = false;
    }

    /**
     * Checks if we should use POH for restoration based on current state
     */
    private boolean shouldUsePoh() {
        if (!config.usePohPool()) {
            return false;
        }

        // Check if we have a way to teleport
        PohTeleportMethod method = config.pohTeleportMethod();
        switch (method) {
            case SPELL:
                if (!Rs2Magic.canCast(MagicAction.TELEPORT_TO_HOUSE)) {
                    return Rs2Inventory.hasItem("Teleport to house"); // Fallback check
                }
                return true;
            case HOUSE_TAB:
                return Rs2Inventory.hasItem("Teleport to house");
            case CONSTRUCTION_CAPE:
                return Rs2Inventory.hasItem("Construct. cape") ||
                       Rs2Inventory.hasItem("Construct. cape(t)") ||
                       Rs2Inventory.hasItem("Teleport to house");
            case MAX_CAPE:
                return Rs2Inventory.hasItem("Max cape") ||
                       Rs2Inventory.hasItem("Teleport to house");
            default:
                return Rs2Inventory.hasItem("Teleport to house");
        }
    }

    /**
     * Checks if we need POH restoration based on HP/Prayer thresholds
     */
    private boolean needsPohRestoration() {
        if (!config.usePohPool() || !config.usePohBeforeBanking()) {
            return false;
        }

        int threshold = config.pohRestoreThreshold();
        if (threshold <= 0) {
            return false;
        }

        double healthPercent = Rs2Player.getHealthPercentage();
        return healthPercent < threshold && shouldUsePoh();
    }

    // ==================== End POH Methods ====================

    // Slayer rewards interface widget constants (group 426)
    // Slayer rewards interface (widget group 426)
    private static final int SLAYER_REWARDS_GROUP_ID = 426;

    private void handleSkipDialogue() {
        // Click through continue prompts first
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            sleep(300, 600);
            return;
        }

        // Check if slayer rewards interface is open (widget-based, not dialogue)
        if (Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            handleSlayerRewardsInterface();
            return;
        }

        // Step 1: Look for "Assignment" or "Assignments" option in main Rewards menu
        // This takes us to the task management submenu
        if (Rs2Dialogue.hasDialogueOption("Assignment")) {
            log.info("Clicking Assignment option");
            Rs2Dialogue.clickOption("Assignment");
            sleep(300, 600);
            return;
        }

        // Step 2: Look for task cancellation options
        // Different masters/interfaces may use different wording
        if (Rs2Dialogue.hasDialogueOption("Cancel current task")) {
            log.info("Clicking Cancel current task option (costs 30 points)");
            Rs2Dialogue.clickOption("Cancel current task");
            sleep(300, 600);
            return;
        }

        // Alternative wording - some interfaces say "Skip task"
        if (Rs2Dialogue.hasDialogueOption("Skip task")) {
            log.info("Clicking Skip task option");
            Rs2Dialogue.clickOption("Skip task");
            sleep(300, 600);
            return;
        }

        // Partial match for "cancel" in case of different wording
        if (Rs2Dialogue.hasDialogueOption("cancel")) {
            log.info("Clicking cancel option (partial match)");
            Rs2Dialogue.clickOption("cancel", false);
            sleep(300, 600);
            return;
        }

        // Step 3: Confirm the skip
        if (Rs2Dialogue.hasDialogueOption("Yes")) {
            log.info("Confirming task skip");
            Rs2Dialogue.clickOption("Yes");
            sleep(600, 1000);
            return;
        }

        // If we see a "No" option but no "Yes", we might be in wrong menu - go back
        if (Rs2Dialogue.hasDialogueOption("No") && !Rs2Dialogue.hasDialogueOption("Yes")) {
            log.warn("Found No but not Yes - might be in wrong dialogue state");
        }
    }

    /**
     * Handles the slayer rewards widget interface for skipping/cancelling a task.
     * Flow: Task tab -> Cancel -> Confirm
     */
    private void handleSlayerRewardsInterface() {
        // Step 3: Confirm overlay
        if (Rs2Widget.clickWidget("Confirm", Optional.of(SLAYER_REWARDS_GROUP_ID), 0, false)) {
            log.info("Clicking Confirm to finalize task skip");
            sleep(600, 1000);
            return;
        }

        // Step 2: Cancel button (visible when on Task tab)
        if (Rs2Widget.clickWidget("Cancel", Optional.of(SLAYER_REWARDS_GROUP_ID), 0, false)) {
            log.info("Clicking Cancel task button (costs 30 points)");
            sleep(600, 1000);
            return;
        }

        // Step 1: Navigate to Task tab
        if (Rs2Widget.clickWidget("Tasks", Optional.of(SLAYER_REWARDS_GROUP_ID), 0, false)) {
            log.info("Clicking Tasks tab in slayer rewards interface");
            sleep(400, 700);
            return;
        }

        log.warn("Could not find Tasks tab, Cancel button, or Confirm in slayer rewards interface");
    }

    /**
     * Checks if the given task name should be skipped based on config
     */
    private boolean shouldSkipTask(String taskName) {
        if (!config.enableAutoSkip() || taskName == null || taskName.isEmpty()) {
            return false;
        }

        String skipList = config.skipTaskList();
        if (skipList == null || skipList.isEmpty()) {
            return false;
        }

        // Check if we have enough points
        int slayerPoints = SlayerPlugin.getSlayerPoints();
        if (slayerPoints < SKIP_TASK_COST || slayerPoints < config.minPointsToSkip()) {
            return false;
        }

        // Parse skip list and check if task is in it
        String taskLower = taskName.toLowerCase().trim();
        return Arrays.stream(skipList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(skipTask -> taskLower.contains(skipTask) || skipTask.contains(taskLower));
    }

    /**
     * Checks if a task is on the skip list (ignoring point requirements)
     * Used to determine if we should stop when we can't afford to skip
     */
    private boolean isTaskOnSkipList(String taskName) {
        if (!config.enableAutoSkip() || taskName == null || taskName.isEmpty()) {
            return false;
        }

        String skipList = config.skipTaskList();
        if (skipList == null || skipList.isEmpty()) {
            return false;
        }

        // Just check if task is in the skip list, don't check points
        String taskLower = taskName.toLowerCase().trim();
        return Arrays.stream(skipList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(skipTask -> taskLower.contains(skipTask) || skipTask.contains(taskLower));
    }

    // ==================== Block Task Methods ====================

    private void handleBlockingTaskState() {
        // Handle dialogue or rewards interface if open
        if (Rs2Dialogue.isInDialogue() || Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            blockAttemptCounter++;
            handleBlockDialogue();
            return;
        }

        // Check if we still have a task (block might have completed)
        String currentTask = Rs2Slayer.getSlayerTask();
        if (!Rs2Slayer.hasSlayerTask()) {
            log.info("Task '{}' blocked successfully, transitioning to GETTING_TASK", taskBeingBlocked);
            resetBlockState();
            SlayerPlugin.setState(SlayerState.GETTING_TASK);
            return;
        }

        // Check if we got a different task (block worked and we auto-got new task)
        if (!taskBeingBlocked.isEmpty() && !taskBeingBlocked.equalsIgnoreCase(currentTask)) {
            log.info("Task changed from '{}' to '{}' - block successful", taskBeingBlocked, currentTask);
            resetBlockState();
            // Check if new task should also be blocked or skipped
            if (shouldBlockTask(currentTask)) {
                log.info("New task '{}' is also on block list", currentTask);
                taskBeingBlocked = currentTask;
            } else if (shouldSkipTask(currentTask)) {
                log.info("New task '{}' is on skip list", currentTask);
                SlayerPlugin.setState(SlayerState.SKIPPING_TASK);
            } else {
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        // Track which task we're trying to block
        if (taskBeingBlocked.isEmpty()) {
            taskBeingBlocked = currentTask;
            log.info("Starting block process for task: {}", taskBeingBlocked);
        }

        // Check if we've exceeded max attempts
        if (blockAttemptCounter >= MAX_BLOCK_ATTEMPTS) {
            log.warn("Failed to block task after {} attempts, trying to skip instead", MAX_BLOCK_ATTEMPTS);
            resetBlockState();
            // Fall back to skipping if we can
            if (shouldSkipTask(currentTask)) {
                SlayerPlugin.setState(SlayerState.SKIPPING_TASK);
            } else {
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        // Check if we have enough points to block
        int slayerPoints = SlayerPlugin.getSlayerPoints();
        if (slayerPoints < BLOCK_TASK_COST || slayerPoints < config.minPointsToBlock()) {
            log.warn("Cannot block task - not enough points. Have: {}, Need: {}, Min reserve: {}",
                    slayerPoints, BLOCK_TASK_COST, config.minPointsToBlock());
            log.info("Checking if task can be skipped instead...");
            resetBlockState();
            // Try to skip instead if on skip list and can afford
            if (shouldSkipTask(currentTask)) {
                SlayerPlugin.setState(SlayerState.SKIPPING_TASK);
            } else if (isTaskOnSkipList(currentTask)) {
                // On skip list but can't afford skip either
                log.error("Task '{}' cannot be blocked or skipped - insufficient points!", currentTask);
                Microbot.showMessage("Cannot block/skip task '" + currentTask + "' - insufficient slayer points. Logging out.");
                stopAndLogout();
            } else {
                // Not on skip list, just do the task
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        SlayerMaster master = config.slayerMaster();
        WorldPoint masterLocation = master.getLocation();
        SlayerPlugin.setCurrentLocation(master.getName() + " (Blocking)");

        // Check if we're close to the master
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        int distance = playerLocation.distanceTo(masterLocation);

        if (distance <= 5) {
            // We're at the master, interact to open rewards
            Rs2NpcModel masterNpc = Rs2Npc.getNpc(master.getName());
            if (masterNpc != null) {
                if (Rs2Npc.interact(masterNpc, "Rewards")) {
                    log.info("Opening rewards menu to block task (attempt {})", blockAttemptCounter + 1);
                    sleepUntil(() -> Rs2Dialogue.isInDialogue() || Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0), 3000);
                }
            } else {
                log.warn("Could not find slayer master: {}", master.getName());
                blockAttemptCounter++;
            }
        } else {
            // Walk to the master
            if (!Rs2Player.isMoving()) {
                log.info("Walking to slayer master to block task: {}", master.getName());
                Rs2Walker.walkTo(masterLocation);
            }
        }
    }

    private void handleBlockDialogue() {
        // Click through continue prompts first
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            sleep(300, 600);
            return;
        }

        // Check if slayer rewards interface is open (widget-based, not dialogue)
        if (Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            handleSlayerRewardsBlockInterface();
            return;
        }

        // Standard dialogue options (fallback for older interfaces)
        if (Rs2Dialogue.hasDialogueOption("Assignment")) {
            log.info("Clicking Assignment option");
            Rs2Dialogue.clickOption("Assignment");
            sleep(300, 600);
            return;
        }

        if (Rs2Dialogue.hasDialogueOption("Block current task")) {
            log.info("Clicking Block current task option (costs 100 points)");
            Rs2Dialogue.clickOption("Block current task");
            sleep(300, 600);
            return;
        }

        if (Rs2Dialogue.hasDialogueOption("Block task")) {
            log.info("Clicking Block task option");
            Rs2Dialogue.clickOption("Block task");
            sleep(300, 600);
            return;
        }

        if (Rs2Dialogue.hasDialogueOption("block")) {
            log.info("Clicking block option (partial match)");
            Rs2Dialogue.clickOption("block", false);
            sleep(300, 600);
            return;
        }

        // Confirm the block
        if (Rs2Dialogue.hasDialogueOption("Yes")) {
            log.info("Confirming task block");
            Rs2Dialogue.clickOption("Yes");
            sleep(600, 1000);
            return;
        }
    }

    /**
     * Handles the slayer rewards widget interface for blocking a task.
     * Flow: Task tab -> Block -> Confirm
     */
    private void handleSlayerRewardsBlockInterface() {
        // Step 3: Confirm overlay
        if (Rs2Widget.clickWidget("Confirm", Optional.of(SLAYER_REWARDS_GROUP_ID), 0, false)) {
            log.info("Clicking Confirm to finalize task block");
            sleep(600, 1000);
            return;
        }

        // Step 2: Block button (visible when on Task tab)
        if (Rs2Widget.clickWidget("Block", Optional.of(SLAYER_REWARDS_GROUP_ID), 0, false)) {
            log.info("Clicking Block task button (costs 100 points)");
            sleep(600, 1000);
            return;
        }

        // Step 1: Navigate to Task tab
        if (Rs2Widget.clickWidget("Tasks", Optional.of(SLAYER_REWARDS_GROUP_ID), 0, false)) {
            log.info("Clicking Tasks tab in slayer rewards interface");
            sleep(400, 700);
            return;
        }

        log.warn("Could not find Tasks tab, Block button, or Confirm in slayer rewards interface");
    }

    private void resetBlockState() {
        blockAttemptCounter = 0;
        taskBeingBlocked = "";
    }

    /**
     * Checks if the given task name should be blocked based on config.
     * Block takes priority over skip since it's a permanent solution.
     */
    private boolean shouldBlockTask(String taskName) {
        if (!config.enableAutoBlock() || taskName == null || taskName.isEmpty()) {
            return false;
        }

        String blockList = config.blockTaskList();
        if (blockList == null || blockList.isEmpty()) {
            return false;
        }

        // Check if we have enough points to block
        int slayerPoints = SlayerPlugin.getSlayerPoints();
        if (slayerPoints < BLOCK_TASK_COST || slayerPoints < config.minPointsToBlock()) {
            return false;
        }

        // Parse block list and check if task is in it
        String taskLower = taskName.toLowerCase().trim();
        return Arrays.stream(blockList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(blockTask -> taskLower.contains(blockTask) || blockTask.contains(taskLower));
    }

    /**
     * Checks if a task is on the block list (ignoring point requirements)
     * Used to determine if we should try to skip instead when we can't afford to block
     */
    private boolean isTaskOnBlockList(String taskName) {
        if (!config.enableAutoBlock() || taskName == null || taskName.isEmpty()) {
            return false;
        }

        String blockList = config.blockTaskList();
        if (blockList == null || blockList.isEmpty()) {
            return false;
        }

        // Just check if task is in the block list, don't check points
        String taskLower = taskName.toLowerCase().trim();
        return Arrays.stream(blockList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(blockTask -> taskLower.contains(blockTask) || blockTask.contains(taskLower));
    }

    private void handleDetectingTaskState(boolean hasTask) {
        // Dismiss any open dialogue first (e.g., residual slayer master dialogue)
        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                sleep(300, 600);
            } else {
                // Try pressing escape to close dialogue
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                sleep(300, 600);
            }
            return;
        }

        if (!hasTask) {
            log.info("No task found, returning to IDLE");
            SlayerPlugin.setState(SlayerState.IDLE);
            resetTravelData();
            return;
        }

        String taskName = Rs2Slayer.getSlayerTask();

        // Look up JSON profile first (preferred)
        activeJsonProfile = profileManager.findProfile(taskName);
        if (activeJsonProfile != null) {
            log.info("Found JSON profile for '{}': setup={}, prayer={}, cannon={}, antipoison={}, antivenom={}",
                    taskName, activeJsonProfile.getSetup(), activeJsonProfile.getPrayer(),
                    activeJsonProfile.isCannon(), activeJsonProfile.isAntipoison(), activeJsonProfile.isAntivenom());
        } else {
            log.warn("No JSON profile found for task '{}' - prayer/location settings may not apply. " +
                    "Add a profile to ~/.runelite/slayer-profiles.json or delete the file to regenerate defaults.",
                    taskName);
        }

        // Update flicker script with active profile prayer
        if (activeJsonProfile != null && activeJsonProfile.hasPrayer()) {
            SlayerPlugin.getFlickerScript().setActivePrayer(activeJsonProfile.getParsedPrayer());
        } else {
            SlayerPlugin.getFlickerScript().clearActiveProfile();
        }

        // Determine if we should use cannon for this task
        isUsingCannon = shouldUseCannon(taskName);

        // Get task location
        if (taskDestination == null) {
            // Priority 1: If using cannon and profile has cannonLocation, use that
            if (isUsingCannon && activeJsonProfile != null && activeJsonProfile.hasCannonLocation()) {
                SlayerLocation cannonLocation = SlayerLocation.fromName(activeJsonProfile.getCannonLocation());
                if (cannonLocation != null) {
                    taskDestination = cannonLocation.getWorldPoint();
                    cannonSpot = taskDestination; // Cannon will be placed here
                    taskLocationName = cannonLocation.getDisplayName();
                    SlayerPlugin.setCurrentLocation(taskLocationName + " (Cannon)");
                    log.info("Using profile cannon location for {}: {} at {}", taskName, taskLocationName, taskDestination);
                } else {
                    log.warn("Unknown profile cannon location '{}', falling back to auto-selection", activeJsonProfile.getCannonLocation());
                }
            }

            // Priority 2: If not using cannon and profile has location, use that
            if (taskDestination == null && !isUsingCannon && activeJsonProfile != null && activeJsonProfile.hasLocation()) {
                SlayerLocation profileLocation = SlayerLocation.fromName(activeJsonProfile.getLocation());
                if (profileLocation != null) {
                    taskDestination = profileLocation.getWorldPoint();
                    taskLocationName = profileLocation.getDisplayName();
                    SlayerPlugin.setCurrentLocation(taskLocationName);
                    log.info("Using profile location for {}: {} at {}", taskName, taskLocationName, taskDestination);
                } else {
                    log.warn("Unknown profile location '{}', falling back to auto-selection", activeJsonProfile.getLocation());
                }
            }

            // Priority 3: If using cannon but no cannonLocation, try CannonSpot enum
            if (taskDestination == null && isUsingCannon) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                cannonSpot = CannonSpot.getClosestSpot(taskName, playerLocation);
                if (cannonSpot != null) {
                    taskDestination = cannonSpot;
                    taskLocationName = "Cannon spot";
                    SlayerPlugin.setCurrentLocation(taskLocationName + " (Cannon)");
                    log.info("Cannon spot found for {}: {}", taskName, cannonSpot);
                } else {
                    log.warn("No cannon spot found for task: {}, using regular location", taskName);
                    isUsingCannon = false;
                }
            }

            // Priority 4: Fall back to auto-selected task location
            if (taskDestination == null) {
                MonsterLocation monsterLocation = Rs2Slayer.getSlayerTaskLocation(3, true);
                if (monsterLocation != null) {
                    taskDestination = monsterLocation.getBestClusterCenter();
                    taskLocationName = monsterLocation.getLocationName();
                    SlayerPlugin.setCurrentLocation(taskLocationName);
                    log.info("Task location found: {} at {}", taskLocationName, taskDestination);
                } else {
                    log.warn("Could not find location for slayer task");
                    return;
                }
            }
        }

        // Check if we need to bank first (initial setup or low supplies)
        if (config.enableAutoBanking() && getActiveInventorySetup() != null) {
            if (!initialSetupDone || needsBanking()) {
                log.info("Banking required, transitioning to BANKING");
                SlayerPlugin.setState(SlayerState.BANKING);
                return;
            }
        }

        // Go to traveling
        if (config.enableAutoTravel()) {
            log.info("Transitioning to TRAVELING");
            SlayerPlugin.setState(SlayerState.TRAVELING);
        } else {
            log.info("Auto travel disabled, staying in DETECTING_TASK");
        }
    }

    private void handleBankingState() {
        // Dismiss any open dialogue first (e.g., residual slayer master dialogue)
        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                sleep(300, 600);
            } else {
                // Try pressing escape to close dialogue
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                sleep(300, 600);
            }
            return;
        }

        // Disable prayers when banking
        deactivateProfilePrayer();

        // Safety: if cannonPlaced is still true at banking, the cannon was not picked up before leaving
        // This can happen if the player teleported away. Reset the flag to avoid getting stuck.
        if (cannonPlaced) {
            if (isCannonPlacedNearby()) {
                log.info("Cannon found nearby at bank, picking up");
                if (pickupCannon()) {
                    sleepUntil(() -> !isCannonPlacedNearby(), 3000);
                }
            } else {
                log.warn("Cannon was left at task location (not nearby), resetting cannon state");
            }
            cannonPlaced = false;
            cannonFired = false;
        }

        // Get the active inventory setup (cannon or regular)
        var activeSetup = getActiveInventorySetup();
        if (activeSetup == null) {
            log.warn("No inventory setup configured, skipping banking");
            if (config.enableAutoTravel()) {
                SlayerPlugin.setState(SlayerState.TRAVELING);
            } else {
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        // If bank is not open, walk to bank and open it
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Player.isMoving()) {
                // Try to open bank if we're near one
                if (Rs2Bank.openBank()) {
                    sleepUntil(Rs2Bank::isOpen, 3000);
                } else {
                    log.info("Walking to nearest bank");
                    Rs2Bank.walkToBank();
                }
            }
            return;
        }

        // Bank is open, load inventory setup
        log.info("Loading inventory setup: {} (cannon: {})", activeSetup.getName(), isUsingCannon);
        Rs2InventorySetup inventorySetup = new Rs2InventorySetup(activeSetup, mainScheduledFuture);

        // Check if already matching
        if (inventorySetup.doesEquipmentMatch() && inventorySetup.doesInventoryMatch()) {
            log.info("Inventory setup matches, closing bank");
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            initialSetupDone = true;

            // Check if we need to swap spellbook based on inventory setup
            if (needsSpellbookSwap()) {
                log.info("Inventory setup requires spellbook swap - transitioning to SWAPPING_SPELLBOOK");
                SlayerPlugin.setState(SlayerState.SWAPPING_SPELLBOOK);
            } else if (config.enableAutoTravel()) {
                SlayerPlugin.setState(SlayerState.TRAVELING);
            } else {
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        // Load equipment and inventory
        boolean equipmentLoaded = inventorySetup.loadEquipment();
        boolean inventoryLoaded = inventorySetup.loadInventory();

        if (equipmentLoaded && inventoryLoaded) {
            log.info("Inventory setup loaded successfully");
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            initialSetupDone = true;

            // Check if we need to swap spellbook based on inventory setup
            if (needsSpellbookSwap()) {
                log.info("Inventory setup requires spellbook swap - transitioning to SWAPPING_SPELLBOOK");
                SlayerPlugin.setState(SlayerState.SWAPPING_SPELLBOOK);
            } else if (config.enableAutoTravel()) {
                SlayerPlugin.setState(SlayerState.TRAVELING);
            } else {
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
        } else {
            log.warn("Failed to load inventory setup completely");
        }
    }

    // Spellbook integer values from InventorySetup
    // 0=Standard, 1=Ancient, 2=Lunar, 3=Arceuus, 4=None
    private static final int SPELLBOOK_STANDARD = 0;
    private static final int SPELLBOOK_ANCIENT = 1;
    private static final int SPELLBOOK_LUNAR = 2;
    private static final int SPELLBOOK_ARCEUUS = 3;
    private static final int SPELLBOOK_NONE = 4;

    // Track the current inventory setup for spellbook checking
    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup currentInventorySetup = null;

    /**
     * Checks if the current inventory setup requires a spellbook swap.
     * Returns true if the inventory setup has a spellbook defined that doesn't match current.
     */
    private boolean needsSpellbookSwap() {
        // Get the active inventory setup
        var setup = getActiveInventorySetup();
        if (setup == null) {
            log.debug("No inventory setup, no spellbook swap needed");
            return false;
        }

        // Store reference for the spellbook swap handler
        currentInventorySetup = setup;

        // Create Rs2InventorySetup to check spellbook
        Rs2InventorySetup inventorySetup = new Rs2InventorySetup(setup, mainScheduledFuture);

        // Check if spellbook matches - if it does, no swap needed
        if (inventorySetup.hasSpellBook()) {
            log.debug("Spellbook matches inventory setup, no swap needed");
            return false;
        }

        // Spellbook doesn't match - need to swap
        // Get the required spellbook from the inventory setup
        int requiredSpellbook = setup.getSpellBook();
        if (requiredSpellbook == SPELLBOOK_NONE || requiredSpellbook < 0) {
            log.debug("Inventory setup has no spellbook requirement (value: {})", requiredSpellbook);
            return false;
        }

        log.info("Inventory setup requires spellbook {} but player is on different spellbook - swap required",
                getSpellbookName(requiredSpellbook));
        return true;
    }

    /**
     * Gets a human-readable name for a spellbook integer value.
     */
    private String getSpellbookName(int spellbook) {
        switch (spellbook) {
            case SPELLBOOK_STANDARD: return "Standard";
            case SPELLBOOK_ANCIENT: return "Ancient";
            case SPELLBOOK_LUNAR: return "Lunar";
            case SPELLBOOK_ARCEUUS: return "Arceuus";
            default: return "Unknown (" + spellbook + ")";
        }
    }

    /**
     * Gets the target spellbook from the current inventory setup.
     * Returns null if no setup or no spellbook requirement.
     * Spellbook values: 0=Standard, 1=Ancient, 2=Lunar, 3=Arceuus, 4=None
     */
    private Rs2Spellbook getRequiredSpellbook() {
        if (currentInventorySetup == null) {
            return null;
        }

        int spellbook = currentInventorySetup.getSpellBook();

        // If spellbook is NONE or invalid, no requirement
        if (spellbook == SPELLBOOK_NONE || spellbook < 0) {
            return null;
        }

        // Convert InventorySetup spellbook int to Rs2Spellbook
        switch (spellbook) {
            case SPELLBOOK_ANCIENT:
                return Rs2Spellbook.ANCIENT;
            case SPELLBOOK_LUNAR:
                return Rs2Spellbook.LUNAR;
            case SPELLBOOK_STANDARD:
            default:
                return Rs2Spellbook.MODERN;
        }
    }

    // Occult Altar IDs for POH (Basic, Marble, Gilded, Ancient variants)
    private static final int[] OCCULT_ALTAR_IDS = {29149, 29150, 29151, 29152};

    /**
     * Handles swapping spellbook in POH based on inventory setup requirements.
     * Uses the occult altar to switch to the required spellbook.
     */
    private void handleSwappingSpellbookState() {
        SlayerPlugin.setCurrentLocation("POH (Spellbook)");

        // Get the required spellbook from inventory setup
        Rs2Spellbook requiredSpellbook = getRequiredSpellbook();
        if (requiredSpellbook == null) {
            log.warn("No spellbook requirement found, skipping swap");
            resetPohState();
            currentInventorySetup = null;
            if (config.enableAutoTravel()) {
                SlayerPlugin.setState(SlayerState.TRAVELING);
            } else {
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        // If we're already on the required spellbook, skip to traveling
        if (Rs2Magic.isSpellbook(requiredSpellbook)) {
            log.info("Already on {} spellbook, transitioning to TRAVELING", requiredSpellbook);
            resetPohState();
            currentInventorySetup = null;
            if (config.enableAutoTravel()) {
                SlayerPlugin.setState(SlayerState.TRAVELING);
            } else {
                SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            }
            return;
        }

        // Check if we're in the house (house portal exists)
        boolean inHouse = isInPoh();

        if (!inHouse) {
            // Need to teleport to house
            if (!pohTeleportAttempted) {
                if (teleportToHouse()) {
                    pohTeleportAttempted = true;
                    sleepUntil(this::isInPoh, 5000);
                } else {
                    log.warn("Failed to teleport to house for spellbook swap, falling back to TRAVELING");
                    resetPohState();
                    currentInventorySetup = null;
                    if (config.enableAutoTravel()) {
                        SlayerPlugin.setState(SlayerState.TRAVELING);
                    } else {
                        SlayerPlugin.setState(SlayerState.DETECTING_TASK);
                    }
                }
            } else {
                // Teleport was attempted but we're not in house - wait a bit more
                sleep(600, 1000);
                if (!isInPoh()) {
                    log.warn("Still not in house after teleport for spellbook swap, falling back");
                    resetPohState();
                    currentInventorySetup = null;
                    if (config.enableAutoTravel()) {
                        SlayerPlugin.setState(SlayerState.TRAVELING);
                    } else {
                        SlayerPlugin.setState(SlayerState.DETECTING_TASK);
                    }
                }
            }
            return;
        }

        // Check if the occult altar dialog is genuinely open (only if a "Select an Option" dialog is visible)
        if (Rs2Dialogue.hasSelectAnOption()) {
            log.info("Occult altar dialog already open, handling spellbook selection");
            if (handleSpellbookWidget(requiredSpellbook)) {
                return;
            }
        }

        // Find and interact with the occult altar
        TileObject occultAltar = findOccultAltar();
        if (occultAltar != null) {
            // Try right-click option first (e.g., "Ancient Magicks", "Lunar", "Standard")
            String altarOption = getAltarOptionForSpellbook(requiredSpellbook);
            log.info("Attempting occult altar right-click option: '{}'", altarOption);

            if (Rs2GameObject.interact(occultAltar, altarOption)) {
                // Wait briefly for either direct spellbook switch or a dialog to open
                sleepUntil(() -> Rs2Magic.isSpellbook(requiredSpellbook) || Rs2Dialogue.hasSelectAnOption(), 3000);

                // Check if the spellbook already switched (right-click option worked directly)
                if (Rs2Magic.isSpellbook(requiredSpellbook)) {
                    log.info("Successfully switched to {} spellbook via right-click", requiredSpellbook);
                    finishSpellbookSwap();
                    return;
                }

                // Right-click option may have opened a dialog - handle it
                if (Rs2Dialogue.hasSelectAnOption() && handleSpellbookWidget(requiredSpellbook)) {
                    return;
                }
            } else {
                // Right-click option didn't match - try left-click "Venerate" which opens dialog
                log.info("Right-click option '{}' not found, trying Venerate", altarOption);
                if (Rs2GameObject.interact(occultAltar, "Venerate")) {
                    sleepUntil(Rs2Dialogue::hasSelectAnOption, 3000);
                    if (!handleSpellbookWidget(requiredSpellbook)) {
                        log.warn("Venerate used but could not handle spellbook dialog");
                    }
                } else {
                    // Last resort: plain interact
                    log.info("Trying plain interact on occult altar");
                    Rs2GameObject.interact(occultAltar);
                    sleepUntil(Rs2Dialogue::hasSelectAnOption, 3000);
                    handleSpellbookWidget(requiredSpellbook);
                }
            }
        } else {
            log.warn("Could not find occult altar in house - ensure POH has an occult altar");
            spellbookSwapAttempts++;
            if (spellbookSwapAttempts >= MAX_SPELLBOOK_SWAP_ATTEMPTS) {
                log.warn("Max spellbook swap attempts reached, skipping swap");
                spellbookSwapAttempts = 0;
                leaveHouse();
                sleepUntil(() -> !isInPoh(), 3000);
                resetPohState();
                currentInventorySetup = null;
                if (config.enableAutoTravel()) {
                    SlayerPlugin.setState(SlayerState.TRAVELING);
                } else {
                    SlayerPlugin.setState(SlayerState.DETECTING_TASK);
                }
            }
        }
    }

    /**
     * Handles a spellbook selection widget/dialog if one is open.
     * The occult altar can open a dialog with options like "Ancient Magicks", "Lunar", etc.
     * Returns true if the widget was handled (regardless of success), false if no widget found.
     */
    private boolean handleSpellbookWidget(Rs2Spellbook requiredSpellbook) {
        // Check for a "Select an Option" dialog (standard game dialog)
        if (Rs2Dialogue.hasSelectAnOption()) {
            String dialogOption = getDialogOptionForSpellbook(requiredSpellbook);
            log.info("Spellbook selection dialog detected, clicking option: '{}'", dialogOption);
            Rs2Dialogue.clickOption(dialogOption);
            sleepUntil(() -> Rs2Magic.isSpellbook(requiredSpellbook), 3000);

            if (Rs2Magic.isSpellbook(requiredSpellbook)) {
                log.info("Successfully switched to {} spellbook via dialog", requiredSpellbook);
                finishSpellbookSwap();
            } else {
                log.warn("Dialog option clicked but spellbook didn't change");
            }
            return true;
        }

        // Check for widget-based spellbook selection (search by text)
        String widgetText = getWidgetTextForSpellbook(requiredSpellbook);
        Widget spellbookWidget = Rs2Widget.findWidget(widgetText);
        if (spellbookWidget != null) {
            log.info("Found spellbook widget with text '{}', clicking", widgetText);
            Rs2Widget.clickWidget(spellbookWidget);
            sleepUntil(() -> Rs2Magic.isSpellbook(requiredSpellbook), 3000);

            if (Rs2Magic.isSpellbook(requiredSpellbook)) {
                log.info("Successfully switched to {} spellbook via widget", requiredSpellbook);
                finishSpellbookSwap();
            } else {
                log.warn("Widget clicked but spellbook didn't change");
            }
            return true;
        }

        return false;
    }

    /**
     * Completes the spellbook swap by leaving POH and transitioning to next state.
     */
    private void finishSpellbookSwap() {
        spellbookSwapAttempts = 0;

        // Set up autocast while stationary at POH (before leaving house)
        setupAutoCastIfNeeded();

        leaveHouse();
        sleepUntil(() -> !isInPoh(), 3000);
        resetPohState();
        currentInventorySetup = null;

        if (config.enableAutoTravel()) {
            SlayerPlugin.setState(SlayerState.TRAVELING);
        } else {
            SlayerPlugin.setState(SlayerState.DETECTING_TASK);
        }
    }

    /**
     * Gets the occult altar right-click menu option for the given spellbook.
     */
    private String getAltarOptionForSpellbook(Rs2Spellbook spellbook) {
        switch (spellbook) {
            case ANCIENT:
                return "Ancient Magicks";
            case LUNAR:
                return "Lunar";
            case MODERN:
            default:
                return "Standard";
        }
    }

    /**
     * Gets the dialog option text for selecting a spellbook in a selection dialog.
     * Uses partial match so it works with Rs2Dialogue.clickOption().
     */
    private String getDialogOptionForSpellbook(Rs2Spellbook spellbook) {
        switch (spellbook) {
            case ANCIENT:
                return "Ancient";
            case LUNAR:
                return "Lunar";
            case MODERN:
            default:
                return "Standard";
        }
    }

    /**
     * Gets the widget text to search for when clicking a spellbook option in a widget.
     */
    private String getWidgetTextForSpellbook(Rs2Spellbook spellbook) {
        switch (spellbook) {
            case ANCIENT:
                return "Ancient Magicks";
            case LUNAR:
                return "Lunar";
            case MODERN:
            default:
                return "Standard";
        }
    }

    /**
     * Finds the occult altar in the POH.
     */
    private TileObject findOccultAltar() {
        for (int altarId : OCCULT_ALTAR_IDS) {
            TileObject altar = Rs2GameObject.findObjectById(altarId);
            if (altar != null) {
                return altar;
            }
        }
        return null;
    }

    private void handleTravelingState() {
        // Dismiss any open dialogue first
        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                sleep(300, 600);
            } else {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                sleep(300, 600);
            }
            return;
        }

        if (taskDestination == null) {
            log.warn("No destination set, returning to DETECTING_TASK");
            SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            return;
        }

        // Check if we've arrived
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && playerLocation.distanceTo(taskDestination) <= ARRIVAL_DISTANCE) {
            log.info("Arrived at task location: {}", taskLocationName);
            SlayerPlugin.setState(SlayerState.AT_LOCATION);
            return;
        }

        // Walk to destination if not already moving
        if (!Rs2Player.isMoving()) {
            log.info("Walking to task location: {}", taskLocationName);
            Rs2Walker.walkTo(taskDestination);
        }
    }

    private void handleAtLocationState(boolean hasTask, int remaining) {
        if (!hasTask || remaining <= 0) {
            log.info("Task complete or no task");
            // Pick up cannon if placed
            if (cannonPlaced) {
                log.info("Picking up cannon after task completion");
                if (pickupCannon()) {
                    sleepUntil(() -> !isCannonPlacedNearby(), 3000);
                    cannonPlaced = false;
                    cannonFired = false;
                }
            }
            resetTravelData();
            initialSetupDone = false;

            // Use POH after task if enabled
            if (config.usePohPool() && config.usePohAfterTask() && shouldUsePoh()) {
                log.info("Task complete, restoring at POH before getting new task");
                stateAfterPoh = config.getNewTask() ? SlayerState.GETTING_TASK : SlayerState.IDLE;
                SlayerPlugin.setState(SlayerState.RESTORING_AT_POH);
                return;
            }

            if (config.getNewTask()) {
                log.info("Transitioning to GETTING_TASK for new assignment");
                SlayerPlugin.setState(SlayerState.GETTING_TASK);
            } else {
                SlayerPlugin.setState(SlayerState.IDLE);
            }
            return;
        }

        // Place cannon if using cannon and not yet placed
        if (isUsingCannon && !cannonPlaced && cannonSpot != null) {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation != null && playerLocation.distanceTo(cannonSpot) <= 2) {
                if (hasCannonParts()) {
                    log.info("Placing cannon at {}", cannonSpot);
                    if (setupCannon()) {
                        // Wait for cannon assembly animation to complete (takes several seconds)
                        log.info("Waiting for cannon assembly...");
                        sleep(6000, 7000); // Cannon assembly takes about 6 seconds

                        // Assume cannon is placed if we got this far
                        cannonPlaced = true;
                        log.info("Cannon assembly complete, attempting to load...");

                        // Try to load/fire the cannon
                        boolean refillResult = Rs2Cannon.refill();
                        log.info("Rs2Cannon.refill() returned: {}", refillResult);
                        if (refillResult) {
                            log.info("Cannon loaded and firing!");
                            cannonFired = true;
                        } else {
                            log.info("Initial refill returned false - will retry via maintenance");
                        }
                        return;
                    } else {
                        log.warn("setupCannon() returned false - couldn't interact with cannon base");
                    }
                } else {
                    log.warn("Missing cannon parts, cannot place cannon");
                    isUsingCannon = false;
                }
            } else {
                // Walk closer to cannon spot
                log.debug("Walking to cannon spot, distance: {}", playerLocation != null ? playerLocation.distanceTo(cannonSpot) : "unknown");
                if (!Rs2Player.isMoving()) {
                    Rs2Walker.walkTo(cannonSpot);
                }
                return;
            }
        }

        // If cannon is placed but not loaded yet, try to load it
        if (cannonPlaced && !cannonFired) {
            log.info("Cannon placed but not loaded, attempting Rs2Cannon.refill()...");
            boolean refillResult = Rs2Cannon.refill();
            if (refillResult) {
                log.info("Cannon loaded successfully!");
                cannonFired = true;
            } else {
                log.debug("Rs2Cannon.refill() returned false - cannon may need more time or already firing");
            }
            return; // Give cannon priority before combat
        }

        // Check if we should disable cannon due to low cannonballs
        // This will pick up cannon, disable cannon mode, and reset destination
        if (checkAndDisableCannonIfLow()) {
            // Re-detect task to get new location and setup without cannon
            log.info("Cannon disabled, transitioning to DETECTING_TASK to reconfigure");
            SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            return;
        }

        // Check if we should use POH for restoration (before banking)
        if (config.usePohPool() && config.usePohBeforeBanking() && needsPohRestoration()) {
            log.info("Low HP, restoring at POH");
            stateAfterPoh = SlayerState.AT_LOCATION; // Return to location after POH
            SlayerPlugin.setState(SlayerState.RESTORING_AT_POH);
            return;
        }

        // Check if we need to bank for supplies
        if (config.enableAutoBanking() && needsBanking()) {
            // Pick up cannon before leaving - it can't be picked up from the bank
            if (cannonPlaced) {
                log.info("Picking up cannon before banking (at location)");
                if (!handleCannonPickup()) {
                    return; // Cannon pickup in progress
                }
                cannonPlaced = false;
                cannonFired = false;
            }
            // Go to POH first to heal before banking (saves supplies)
            if (config.usePohPool() && shouldUsePoh()) {
                log.info("Low on supplies - healing at POH before banking");
                stateAfterPoh = SlayerState.BANKING;
                SlayerPlugin.setState(SlayerState.RESTORING_AT_POH);
            } else {
                log.info("Low on supplies, transitioning to BANKING");
                SlayerPlugin.setState(SlayerState.BANKING);
            }
            return;
        }

        // Set up autocast before entering combat (player is stationary at task location)
        setupAutoCastIfNeeded();

        // Transition to fighting if auto combat is enabled
        if (config.enableAutoCombat()) {
            SlayerPlugin.setState(SlayerState.FIGHTING);
        }
    }

    private void handleFightingState(boolean hasTask, int remaining) {
        // Check if task is complete
        if (!hasTask || remaining <= 0) {
            // Start the loot delay timer if not already started
            if (!taskCompletedLooting) {
                log.info("Task complete - looting for {} seconds before transitioning",
                        TASK_COMPLETE_LOOT_DELAY_MS / 1000);
                taskCompletedLooting = true;
                taskCompletedTime = System.currentTimeMillis();
                deactivateProfilePrayer();
            }

            // Continue looting during the delay period
            long timeSinceComplete = System.currentTimeMillis() - taskCompletedTime;
            if (timeSinceComplete < TASK_COMPLETE_LOOT_DELAY_MS) {
                // Handle looting during the delay
                if (config.enableLooting()) {
                    handleLooting();
                }
                return;
            }

            // Loot delay complete, now transition
            log.info("Loot delay complete, transitioning");
            taskCompletedLooting = false;

            // Pick up cannon if placed - must complete before transitioning
            if (cannonPlaced) {
                if (!handleCannonPickup()) {
                    // Cannon pickup in progress, don't transition yet
                    return;
                }
                // Cannon picked up successfully
                cannonPlaced = false;
                cannonFired = false;
            }

            resetTravelData();
            initialSetupDone = false;

            // Use POH after task if enabled
            if (config.usePohPool() && config.usePohAfterTask() && shouldUsePoh()) {
                log.info("Task complete, restoring at POH before getting new task");
                stateAfterPoh = config.getNewTask() ? SlayerState.GETTING_TASK : SlayerState.IDLE;
                SlayerPlugin.setState(SlayerState.RESTORING_AT_POH);
                return;
            }

            if (config.getNewTask()) {
                log.info("Transitioning to GETTING_TASK for new assignment");
                SlayerPlugin.setState(SlayerState.GETTING_TASK);
            } else {
                SlayerPlugin.setState(SlayerState.IDLE);
            }
            return;
        }

        // Reset task completed flag if task is not complete (e.g., got a new task)
        if (taskCompletedLooting) {
            taskCompletedLooting = false;
        }

        // IMMEDIATELY activate prayer when in FIGHTING state
        // This ensures prayer is on as soon as we enter combat area
        activateProfilePrayer();

        // Ensure flicker script has the correct prayer from profile
        if (activeJsonProfile != null && activeJsonProfile.hasPrayer()) {
            SlayerPlugin.getFlickerScript().setActivePrayer(activeJsonProfile.getParsedPrayer());
        }

        // Handle drinking potions EARLY - before combat checks
        // This ensures super antifire, combat potions, etc. are active
        handlePotions();

        // Handle fungicide spray refill (for zygomite tasks)
        handleFungicideRefill();

        // Handle eating food BEFORE POH check
        // This ensures we eat food first, only use POH if still low HP after eating
        boolean ateFood = handleEating();

        // Handle cannon maintenance (repair and refill)
        if (cannonPlaced) {
            handleCannonMaintenance();
        }

        // Check if we should disable cannon due to low cannonballs
        // This will pick up cannon, disable cannon mode, and reset destination
        if (checkAndDisableCannonIfLow()) {
            // Re-detect task to get new location and setup without cannon
            log.info("Cannon disabled mid-task, transitioning to DETECTING_TASK to reconfigure");
            deactivateProfilePrayer();
            SlayerPlugin.setState(SlayerState.DETECTING_TASK);
            return;
        }

        // Check if we should use POH for restoration (before banking)
        // Skip POH if we just ate food - give it time to heal before teleporting
        // POH acts as a "panic teleport" if HP drops dangerously low even after eating
        if (!ateFood && config.usePohPool() && config.usePohBeforeBanking() && needsPohRestoration()) {
            log.info("Low HP - using POH as panic teleport");
            deactivateProfilePrayer();
            stateAfterPoh = SlayerState.TRAVELING; // Return to task after POH
            SlayerPlugin.setState(SlayerState.RESTORING_AT_POH);
            return;
        }

        // Check if we need to bank
        if (config.enableAutoBanking() && needsBanking()) {
            // Pick up cannon before leaving - it can't be picked up from the bank
            if (cannonPlaced) {
                log.info("Picking up cannon before banking");
                if (!handleCannonPickup()) {
                    return; // Cannon pickup in progress
                }
                cannonPlaced = false;
                cannonFired = false;
            }
            deactivateProfilePrayer();
            // Go to POH first to heal before banking (saves supplies)
            if (config.usePohPool() && shouldUsePoh()) {
                log.info("Low on supplies - healing at POH before banking");
                stateAfterPoh = SlayerState.BANKING;
                SlayerPlugin.setState(SlayerState.RESTORING_AT_POH);
            } else {
                log.info("Low on supplies, transitioning to BANKING");
                SlayerPlugin.setState(SlayerState.BANKING);
            }
            return;
        }

        // Handle looting (combat check is done inside handleLooting based on forceLoot setting)
        if (config.enableLooting()) {
            handleLooting();
        }

        // Handle combat
        handleCombat();
    }

    /**
     * Handles eating food when HP is low.
     * @return true if food was eaten, false otherwise
     */
    private boolean handleEating() {
        double healthPercent = Rs2Player.getHealthPercentage();
        if (healthPercent < config.eatAtHealthPercent()) {
            List<Rs2ItemModel> food = Rs2Inventory.getInventoryFood();
            if (!food.isEmpty()) {
                Rs2ItemModel foodItem = food.get(0);
                if (Rs2Inventory.interact(foodItem, "Eat")) {
                    log.info("Eating {} at {}% HP", foodItem.getName(), healthPercent);
                    return true;
                }
            }
        }
        return false;
    }

    private void handlePotions() {
        // Only drink one potion per tick - return after each successful drink

        // Handle prayer potions
        if (config.prayerFlickStyle() != PrayerFlickStyle.OFF || config.drinkPrayerAt() > 0) {
            int prayerPoints = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
            if (prayerPoints < config.drinkPrayerAt()) {
                Rs2ItemModel prayerPotion = Rs2Inventory.get(item ->
                        item != null && item.getName() != null &&
                        (item.getName().toLowerCase().contains("prayer potion") ||
                         item.getName().toLowerCase().contains("super restore")));
                if (prayerPotion != null) {
                    if (Rs2Inventory.interact(prayerPotion, "Drink")) {
                        log.info("Drinking {} at {} prayer points", prayerPotion.getName(), prayerPoints);
                        return; // One potion per tick
                    }
                }
            }
        }

        // Handle combat potions (super combat, divine, ranging, magic, etc.)
        if (config.useCombatPotions()) {
            // Check melee potions - uses Attack skill
            int attackLevel = Rs2Player.getRealSkillLevel(Skill.ATTACK);
            int boostedAttack = Rs2Player.getBoostedSkillLevel(Skill.ATTACK);
            if (boostedAttack <= attackLevel) {
                Rs2ItemModel meleePotion = Rs2Inventory.get(item ->
                        item != null && item.getName() != null &&
                        (item.getName().toLowerCase().contains("super combat") ||
                         item.getName().toLowerCase().contains("divine super combat") ||
                         item.getName().toLowerCase().contains("super attack")));
                if (meleePotion != null) {
                    if (Rs2Inventory.interact(meleePotion, "Drink")) {
                        log.info("Drinking {} (attack boost worn off)", meleePotion.getName());
                        return; // One potion per tick
                    }
                }
            }

            // Check ranging potions - uses Ranged skill
            int rangedLevel = Rs2Player.getRealSkillLevel(Skill.RANGED);
            int boostedRanged = Rs2Player.getBoostedSkillLevel(Skill.RANGED);
            if (boostedRanged <= rangedLevel) {
                Rs2ItemModel rangingPotion = Rs2Inventory.get(item ->
                        item != null && item.getName() != null &&
                        (item.getName().toLowerCase().contains("ranging potion") ||
                         item.getName().toLowerCase().contains("divine ranging") ||
                         item.getName().toLowerCase().contains("bastion potion") ||
                         item.getName().toLowerCase().contains("divine bastion")));
                if (rangingPotion != null) {
                    if (Rs2Inventory.interact(rangingPotion, "Drink")) {
                        log.info("Drinking {} (ranged boost worn off)", rangingPotion.getName());
                        return; // One potion per tick
                    }
                }
            }

            // Check magic potions - uses Magic skill
            int magicLevel = Rs2Player.getRealSkillLevel(Skill.MAGIC);
            int boostedMagic = Rs2Player.getBoostedSkillLevel(Skill.MAGIC);
            if (boostedMagic <= magicLevel) {
                Rs2ItemModel magicPotion = Rs2Inventory.get(item ->
                        item != null && item.getName() != null &&
                        (item.getName().toLowerCase().contains("magic potion") ||
                         item.getName().toLowerCase().contains("divine magic") ||
                         item.getName().toLowerCase().contains("battlemage potion") ||
                         item.getName().toLowerCase().contains("divine battlemage")));
                if (magicPotion != null) {
                    if (Rs2Inventory.interact(magicPotion, "Drink")) {
                        log.info("Drinking {} (magic boost worn off)", magicPotion.getName());
                        return; // One potion per tick
                    }
                }
            }
        }

        // Handle antipoison when poisoned - check poison status first
        // Poison varp: 0 = not poisoned, >0 and <1000000 = poisoned, >1000000 = venomed
        int poisonVarp = Microbot.getClient().getVarpValue(102);
        if (poisonVarp > 0 && poisonVarp < 1000000) {
            // Actually poisoned - try to cure
            if (Rs2Player.drinkAntiPoisonPotion()) {
                log.info("Drank antipoison (poison varp={})", poisonVarp);
                return; // One potion per tick
            } else {
                // Fallback: manually check for poison and drink antidote
                if (handleAntipoison()) {
                    return; // One potion per tick
                }
            }
        }

        // Handle antivenom when venomed (poison varp > 1000000)
        if (poisonVarp > 1000000) {
            if (handleAntivenom()) {
                return; // One potion per tick
            }
        }

        // Handle goading potions (from JSON profile) - for burst/barrage tasks
        // Rs2Player.drinkGoadingPotion() checks if goaded buff has worn off
        if (activeJsonProfile != null && activeJsonProfile.shouldUseGoading()) {
            if (Rs2Player.drinkGoadingPotion()) {
                log.info("Drank goading potion (task profile requires goading)");
                return; // One potion per tick
            }
        }

        // Handle super antifire (from JSON profile) - for dragon tasks
        if (activeJsonProfile != null && activeJsonProfile.isSuperAntifire()) {
            handleSuperAntifire();
        }
    }

    /**
     * Handles refilling the fungicide spray during zygomite tasks.
     * The auto-spray slayer perk consumes charges automatically, so we just
     * need to use fungicide on the spray when charges get low.
     */
    private void handleFungicideRefill() {
        // Find fungicide spray in inventory
        Rs2ItemModel spray = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().startsWith("fungicide spray"));

        if (spray == null) {
            return; // No spray in inventory, not a zygomite task
        }

        // Parse charge count from name (e.g., "Fungicide spray 5")
        int charges = parseFungicideCharges(spray.getName());
        if (charges > 2) {
            return; // Spray still has enough charges
        }

        // Find fungicide refill in inventory
        Rs2ItemModel fungicide = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().equalsIgnoreCase("fungicide"));

        if (fungicide == null) {
            log.debug("Fungicide spray low ({} charges) but no fungicide refills in inventory", charges);
            return;
        }

        log.info("Refilling fungicide spray ({} charges)", charges);
        Rs2Inventory.combine(fungicide, spray);
        sleep(600, 900);
    }

    /**
     * Parses the charge count from a fungicide spray item name.
     * Expected format: "Fungicide spray X" where X is 0-10.
     */
    private int parseFungicideCharges(String name) {
        if (name == null) return -1;
        try {
            String[] parts = name.split(" ");
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Varbit for antifire protection.
     * 0 = no protection, >0 = protected (ticks remaining)
     */
    private static final int ANTIFIRE_VARBIT = 3981;
    private static final int SUPER_ANTIFIRE_VARBIT = 6101;

    /**
     * Handles drinking super antifire potions when protection wears off.
     * Checks the antifire varbit to see if we still have protection.
     */
    private void handleSuperAntifire() {
        // Check if we have super antifire protection active
        int superAntifireTimer = Microbot.getVarbitValue(SUPER_ANTIFIRE_VARBIT);
        int regularAntifireTimer = Microbot.getVarbitValue(ANTIFIRE_VARBIT);

        // If we have protection, no need to drink
        if (superAntifireTimer > 0 || regularAntifireTimer > 0) {
            return;
        }

        // Try extended super antifire first (longer duration)
        Rs2ItemModel extendedSuperAntifire = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("extended super antifire"));

        if (extendedSuperAntifire != null) {
            if (Rs2Inventory.interact(extendedSuperAntifire, "Drink")) {
                log.info("Drinking {} for dragon protection", extendedSuperAntifire.getName());
            }
            return;
        }

        // Fall back to regular super antifire
        Rs2ItemModel superAntifire = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("super antifire") &&
                !item.getName().toLowerCase().contains("extended"));

        if (superAntifire != null) {
            if (Rs2Inventory.interact(superAntifire, "Drink")) {
                log.info("Drinking {} for dragon protection", superAntifire.getName());
            }
            return;
        }

        // Fall back to regular antifire if no super antifire available
        Rs2ItemModel antifire = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                (item.getName().toLowerCase().contains("antifire") ||
                 item.getName().toLowerCase().contains("anti-fire")) &&
                !item.getName().toLowerCase().contains("super"));

        if (antifire != null) {
            if (Rs2Inventory.interact(antifire, "Drink")) {
                log.info("Drinking {} for dragon protection (no super antifire)", antifire.getName());
            }
        } else {
            log.warn("No antifire potions in inventory for dragon task!");
        }
    }

    /**
     * Handles drinking antivenom when envenomed.
     * Uses poison varbit value > 1000000 to detect venom (poison is < 1000000).
     * Supports antivenom, antivenom+, and anti-venom+ potions.
     * @return true if a potion was drunk, false otherwise
     */
    private boolean handleAntivenom() {
        // Check if envenomed - venom is poison varbit > 1000000
        int poisonVarp = Microbot.getClient().getVarpValue(102); // VarPlayerID.POISON
        if (poisonVarp <= 1000000) {
            // Not venomed - if still poisoned, let antipoison handle it
            return false;
        }

        // Try antivenom potions first
        Rs2ItemModel antivenomPotion = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                (item.getName().toLowerCase().contains("anti-venom") ||
                 item.getName().toLowerCase().contains("antivenom")));

        if (antivenomPotion != null) {
            if (Rs2Inventory.interact(antivenomPotion, "Drink")) {
                log.info("Drinking {} (venomed)", antivenomPotion.getName());
                return true;
            }
        } else {
            // Antidote++ can provide some venom protection
            Rs2ItemModel antidotePotion = Rs2Inventory.get(item ->
                    item != null && item.getName() != null &&
                    item.getName().toLowerCase().contains("antidote++"));

            if (antidotePotion != null) {
                if (Rs2Inventory.interact(antidotePotion, "Drink")) {
                    log.info("Drinking {} (venomed, no antivenom)", antidotePotion.getName());
                    return true;
                }
            } else {
                log.warn("Venomed but no antivenom potions in inventory!");
            }
        }
        return false;
    }

    /**
     * Varbit for poison status.
     * 0 = not poisoned, >0 = poisoned (damage value)
     * Values > 1000000 indicate venom (handled by handleAntivenom)
     */
    private static final int POISON_VARP = 102;

    /**
     * Handles drinking antipoison/antidote potions when poisoned.
     * This is a fallback if Rs2Player.drinkAntiPoisonPotion() doesn't work.
     * @return true if a potion was drunk, false otherwise
     */
    private boolean handleAntipoison() {
        // Check if poisoned (but not venomed - venom is > 1000000)
        int poisonVarp = Microbot.getClient().getVarpValue(POISON_VARP);
        if (poisonVarp <= 0 || poisonVarp > 1000000) {
            // Not poisoned (or venomed, which is handled separately)
            return false;
        }

        log.info("Poisoned (varp={}), looking for antipoison/antidote", poisonVarp);

        // Try antidote++ first (best)
        Rs2ItemModel antidotePlusPlus = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("antidote++"));

        if (antidotePlusPlus != null) {
            if (Rs2Inventory.interact(antidotePlusPlus, "Drink")) {
                log.info("Drinking {} (poisoned)", antidotePlusPlus.getName());
                return true;
            }
            return false;
        }

        // Try antidote+
        Rs2ItemModel antidotePlus = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("antidote+") &&
                !item.getName().toLowerCase().contains("antidote++"));

        if (antidotePlus != null) {
            if (Rs2Inventory.interact(antidotePlus, "Drink")) {
                log.info("Drinking {} (poisoned)", antidotePlus.getName());
                return true;
            }
            return false;
        }

        // Try superantipoison
        Rs2ItemModel superAntipoison = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("superantipoison"));

        if (superAntipoison != null) {
            if (Rs2Inventory.interact(superAntipoison, "Drink")) {
                log.info("Drinking {} (poisoned)", superAntipoison.getName());
                return true;
            }
            return false;
        }

        // Try regular antipoison
        Rs2ItemModel antipoison = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("antipoison") &&
                !item.getName().toLowerCase().contains("super"));

        if (antipoison != null) {
            if (Rs2Inventory.interact(antipoison, "Drink")) {
                log.info("Drinking {} (poisoned)", antipoison.getName());
                return true;
            }
            return false;
        }

        // Try any antidote (generic)
        Rs2ItemModel antidote = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("antidote"));

        if (antidote != null) {
            if (Rs2Inventory.interact(antidote, "Drink")) {
                log.info("Drinking {} (poisoned)", antidote.getName());
                return true;
            }
            return false;
        }

        log.warn("Poisoned but no antipoison/antidote potions in inventory!");
        return false;
    }

    private static final int MIN_ARROW_STACK = 9;  // Loot 10+ arrows
    private static final int MIN_RUNE_STACK = 1;   // Loot 2+ runes

    private void handleLooting() {
        // Delay between looting to avoid spam
        if (System.currentTimeMillis() - lastLootTime < LOOT_DELAY_MS) {
            return;
        }

        // Don't loot if inventory is full (unless we can eat for space)
        if (Rs2Inventory.isFull() && !config.eatForLootSpace()) {
            return;
        }

        // Don't loot while in combat unless force loot is enabled
        if (Rs2Combat.inCombat() && !config.forceLoot()) {
            return;
        }

        // Parse exclusion list
        Set<String> excludedItems = parseItemList(config.lootExcludeList());

        // Build looting parameters
        int maxPrice = config.maxLootValue() > 0 ? config.maxLootValue() : Integer.MAX_VALUE;
        LootingParameters params = new LootingParameters(
                config.minLootValue(),
                maxPrice,
                config.attackRadius(),
                1,  // minQuantity
                1,  // minInvSlots
                config.delayedLooting(),
                config.onlyLootMyItems()
        );
        params.setEatFoodForSpace(config.eatForLootSpace());

        // Build the loot engine with exclusion filter
        Rs2LootEngine.Builder builder = Rs2LootEngine.with(params)
                .withLootAction(groundItem -> {
                    // Check exclusion list before looting
                    if (isItemExcluded(groundItem.getName(), excludedItems)) {
                        log.debug("Skipping excluded item: {}", groundItem.getName());
                        return; // Don't loot excluded items
                    }
                    Rs2GroundItem.coreLoot(groundItem);
                });

        // Add custom item list if using ITEM_LIST or MIXED style
        LootStyle style = config.lootStyle();
        if (style == LootStyle.ITEM_LIST || style == LootStyle.MIXED) {
            addCustomItemList(builder, config.lootItemList());
        }

        // Add GE price-based looting if using GE_PRICE_RANGE or MIXED style
        // Also add value filtering if minLootValue > 0 to ensure all looting respects the min value
        if (style == LootStyle.GE_PRICE_RANGE || style == LootStyle.MIXED || config.minLootValue() > 0) {
            builder.addByValue();
        }

        // Add specific loot types based on config
        if (config.lootBones() && config.buryBones()) {
            builder.addBones();  // This will also bury them
        } else if (config.lootBones()) {
            // Just loot bones without burying - add as custom filter
            addBonesWithoutBury(builder);
        }

        if (config.scatterAshes()) {
            builder.addAshes();
        }

        if (config.lootCoins()) {
            int minCoinStack = config.minCoinStack();
            if (minCoinStack > 0) {
                // Use custom filter with minimum stack size
                Predicate<GroundItem> coinFilter = gi -> {
                    if (gi.getName() == null) return false;
                    String name = gi.getName().toLowerCase();
                    return name.equals("coins") && gi.getQuantity() >= minCoinStack;
                };
                builder.addCustom("coins", coinFilter, null);
            } else {
                // Loot all coins
                builder.addCoins();
            }
        }

        if (config.lootUntradables()) {
            builder.addUntradables();
        }

        if (config.lootArrows()) {
            builder.addArrows(MIN_ARROW_STACK);
        }

        if (config.lootRunes()) {
            builder.addRunes(MIN_RUNE_STACK);
        }

        // Execute the looting pass
        boolean looted = builder.loot();
        if (looted) {
            lastLootTime = System.currentTimeMillis();
        }
    }

    /**
     * Parses the exclusion list from config into a set of lowercase item names.
     */
    private Set<String> parseItemList(String csvNames) {
        Set<String> items = new HashSet<>();
        if (csvNames == null || csvNames.trim().isEmpty()) {
            return items;
        }

        Arrays.stream(csvNames.split(","))
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .forEach(items::add);

        return items;
    }

    /**
     * Matches an item name against a pattern.
     * Uses exact matching by default. Supports wildcards (*) for partial matching.
     * Examples: "bones" matches only "bones", "*bones" matches "superior dragon bones",
     *           "dragon*" matches "dragon dagger", "*dragon*" matches "superior dragon bones".
     */
    private boolean matchesItemPattern(String itemName, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }

        // Exact match when no wildcards
        if (!pattern.contains("*")) {
            return itemName.equals(pattern);
        }

        // Convert wildcard pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("*", ".*");

        return itemName.matches(regex);
    }

    /**
     * Checks if an item name matches any entry in the exclusion list.
     * Uses exact matching by default. Use wildcards (*) for partial matching.
     */
    private boolean isItemExcluded(String itemName, Set<String> excludedItems) {
        if (itemName == null || excludedItems.isEmpty()) {
            return false;
        }

        String lowerName = itemName.trim().toLowerCase();
        for (String pattern : excludedItems) {
            if (matchesItemPattern(lowerName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds custom item names from the config to the loot engine.
     * Uses exact matching by default. Use wildcards (*) for partial matching.
     */
    private void addCustomItemList(Rs2LootEngine.Builder builder, String csvNames) {
        if (csvNames == null || csvNames.trim().isEmpty()) {
            return;
        }

        final Set<String> itemPatterns = parseItemList(csvNames);

        if (itemPatterns.isEmpty()) {
            return;
        }

        Predicate<GroundItem> byNames = gi -> {
            final String name = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
            for (String pattern : itemPatterns) {
                if (matchesItemPattern(name, pattern)) {
                    return true;
                }
            }
            return false;
        };

        builder.addCustom("itemList", byNames, null);
    }

    /**
     * Adds bones to the loot engine without the auto-bury feature.
     */
    private void addBonesWithoutBury(Rs2LootEngine.Builder builder) {
        Predicate<GroundItem> isBones = gi -> {
            final String name = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
            return name.contains("bones") && !name.contains("bonemeal");
        };

        builder.addCustom("bones", isBones, null);
    }

    private void handleCombat() {
        // Check if we're using AoE burst/barrage combat style
        if (activeJsonProfile != null && activeJsonProfile.isAoeStyle()) {
            handleAoeCombat();
            return;
        }

        // Get target monster names (use variant if specified, otherwise use slayer task monsters)
        List<String> targetMonsters = getTargetMonsterNames();

        // Log target monsters for debugging
        if (targetMonsters == null || targetMonsters.isEmpty()) {
            log.warn("No target monsters found for current task. Task: {}, Profile: {}",
                    Rs2Slayer.getSlayerTask(),
                    activeJsonProfile != null ? "found" : "null");

            // Fallback: try to attack anything that's attacking us ONLY at task location
            if (taskDestination != null) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation != null && playerLocation.distanceTo(taskDestination) <= config.attackRadius()) {
                    Rs2NpcModel anyAttacker = findAnyNpcAttackingUs();
                    if (anyAttacker != null) {
                        log.info("Fallback: Attacking {} that is attacking us (at task location)", anyAttacker.getName());
                        if (Rs2Npc.interact(anyAttacker, "Attack")) {
                            sleepUntil(Rs2Player::isInteracting, 1000);
                        }
                    }
                }
            }
            return;
        }

        log.debug("Target monsters: {}", targetMonsters);

        // Check for superiors first - they take priority over everything
        if (config.prioritizeSuperiors()) {
            Rs2NpcModel superior = findNearbySuperior();
            if (superior != null) {
                log.info("Superior monster detected: {}! Attacking.", superior.getName());
                if (Rs2Npc.interact(superior, "Attack")) {
                    sleepUntil(Rs2Player::isInteracting, 1000);
                }
                return;
            }
        }

        // Check if we're already in combat with a living target
        Actor currentInteracting = Rs2Player.getInteracting();
        if (currentInteracting != null) {
            // Verify the target is actually alive - don't idle on dead NPCs
            boolean targetAlive = true;
            if (currentInteracting instanceof Rs2NpcModel) {
                Rs2NpcModel npc = (Rs2NpcModel) currentInteracting;
                targetAlive = !npc.isDead() && npc.getHealthRatio() != 0;
            }
            if (targetAlive) {
                log.debug("In combat with: {}", currentInteracting.getName());
                return;
            }
            log.debug("Current target {} is dead, finding new target", currentInteracting.getName());
        }

        // Check if we're being attacked but not fighting back
        boolean beingAttacked = isBeingAttacked();
        if (beingAttacked) {
            // First try to find attacker from target list
            Rs2NpcModel attacker = findNpcAttackingUs(targetMonsters);
            if (attacker != null) {
                log.info("Retaliating against target monster: {}", attacker.getName());
                if (Rs2Npc.interact(attacker, "Attack")) {
                    sleepUntil(Rs2Player::isInteracting, 1000);
                }
                return;
            }

            // Fallback: attack ANY NPC that is attacking us ONLY if we're at the task location
            if (taskDestination != null) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation != null && playerLocation.distanceTo(taskDestination) <= config.attackRadius()) {
                    Rs2NpcModel anyAttacker = findAnyNpcAttackingUs();
                    if (anyAttacker != null) {
                        log.info("Fallback: Retaliating against {} (at task location, not in target list)", anyAttacker.getName());
                        if (Rs2Npc.interact(anyAttacker, "Attack")) {
                            sleepUntil(Rs2Player::isInteracting, 1000);
                        }
                        return;
                    }
                }
            }
            // No attacker found, fall through to find new targets proactively
        }

        // Find attackable NPCs matching target monsters
        // Prioritize NPCs that are already attacking us (interacting with player)
        List<Rs2NpcModel> attackableNpcs = Rs2Npc.getAttackableNpcs(true)
                .filter(npc -> npc.getName() != null)
                .filter(npc -> targetMonsters.stream()
                        .anyMatch(monster -> matchesTargetMonster(npc.getName(), monster)))
                .filter(npc -> taskDestination == null ||
                        npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                .sorted(Comparator
                        // First priority: NPCs already attacking us (interacting with player)
                        .comparingInt((Rs2NpcModel npc) ->
                                npc.getInteracting() == Microbot.getClient().getLocalPlayer() ? 0 : 1)
                        // Second priority: closest distance
                        .thenComparingInt(npc ->
                                Rs2Player.getWorldLocation().distanceTo(npc.getWorldLocation())))
                .collect(Collectors.toList());

        if (attackableNpcs.isEmpty()) {
            log.debug("No attackable slayer monsters found nearby matching: {}", targetMonsters);

            // Debug: log nearby NPCs to help diagnose
            List<String> nearbyNpcNames = Rs2Npc.getAttackableNpcs(true)
                    .filter(npc -> npc.getName() != null)
                    .filter(npc -> taskDestination == null ||
                            npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                    .map(Rs2NpcModel::getName)
                    .distinct()
                    .collect(Collectors.toList());
            if (!nearbyNpcNames.isEmpty()) {
                log.info("Nearby attackable NPCs: {} (looking for: {})", nearbyNpcNames, targetMonsters);
            }

            // Crash detection - check if we've been unable to find targets for too long
            if (config.hopWhenCrashed() && taskDestination != null) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                boolean atTaskLocation = playerLocation != null &&
                        playerLocation.distanceTo(taskDestination) <= config.attackRadius();

                if (atTaskLocation) {
                    // Start or check crash detection timer
                    if (!isSearchingForTargets) {
                        isSearchingForTargets = true;
                        crashDetectionStartTime = System.currentTimeMillis();
                        log.debug("Started crash detection timer at task location");
                    } else {
                        long timeSinceStart = System.currentTimeMillis() - crashDetectionStartTime;
                        if (timeSinceStart >= CRASH_HOP_DELAY_MS) {
                            // Check cooldown
                            long timeSinceLastHop = System.currentTimeMillis() - lastWorldHopTime;
                            if (timeSinceLastHop >= WORLD_HOP_COOLDOWN_MS) {
                                log.info("No targets found for {} seconds - hopping worlds (likely crashed)",
                                        CRASH_HOP_DELAY_MS / 1000);
                                hopToNewWorld();
                                return;
                            } else {
                                log.debug("Would hop but on cooldown ({} seconds remaining)",
                                        (WORLD_HOP_COOLDOWN_MS - timeSinceLastHop) / 1000);
                            }
                        }
                    }
                }
            }
            return;
        }

        // Found targets - reset crash detection timer
        if (isSearchingForTargets) {
            isSearchingForTargets = false;
            crashDetectionStartTime = 0;
            log.debug("Reset crash detection timer - found targets");
        }

        // Attack the first NPC (prioritizes those attacking us, then closest)
        Rs2NpcModel target = attackableNpcs.get(0);
        if (Rs2Npc.interact(target, "Attack")) {
            log.info("Attacking {}", target.getName());
            sleepUntil(Rs2Player::isInteracting, 1000);
        }
    }

    /**
     * Hops to a new world to avoid crashed spots.
     * Uses the configured world list if provided, otherwise picks a random members world.
     */
    private void hopToNewWorld() {
        try {
            // Disable prayers before hopping
            deactivateProfilePrayer();
            deactivateOffensivePrayers();

            // Get world from config list or random
            int world = getHopWorld();
            if (world <= 0) {
                log.warn("Could not determine a valid world to hop to");
                return;
            }
            log.info("Hopping to world {}", world);

            Microbot.hopToWorld(world);

            // Wait for switch world confirmation dialog
            sleepUntil(() -> Rs2Widget.findWidget("Switch World") != null, 5000);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

            // Wait for hop to complete
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING, 5000);
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 10000);

            // Brief pause after hopping
            sleep(1500, 2500);

            // Reset crash detection state
            isSearchingForTargets = false;
            crashDetectionStartTime = 0;
            lastWorldHopTime = System.currentTimeMillis();

            log.info("Successfully hopped to world {}", world);
        } catch (Exception e) {
            log.error("Error hopping worlds: {}", e.getMessage());
            // Reset state even on error
            isSearchingForTargets = false;
            crashDetectionStartTime = 0;
        }
    }

    /**
     * Gets a world to hop to from the config list, or a random members world if list is empty.
     * Avoids hopping to the current world.
     */
    private int getHopWorld() {
        String worldList = config.hopWorldList();
        int currentWorld = Microbot.getClient().getWorld();

        if (worldList != null && !worldList.trim().isEmpty()) {
            // Parse comma-separated world list
            List<Integer> worlds = Arrays.stream(worldList.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid world number in hop list: {}", s);
                            return -1;
                        }
                    })
                    .filter(w -> w > 0 && w != currentWorld)
                    .collect(Collectors.toList());

            if (!worlds.isEmpty()) {
                int selectedWorld = worlds.get(new Random().nextInt(worlds.size()));
                log.debug("Selected world {} from config list (available: {})", selectedWorld, worlds);
                return selectedWorld;
            } else {
                log.warn("No valid worlds in hop list (or all filtered out), falling back to random");
            }
        }

        // Fallback to random members world
        return Login.getRandomWorld(true, null);
    }

    /**
     * Checks if any NPC is currently attacking the player.
     */
    private boolean isBeingAttacked() {
        return Rs2Npc.getNpcsForPlayer()
                .anyMatch(npc -> npc.getInteracting() == Microbot.getClient().getLocalPlayer());
    }

    /**
     * Finds an NPC from the target list that is currently attacking the player.
     * Used when we're "in combat" (being attacked) but not attacking back.
     */
    private Rs2NpcModel findNpcAttackingUs(List<String> targetMonsters) {
        return Rs2Npc.getNpcsForPlayer()
                .filter(npc -> npc.getName() != null)
                .filter(npc -> npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .filter(npc -> targetMonsters.stream()
                        .anyMatch(monster -> matchesTargetMonster(npc.getName(), monster)))
                .filter(npc -> taskDestination == null ||
                        npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius() + 5)
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds ANY NPC that is currently attacking the player (regardless of target list).
     * Used as a fallback when variant/target list doesn't match.
     */
    private Rs2NpcModel findAnyNpcAttackingUs() {
        return Rs2Npc.getNpcsForPlayer()
                .filter(npc -> npc.getName() != null)
                .filter(npc -> npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .filter(npc -> !npc.isDead())
                .findFirst()
                .orElse(null);
    }

    /**
     * Handles AoE combat using burst/barrage spells.
     * Waits for monsters to stack using goading, then casts the appropriate spell.
     */
    private void handleAoeCombat() {
        // Ensure we have a valid profile for AoE combat
        if (activeJsonProfile == null) {
            log.warn("No active profile for AoE combat");
            return;
        }

        // Get the combat style and min stack size from profile
        SlayerCombatStyle style = activeJsonProfile.getParsedStyle();
        if (style == null) {
            log.warn("No combat style defined in profile for AoE combat");
            return;
        }

        // Determine which spell to use based on style
        Rs2CombatSpells spell = (style == SlayerCombatStyle.BARRAGE)
                ? Rs2CombatSpells.ICE_BARRAGE
                : Rs2CombatSpells.ICE_BURST;

        // Safety check: re-set autocast if it was cleared (e.g., weapon swap mid-combat)
        if (Rs2Magic.getCurrentAutoCastSpell() != spell) {
            log.info("Autocast was reset, re-setting to {}", spell.name());
            Rs2Combat.setAutoCastSpell(spell, false);
            sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == spell, 3000);
            return;
        }

        int minStackSize = activeJsonProfile.getMinStackSize();
        if (minStackSize < 1) {
            minStackSize = 3; // Default minimum
        }

        // Activate protection prayer
        activateProfilePrayer();

        // If already in combat with a valid target, let autocast handle it
        if (Rs2Player.isInteracting()) {
            Actor currentTarget = Rs2Player.getInteracting();
            if (currentTarget != null && !currentTarget.isDead()) {
                log.debug("Already in combat with {} - autocast will continue", currentTarget.getName());
                return;
            }
        }

        // Get target monster names (use variant if specified)
        List<String> targetMonsters = getTargetMonsterNames();
        if (targetMonsters == null || targetMonsters.isEmpty()) {
            log.warn("No target monsters found for current task");
            return;
        }

        // Find all nearby target monsters (including those already in combat with us)
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        List<Rs2NpcModel> nearbyMonsters = Rs2Npc.getNpcsForPlayer()
                .filter(npc -> npc.getName() != null)
                .filter(npc -> targetMonsters.stream()
                        .anyMatch(monster -> npc.getName().equalsIgnoreCase(monster)))
                .filter(npc -> !npc.isDead())
                .filter(npc -> taskDestination == null ||
                        npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                .collect(Collectors.toList());

        if (nearbyMonsters.isEmpty()) {
            log.debug("No target monsters found nearby for AoE combat");
            return;
        }

        // Count monsters within AoE range (3x3 for burst, 5x5 for barrage centered on target)
        int aoeRange = (style == SlayerCombatStyle.BARRAGE) ? 2 : 1;

        // Find the best target (one with most monsters stacked nearby)
        Rs2NpcModel bestTarget = null;
        int maxStackedCount = 0;

        for (Rs2NpcModel potentialTarget : nearbyMonsters) {
            WorldPoint targetLoc = potentialTarget.getWorldLocation();
            int stackedCount = 0;

            for (Rs2NpcModel monster : nearbyMonsters) {
                WorldPoint monsterLoc = monster.getWorldLocation();
                // Check if within AoE range of the potential target
                if (Math.abs(targetLoc.getX() - monsterLoc.getX()) <= aoeRange &&
                    Math.abs(targetLoc.getY() - monsterLoc.getY()) <= aoeRange &&
                    targetLoc.getPlane() == monsterLoc.getPlane()) {
                    stackedCount++;
                }
            }

            if (stackedCount > maxStackedCount) {
                maxStackedCount = stackedCount;
                bestTarget = potentialTarget;
            }
        }

        // Check if we have enough monsters stacked
        if (maxStackedCount < minStackSize) {
            // If using goading, we need to attack first to trigger the aggro effect
            // Goading works by attracting NPCs when you're already in combat
            if (activeJsonProfile.shouldUseGoading() && bestTarget != null) {
                log.info("Attacking {} to trigger goading aggro ({} monsters nearby)",
                        bestTarget.getName(), nearbyMonsters.size());
                if (Rs2Npc.interact(bestTarget, "Attack")) {
                    sleepUntil(Rs2Player::isInteracting, 1000);
                }
                return;
            }

            log.debug("Waiting for monsters to stack: {}/{} (goading active: {})",
                    maxStackedCount, minStackSize, activeJsonProfile.shouldUseGoading());
            // Just wait for more monsters to come
            return;
        }

        if (bestTarget == null) {
            log.debug("No suitable target found for AoE combat");
            return;
        }

        // Attack the best target - autocast will handle spell casting
        log.info("Attacking {} with autocast {} ({} monsters stacked)",
                bestTarget.getName(), spell.name(), maxStackedCount);

        if (Rs2Npc.interact(bestTarget, "Attack")) {
            sleepUntil(Rs2Player::isInteracting, 1000);
        }
    }

    /**
     * Gets the list of monster names to target.
     * If a variant is specified in the JSON profile, uses that instead of the slayer task monsters.
     * This allows targeting specific monsters like "rune dragons" when task is "metal dragons".
     */
    private List<String> getTargetMonsterNames() {
        // Check if profile specifies a variant
        if (activeJsonProfile != null && activeJsonProfile.hasVariant()) {
            String variant = activeJsonProfile.getVariant();
            log.debug("Using variant monster: {}", variant);
            return Arrays.asList(variant);
        }

        // Try standard slayer monster list first
        List<String> monsters = Rs2Slayer.getSlayerMonsters();
        if (monsters != null && !monsters.isEmpty()) {
            return monsters;
        }

        // Fallback: convert task name from plural to singular
        // e.g., "Frost Dragons" -> "Frost dragon"
        String taskName = Rs2Slayer.getSlayerTask();
        if (taskName != null && !taskName.isEmpty()) {
            String singularName = convertTaskNameToNpcName(taskName);
            log.debug("Using fallback NPC name: {} (from task: {})", singularName, taskName);
            return Arrays.asList(singularName);
        }

        return null;
    }

    /**
     * Returns true if using a specific variant from the profile (exact match required).
     */
    private boolean isUsingVariant() {
        return activeJsonProfile != null && activeJsonProfile.hasVariant();
    }

    /**
     * Checks if an NPC name matches the target monster name.
     * Uses exact matching for variants, broader matching for general task names.
     */
    private boolean matchesTargetMonster(String npcName, String targetMonster) {
        if (npcName == null || targetMonster == null) {
            return false;
        }
        String npcLower = npcName.toLowerCase();
        String targetLower = targetMonster.toLowerCase();

        if (isUsingVariant()) {
            // Exact match for variants (e.g., "baby blue dragon" should NOT match "blue dragon")
            return npcLower.equals(targetLower);
        } else {
            // Broader match for general task names
            // e.g., task "blue dragons" with monster "blue dragon" should match NPC "Blue dragon"
            return npcLower.contains(targetLower) || targetLower.contains(npcLower);
        }
    }

    /**
     * Converts a slayer task name (often plural) to the likely NPC name (often singular).
     * Examples: "Frost Dragons" -> "Frost dragon", "Abyssal Demons" -> "Abyssal demon"
     */
    private String convertTaskNameToNpcName(String taskName) {
        if (taskName == null || taskName.isEmpty()) {
            return taskName;
        }

        String result = taskName.trim();

        // Remove trailing 's' for simple plurals (Dragons -> Dragon)
        if (result.endsWith("s") && !result.endsWith("ss")) {
            result = result.substring(0, result.length() - 1);
        }

        // Convert to title case (first letter uppercase, rest lowercase for each word)
        // "FROST DRAGON" -> "Frost dragon", "frost dragon" -> "Frost dragon"
        String[] words = result.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 0) {
                if (i == 0) {
                    // First word: capitalize first letter
                    sb.append(Character.toUpperCase(words[i].charAt(0)));
                    if (words[i].length() > 1) {
                        sb.append(words[i].substring(1).toLowerCase());
                    }
                } else {
                    // Subsequent words: all lowercase
                    sb.append(" ").append(words[i].toLowerCase());
                }
            }
        }

        return sb.toString();
    }

    /**
     * Finds a nearby superior slayer monster that is attackable
     * @return The superior NPC if found, null otherwise
     */
    private Rs2NpcModel findNearbySuperior() {
        return Rs2Npc.getAttackableNpcs(true)
                .filter(npc -> npc.getName() != null)
                .filter(npc -> SUPERIOR_MONSTERS.contains(npc.getName()))
                .filter(npc -> taskDestination == null ||
                        npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius() + 5) // Slightly larger radius for superiors
                .min(Comparator.comparingInt(npc ->
                        Rs2Player.getWorldLocation().distanceTo(npc.getWorldLocation())))
                .orElse(null);
    }

    /**
     * Checks if we need to bank based on food, potion, and cannonball thresholds
     */
    private boolean needsBanking() {
        // Check food threshold
        int foodThreshold = config.foodThreshold();
        if (foodThreshold > 0) {
            int foodCount = Rs2Inventory.getInventoryFood().size();
            if (foodCount < foodThreshold) {
                log.info("Food count ({}) below threshold ({})", foodCount, foodThreshold);
                return true;
            }
        }

        // Check prayer potion threshold
        int potionThreshold = config.potionThreshold();
        if (potionThreshold > 0) {
            int prayerDoses = getPrayerPotionDoses();
            if (prayerDoses < potionThreshold) {
                log.info("Prayer potion doses ({}) below threshold ({})", prayerDoses, potionThreshold);
                return true;
            }
        }

        // Check cannonball threshold if using cannon
        if (isUsingCannon && config.enableCannon()) {
            int cannonballThreshold = config.cannonballThreshold();
            if (cannonballThreshold > 0) {
                int cannonballCount = getCannonballCount();
                if (cannonballCount < cannonballThreshold) {
                    log.info("Cannonball count ({}) below threshold ({})", cannonballCount, cannonballThreshold);
                    return true;
                }
            }
        }

        // Check fungicide spray - bank if spray is empty and no refills remain
        Rs2ItemModel fungicideSpray = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().startsWith("fungicide spray"));
        if (fungicideSpray != null) {
            int charges = parseFungicideCharges(fungicideSpray.getName());
            boolean hasRefills = Rs2Inventory.get(item ->
                    item != null && item.getName() != null &&
                    item.getName().equalsIgnoreCase("fungicide")) != null;
            if (charges <= 0 && !hasRefills) {
                log.info("Fungicide spray empty and no refills remaining");
                return true;
            }
        }

        return false;
    }

    /**
     * Gets total prayer potion doses in inventory (prayer potions and super restores)
     */
    private int getPrayerPotionDoses() {
        return Rs2Inventory.items()
                .filter(item -> item != null && item.getName() != null)
                .filter(item -> {
                    String name = item.getName().toLowerCase();
                    // Only count prayer-restoring potions
                    return (name.contains("prayer potion") ||
                            name.contains("super restore") ||
                            name.contains("sanfew serum") ||
                            name.contains("blighted super restore")) &&
                           (name.contains("(4)") || name.contains("(3)") ||
                            name.contains("(2)") || name.contains("(1)"));
                })
                .mapToInt(item -> {
                    String name = item.getName();
                    if (name.contains("(4)")) return 4;
                    if (name.contains("(3)")) return 3;
                    if (name.contains("(2)")) return 2;
                    if (name.contains("(1)")) return 1;
                    return 0;
                })
                .sum();
    }

    /**
     * Updates the BreakHandler lock state based on current slayer state.
     * Only allows breaks during safe states (BANKING, TRAVELING).
     */
    private void updateBreakHandlerLock(SlayerState currentState) {
        try {
            // Only allow breaks during safe states
            boolean allowBreak = (currentState == SlayerState.BANKING ||
                                  currentState == SlayerState.TRAVELING);

            // Set lock state (true = locked/no breaks, false = unlocked/can break)
            boolean shouldLock = !allowBreak;

            // Only update if state changed to avoid spam
            if (BreakHandlerScript.isLockState() != shouldLock) {
                BreakHandlerScript.setLockState(shouldLock);
                log.debug("BreakHandler lock state set to {} (state: {})", shouldLock, currentState);
            }
        } catch (Exception e) {
            // BreakHandler might not be enabled, ignore errors
            log.debug("BreakHandler not available: {}", e.getMessage());
        }
    }

    private void resetTravelData() {
        taskDestination = null;
        taskLocationName = "";
        SlayerPlugin.setCurrentLocation("");
        // Reset cannon state
        isUsingCannon = false;
        cannonSpot = null;
        // Reset active JSON profile
        activeJsonProfile = null;
        // Reset task completion loot delay
        taskCompletedLooting = false;
        // Note: cannonPlaced is NOT reset here - we track actual cannon placement separately
    }

    // ==================== Prayer Methods ====================

    /**
     * Sets up autocast spell for AoE (burst/barrage) profiles.
     * Should be called when the player is stationary (at POH after spellbook swap,
     * or at task location before combat) to avoid UI interaction being interrupted by movement.
     */
    private void setupAutoCastIfNeeded() {
        if (activeJsonProfile == null) {
            log.debug("setupAutoCastIfNeeded: No active profile");
            return;
        }
        if (!activeJsonProfile.isAoeStyle()) {
            log.debug("setupAutoCastIfNeeded: Profile style is not AOE (style: {})", activeJsonProfile.getStyle());
            return;
        }

        SlayerCombatStyle style = activeJsonProfile.getParsedStyle();
        if (style == null) {
            log.warn("setupAutoCastIfNeeded: Could not parse combat style from profile");
            return;
        }

        Rs2CombatSpells spell = (style == SlayerCombatStyle.BARRAGE)
                ? Rs2CombatSpells.ICE_BARRAGE
                : Rs2CombatSpells.ICE_BURST;

        Rs2CombatSpells currentSpell = Rs2Magic.getCurrentAutoCastSpell();
        log.debug("setupAutoCastIfNeeded: Current autocast={}, desired={}",
                currentSpell != null ? currentSpell.name() : "NONE", spell.name());

        if (currentSpell != spell) {
            log.info("Setting autocast to {} (current: {})", spell.name(),
                    currentSpell != null ? currentSpell.name() : "NONE");

            // Use Rs2Combat API (requires Combat Options tab to have a hotkey assigned)
            Rs2Combat.setAutoCastSpell(spell, false);
            sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == spell, 5000);

            Rs2CombatSpells afterSpell = Rs2Magic.getCurrentAutoCastSpell();
            if (afterSpell == spell) {
                log.info("Autocast set to {} successfully", spell.name());
            } else {
                log.warn("Failed to set autocast to {} - current is now: {}. " +
                        "Ensure Combat Options tab has a hotkey assigned in OSRS settings.", spell.name(),
                        afterSpell != null ? afterSpell.name() : "NONE");
            }
        } else {
            log.debug("Autocast already set to {}", spell.name());
        }
    }

    /**
     * Activates the appropriate prayer based on the active JSON profile and prayer style.
     * For flicking modes, prayer activation is handled by the flicker script.
     */
    private void activateProfilePrayer() {
        PrayerFlickStyle style = config.prayerFlickStyle();

        // Activate offensive prayers if enabled (independent of protection prayer style)
        activateOffensivePrayer();

        // Don't activate protection prayer if style is OFF
        if (style == PrayerFlickStyle.OFF) {
            return;
        }

        // Flicking modes are handled by FlickerScript, not here
        if (style == PrayerFlickStyle.LAZY_FLICK ||
            style == PrayerFlickStyle.PERFECT_LAZY_FLICK ||
            style == PrayerFlickStyle.MIXED_LAZY_FLICK) {
            return;
        }

        // Check JSON profile for prayer (ALWAYS_ON mode)
        if (activeJsonProfile != null && activeJsonProfile.hasPrayer()) {
            SlayerPrayer slayerPrayer = activeJsonProfile.getParsedPrayer();
            if (slayerPrayer != null && slayerPrayer != SlayerPrayer.NONE) {
                activatePrayer(slayerPrayer);
            }
        } else if (style == PrayerFlickStyle.ALWAYS_ON) {
            log.warn("ALWAYS_ON mode but no profile prayer found - activeJsonProfile={}, hasPrayer={}",
                    activeJsonProfile != null ? "present" : "null",
                    activeJsonProfile != null ? activeJsonProfile.hasPrayer() : "N/A");
        }
    }

    /**
     * Helper method to activate a specific SlayerPrayer
     */
    private void activatePrayer(SlayerPrayer slayerPrayer) {
        if (slayerPrayer.getPrayer() != null) {
            Rs2PrayerEnum prayer = slayerPrayer.getPrayer();
            if (!Rs2Prayer.isPrayerActive(prayer)) {
                Rs2Prayer.toggle(prayer, true);
                log.debug("Activated prayer: {}", slayerPrayer.getDisplayName());
            }
        }
    }

    /**
     * Deactivates profile-specific prayers.
     */
    private void deactivateProfilePrayer() {
        PrayerFlickStyle style = config.prayerFlickStyle();

        if (style == PrayerFlickStyle.OFF) {
            return;
        }

        // Disable JSON profile prayer
        if (activeJsonProfile != null && activeJsonProfile.hasPrayer()) {
            SlayerPrayer slayerPrayer = activeJsonProfile.getParsedPrayer();
            if (slayerPrayer != null && slayerPrayer != SlayerPrayer.NONE && slayerPrayer.getPrayer() != null) {
                Rs2Prayer.toggle(slayerPrayer.getPrayer(), false);
            }
        }

        // Disable offensive prayers
        if (config.useOffensivePrayers()) {
            deactivateOffensivePrayers();
        }
    }

    // Varbits for prayer unlock status
    private static final int RIGOUR_UNLOCKED_VARBIT = 5451;
    private static final int AUGURY_UNLOCKED_VARBIT = 5452;

    /**
     * Activates the best offensive prayer based on the current combat style.
     * Uses the style from JSON profile if available, otherwise defaults to melee.
     */
    private void activateOffensivePrayer() {
        if (!config.useOffensivePrayers()) {
            return;
        }

        SlayerCombatStyle combatStyle = SlayerCombatStyle.MELEE;
        if (activeJsonProfile != null && activeJsonProfile.hasStyle()) {
            combatStyle = activeJsonProfile.getParsedStyle();
        }

        int prayerLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);

        switch (combatStyle) {
            case MELEE:
                activateBestMeleePrayer(prayerLevel);
                break;
            case RANGED:
                activateBestRangedPrayer(prayerLevel);
                break;
            case MAGIC:
            case BURST:
            case BARRAGE:
                activateBestMagicPrayer(prayerLevel);
                break;
        }
    }

    /**
     * Activates the best melee offensive prayer available.
     * Priority: Piety > Chivalry > Ultimate Strength + Incredible Reflexes
     */
    private void activateBestMeleePrayer(int prayerLevel) {
        // Piety requires 70 Prayer and Knight Waves completion
        if (prayerLevel >= 70) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
                    log.debug("Activated Piety");
                    return;
                }
            } else {
                return; // Already active
            }
        }

        // Chivalry requires 60 Prayer and Knight Waves completion
        if (prayerLevel >= 60) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.CHIVALRY)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.CHIVALRY, true);
                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.CHIVALRY)) {
                    log.debug("Activated Chivalry");
                    return;
                }
            } else {
                return;
            }
        }

        // Fallback to separate strength + attack prayers
        if (prayerLevel >= 31 && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.ULTIMATE_STRENGTH)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, true);
        }
        if (prayerLevel >= 34 && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.INCREDIBLE_REFLEXES)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.INCREDIBLE_REFLEXES, true);
        }
    }

    /**
     * Activates the best ranged offensive prayer available.
     * Priority: Rigour > Eagle Eye > Hawk Eye > Sharp Eye
     */
    private void activateBestRangedPrayer(int prayerLevel) {
        // Rigour requires 74 Prayer and Rigour scroll
        if (prayerLevel >= 74) {
            boolean rigourUnlocked = Microbot.getVarbitValue(RIGOUR_UNLOCKED_VARBIT) == 1;
            if (rigourUnlocked) {
                if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true);
                    log.debug("Activated Rigour");
                }
                return;
            }
        }

        // Eagle Eye requires 44 Prayer
        if (prayerLevel >= 44) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, true);
                log.debug("Activated Eagle Eye");
            }
            return;
        }

        // Hawk Eye requires 26 Prayer
        if (prayerLevel >= 26) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.HAWK_EYE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.HAWK_EYE, true);
                log.debug("Activated Hawk Eye");
            }
            return;
        }

        // Sharp Eye requires 8 Prayer
        if (prayerLevel >= 8) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.SHARP_EYE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.SHARP_EYE, true);
                log.debug("Activated Sharp Eye");
            }
        }
    }

    /**
     * Activates the best magic offensive prayer available.
     * Priority: Augury > Mystic Might > Mystic Lore > Mystic Will
     */
    private void activateBestMagicPrayer(int prayerLevel) {
        // Augury requires 77 Prayer and Augury scroll
        if (prayerLevel >= 77) {
            boolean auguryUnlocked = Microbot.getVarbitValue(AUGURY_UNLOCKED_VARBIT) == 1;
            if (auguryUnlocked) {
                if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.AUGURY, true);
                    log.debug("Activated Augury");
                }
                return;
            }
        }

        // Mystic Might requires 45 Prayer
        if (prayerLevel >= 45) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_MIGHT)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_MIGHT, true);
                log.debug("Activated Mystic Might");
            }
            return;
        }

        // Mystic Lore requires 27 Prayer
        if (prayerLevel >= 27) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_LORE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_LORE, true);
                log.debug("Activated Mystic Lore");
            }
            return;
        }

        // Mystic Will requires 9 Prayer
        if (prayerLevel >= 9) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_WILL)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_WILL, true);
                log.debug("Activated Mystic Will");
            }
        }
    }

    /**
     * Deactivates all offensive prayers.
     */
    private void deactivateOffensivePrayers() {
        // Melee prayers
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.CHIVALRY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.CHIVALRY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.ULTIMATE_STRENGTH)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.INCREDIBLE_REFLEXES)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.INCREDIBLE_REFLEXES, false);
        }

        // Ranged prayers
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.HAWK_EYE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.HAWK_EYE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.SHARP_EYE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.SHARP_EYE, false);
        }

        // Magic prayers
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.AUGURY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_MIGHT)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_MIGHT, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_LORE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_LORE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_WILL)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_WILL, false);
        }
    }

    /**
     * Checks if prayer should be active based on style and combat state
     */
    private boolean shouldPrayerBeActive() {
        PrayerFlickStyle style = config.prayerFlickStyle();

        switch (style) {
            case OFF:
                return false;
            case ALWAYS_ON:
                return true;
            default:
                // Flicking modes handled separately
                return false;
        }
    }

    // ==================== Cannon Helper Methods ====================

    /**
     * Checks if we should use cannon for the given task
     */
    private boolean shouldUseCannon(String taskName) {
        if (taskName == null || taskName.isEmpty()) {
            return false;
        }

        // MASTER TOGGLE: Config enableCannon must be ON for any cannon usage
        if (!config.enableCannon()) {
            return false;
        }

        // Check JSON profile for cannon setting (per-task preference)
        if (activeJsonProfile != null) {
            if (activeJsonProfile.isCannon()) {
                // Profile wants cannon - verify we have a spot for it
                if (CannonSpot.hasSpotForTask(taskName) || activeJsonProfile.hasCannonLocation()) {
                    log.info("Using cannon for '{}' based on JSON profile", taskName);
                    return true;
                } else {
                    log.warn("JSON profile specifies cannon for '{}' but no cannon spot/location found", taskName);
                    return false;
                }
            } else {
                // Profile exists but cannon is disabled for this task
                return false;
            }
        }

        // No profile found - fall back to cannon task list filter
        // Check if task has a predefined cannon spot
        if (!CannonSpot.hasSpotForTask(taskName)) {
            return false;
        }

        // Check if cannon task list is configured (optional filter)
        String cannonTaskList = config.cannonTaskList();
        if (cannonTaskList != null && !cannonTaskList.isEmpty()) {
            String taskLower = taskName.toLowerCase().trim();
            boolean isInList = Arrays.stream(cannonTaskList.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .anyMatch(cannonTask -> taskLower.contains(cannonTask) || cannonTask.contains(taskLower));
            if (!isInList) {
                log.info("Task '{}' not in cannon task list, skipping cannon", taskName);
                return false;
            }
        }

        // Check if we have an inventory setup configured in the profile
        if (activeJsonProfile == null || !activeJsonProfile.hasSetup()) {
            log.warn("Cannon enabled but no inventory setup configured in profile");
            return false;
        }

        return true;
    }

    /**
     * Gets the active inventory setup based on task profile, cannon usage, and config.
     * If cannon is being used, automatically tries to find a cannon variant by appending
     * "-cannon" suffix (e.g., "melee" -> "melee-cannon", "ranged" -> "ranged-cannon").
     * Checks JSON profile first, then falls back to text-based profile.
     */
    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup getActiveInventorySetup() {
        String setupName = null;

        // Check JSON profile for setup name
        if (activeJsonProfile != null && activeJsonProfile.hasSetup()) {
            setupName = activeJsonProfile.getSetup();
            log.debug("Using setup name '{}' from JSON profile", setupName);
        }

        // If we have a setup name from a profile, try to find it
        if (setupName != null && !setupName.isEmpty()) {
            // If using cannon, try to find cannon variant first (e.g., "melee" -> "melee-cannon")
            if (isUsingCannon && !setupName.toLowerCase().endsWith("-cannon")) {
                String cannonName = setupName + "-cannon";
                net.runelite.client.plugins.microbot.inventorysetups.InventorySetup cannonSetup = findInventorySetupByName(cannonName);
                if (cannonSetup != null) {
                    log.info("Using cannon inventory setup '{}' (cannon enabled)", cannonName);
                    return cannonSetup;
                }
                log.debug("Cannon setup '{}' not found, falling back to '{}'", cannonName, setupName);
            }

            // Try to find the inventory setup by name
            net.runelite.client.plugins.microbot.inventorysetups.InventorySetup profileSetup = findInventorySetupByName(setupName);
            if (profileSetup != null) {
                log.debug("Using inventory setup '{}' from profile", setupName);
                return profileSetup;
            } else {
                log.warn("Could not find inventory setup '{}' specified in profile", setupName);
            }
        }

        // No profile setup found - inventory setup must be defined in slayer-profiles.json
        log.warn("No inventory setup found. Please configure 'setup' in slayer-profiles.json for this task.");
        return null;
    }

    /**
     * Finds an inventory setup by name from the inventory setups plugin
     */
    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup findInventorySetupByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Look up from InventorySetupsPlugin by name
        var allSetups = MInventorySetupsPlugin.getInventorySetups();
        if (allSetups != null) {
            var found = allSetups.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(setup -> setup.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                log.debug("Found inventory setup '{}' from InventorySetupsPlugin", name);
                return found;
            }
        }

        log.warn("Inventory setup '{}' not found. " +
                "Please create an inventory setup with this name in the Inventory Setups plugin.", name);
        return null;
    }

    /**
     * Checks if player has cannon parts in inventory
     */
    private boolean hasCannonParts() {
        return Rs2Inventory.hasItem("Cannon base") &&
               Rs2Inventory.hasItem("Cannon stand") &&
               Rs2Inventory.hasItem("Cannon barrels") &&
               Rs2Inventory.hasItem("Cannon furnace");
    }

    /**
     * Gets the count of cannonballs in inventory
     */
    private int getCannonballCount() {
        Rs2ItemModel cannonballs = Rs2Inventory.get("Cannonball");
        if (cannonballs != null) {
            return cannonballs.getQuantity();
        }
        // Also check for granite cannonballs
        Rs2ItemModel graniteCannonballs = Rs2Inventory.get("Granite cannonball");
        if (graniteCannonballs != null) {
            return graniteCannonballs.getQuantity();
        }
        return 0;
    }

    /**
     * Checks if we should disable cannon mode due to low cannonballs.
     * If cannonballs are below threshold, this method:
     * 1. Picks up the cannon if placed
     * 2. Disables cannon mode
     * 3. Resets travel data so we switch to non-cannon location
     * 4. Returns true to indicate we should bank/re-setup
     *
     * @return true if cannon mode was disabled and we need to re-setup
     */
    private boolean checkAndDisableCannonIfLow() {
        if (!isUsingCannon || !config.enableCannon()) {
            return false;
        }

        int cannonballCount = getCannonballCount();
        int threshold = config.cannonballThreshold();

        // Only disable if we're actually below threshold
        if (threshold > 0 && cannonballCount < threshold) {
            log.info("Cannonballs ({}) below threshold ({}), disabling cannon mode", cannonballCount, threshold);

            // Pick up cannon if placed
            if (cannonPlaced) {
                log.info("Picking up cannon due to low cannonballs");
                if (pickupCannon()) {
                    sleepUntil(() -> !isCannonPlacedNearby(), 3000);
                }
                cannonPlaced = false;
                cannonFired = false;
            }

            // Disable cannon mode
            isUsingCannon = false;
            cannonFired = false;
            cannonSpot = null;

            // Reset travel data so we recalculate with non-cannon location
            taskDestination = null;
            taskLocationName = "";

            log.info("Cannon mode disabled, will switch to non-cannon setup and location");
            return true;
        }

        return false;
    }

    /**
     * Handles cannon maintenance - repair if broken, refill if needed.
     * Based on AIOFighter's simple approach: just repair and refill.
     */
    private void handleCannonMaintenance() {
        log.debug("Cannon maintenance - cannonPlaced: {}, cannonFired: {}", cannonPlaced, cannonFired);

        // Try to repair first (if broken)
        if (Rs2Cannon.repair()) {
            log.info("Repaired cannon");
            cannonFired = true; // Cannon is working if we repaired it
            return;
        }

        // Refill cannon with cannonballs - this also starts the cannon firing
        if (Rs2Cannon.refill()) {
            log.info("Refilled cannon with cannonballs");
            cannonFired = true; // Mark as fired since refill starts the cannon
        }
    }

    /**
     * Sets up the cannon by using the cannon base from inventory
     * @return true if setup was initiated successfully
     */
    private boolean setupCannon() {
        // Use cannon base from inventory to start setup
        if (Rs2Inventory.interact("Cannon base", "Set-up")) {
            log.info("Setting up cannon...");
            return true;
        }
        return false;
    }

    /**
     * Starts the cannon by refilling it with cannonballs.
     * Uses Rs2Cannon.refill() which handles loading cannonballs and starting the cannon.
     * @return true if cannon was refilled/started successfully
     */
    private boolean fireCannon() {
        // Just use Rs2Cannon.refill() - this handles loading cannonballs and starting the cannon
        // This is the same approach used by AIOFighter
        if (Rs2Cannon.refill()) {
            log.info("Cannon refilled and started via Rs2Cannon.refill()");
            return true;
        }
        log.debug("Rs2Cannon.refill() returned false - cannon may already be full or not placed");
        return false;
    }

    /**
     * Handles the full cannon pickup process including walking to it if needed.
     * @return true if cannon was successfully picked up (no longer exists), false if still in progress
     */
    private boolean handleCannonPickup() {
        // Check if cannon still exists
        var cannon = Rs2GameObject.findObject("Dwarf multicannon", true, 50, false, Rs2Player.getWorldLocation());
        if (cannon == null) {
            log.info("Cannon already picked up or not found");
            return true; // Cannon doesn't exist, we're done
        }

        WorldPoint cannonLocation = cannon.getWorldLocation();
        int distance = Rs2Player.getWorldLocation().distanceTo(cannonLocation);

        // If too far, walk to cannon first
        if (distance > 5) {
            log.info("Walking to cannon to pick it up (distance: {})", distance);
            Rs2Walker.walkTo(cannonLocation, 2);
            return false; // Still in progress
        }

        // Try to pick up cannon
        if (Rs2GameObject.interact(cannon, "Pick-up")) {
            log.info("Picking up cannon...");
            sleepUntil(() -> !isCannonPlacedNearby(), 5000);

            // Check if pickup was successful
            if (!isCannonPlacedNearby()) {
                log.info("Cannon picked up successfully");
                return true;
            } else {
                log.warn("Cannon pickup may have failed, retrying next tick");
                return false;
            }
        }

        log.warn("Failed to interact with cannon");
        return false; // Will retry next tick
    }

    /**
     * Picks up the cannon by interacting with it
     * @return true if pickup was initiated successfully
     */
    private boolean pickupCannon() {
        if (Rs2GameObject.interact("Dwarf multicannon", "Pick-up")) {
            log.info("Picking up cannon...");
            return true;
        }
        return false;
    }

    /**
     * Checks if a cannon is placed nearby
     * @return true if cannon object is found nearby
     */
    private boolean isCannonPlacedNearby() {
        return Rs2GameObject.findObject("Dwarf multicannon", true, 10, false, Rs2Player.getWorldLocation()) != null;
    }

    /**
     * Resets cannon state
     */
    private void resetCannonState() {
        isUsingCannon = false;
        cannonPlaced = false;
        cannonFired = false;
        cannonSpot = null;
    }

    // ==================== End Cannon Helper Methods ====================

    /**
     * Stops the plugin and logs out the player.
     * Used when we can't complete a required action (e.g., can't skip a task on skip list)
     */
    private void stopAndLogout() {
        log.info("Stopping plugin and logging out...");

        // Disable prayers
        deactivateProfilePrayer();

        // Set state to idle
        SlayerPlugin.setState(SlayerState.IDLE);

        // Wait briefly for combat to end if we're fighting
        if (Rs2Combat.inCombat()) {
            log.info("Waiting for combat to end before logout...");
            sleepUntil(() -> !Rs2Combat.inCombat(), 10000);
        }

        // Logout
        Rs2Player.logout();
        sleep(1000, 2000);

        // Stop the script
        shutdown();
    }

    @Override
    public void shutdown() {
        log.info("Slayer script shutting down");
        // Unlock break handler on shutdown
        try {
            BreakHandlerScript.setLockState(false);
        } catch (Exception e) {
            // BreakHandler might not be enabled, ignore
        }
        // Disable prayers on shutdown
        deactivateProfilePrayer();
        // Note: We don't pick up cannon on shutdown - user may want to keep it
        resetTravelData();
        resetSkipState();
        resetPohState();
        resetCannonState();
        initialSetupDone = false;
        super.shutdown();
    }
}
