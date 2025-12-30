package net.runelite.client.plugins.microbot.autoessencemining;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autoessencemining.enums.AutoEssenceMiningState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;


@Slf4j
public class AutoEssenceMiningScript extends Script {
    private static final WorldPoint AUBURY_LOCATION = new WorldPoint(3253, 3399, 0);
    private static final int ESSENCE_MINE_REGION = 11595; // Rune essence mine region ID
    
    private AutoEssenceMiningState state = AutoEssenceMiningState.WALKING_TO_AUBURY;
    private boolean hasTeleportedWithAubury = false;
    private boolean isInEssenceMine = false;
    private boolean needsToBank = false;
    private long stateStartTime = System.currentTimeMillis(); // track state timeout
    private long lastStateChangeTime = 0; // prevent rapid state changes
    private static final long STATE_CHANGE_COOLDOWN_MS = 500; // minimum time between state changes
    private boolean isUsingPortal = false; // track if we're currently using the portal
    private long portalUseStartTime = 0; // track when we started using portal
    private long lastPortalAttemptTime = 0; // track last portal interaction attempt
    private static final long PORTAL_RETRY_COOLDOWN_MS = 2000; // wait 2 seconds between portal attempts

    public boolean run(AutoEssenceMiningConfig config) {
        log.info("Starting essence mining script");
        initialPlayerLocation = Rs2Player.getWorldLocation();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    log.info("Super.run() returned false, stopping");
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    log.info("Not logged in, waiting");
                    return;
                }
                if (Rs2AntibanSettings.actionCooldownActive) {
                    log.info("Antiban cooldown active, waiting");
                    return;
                }

                // track loop performance
                long startTime = System.currentTimeMillis();
                
                // state timeout protection
                if (System.currentTimeMillis() - stateStartTime > 30000) {
                    log.info("State timeout after 30 seconds, resetting to WALKING_TO_AUBURY");
                    changeState(AutoEssenceMiningState.WALKING_TO_AUBURY);
                    return;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                    log.info("Player is moving or animating, waiting");
                    return;
                }

                // determine current location and state
                isInEssenceMine = (Rs2Player.getWorldLocation().getRegionID() == ESSENCE_MINE_REGION);
                needsToBank = Rs2Inventory.isFull();
                
                // Check if portal teleport completed (we're no longer in the mine)
                if (isUsingPortal && !isInEssenceMine) {
                    log.info("Portal teleport completed, no longer in essence mine");
                    isUsingPortal = false;
                    portalUseStartTime = 0;
                }
                
                // Timeout portal use if it takes too long (10 seconds)
                if (isUsingPortal && System.currentTimeMillis() - portalUseStartTime > 10000) {
                    log.info("Portal use timeout, resetting flag");
                    isUsingPortal = false;
                    portalUseStartTime = 0;
                }
                
                log.info("=== State Evaluation ===");
                log.info("Current state: {}", state);
                log.info("In essence mine: {}", isInEssenceMine);
                log.info("Inventory full: {}", needsToBank);
                log.info("Is using portal: {}", isUsingPortal);
                log.info("Distance to Aubury: {}", Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION));

                // Skip state evaluation if we're currently using the portal OR if we're in USING_PORTAL state and still in mine
                // This prevents re-evaluation loops when waiting for portal cooldown or retrying portal interaction
                // Allow state evaluation if we've left the mine (portal worked) or if we've been stuck for too long
                boolean shouldSkipStateEvaluation = false;
                if (isUsingPortal) {
                    shouldSkipStateEvaluation = true;
                    log.info("Skipping state evaluation - portal use in progress");
                } else if (state == AutoEssenceMiningState.USING_PORTAL && isInEssenceMine) {
                    // If we're in USING_PORTAL state but still in the mine, skip state evaluation
                    // unless we've been stuck for more than 20 seconds
                    long timeInPortalState = System.currentTimeMillis() - stateStartTime;
                    if (timeInPortalState < 20000) {
                        shouldSkipStateEvaluation = true;
                        log.info("Skipping state evaluation - in USING_PORTAL state for {}ms, waiting for portal interaction", timeInPortalState);
                    } else {
                        log.info("Been in USING_PORTAL state for {}ms, allowing state re-evaluation", timeInPortalState);
                    }
                }
                
                if (!shouldSkipStateEvaluation) {
                    // Evaluate what state we should be in, but only change once per iteration
                    AutoEssenceMiningState targetState = determineTargetState();
                    
                    // Only change state if it's different and cooldown has passed
                    if (targetState != state) {
                        changeState(targetState);
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
                        handleBanking();
                        break;
                }
                
                // track loop performance
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                log.info("Total time for loop: {}ms", totalTime);
                
            } catch (Exception ex) {
                log.error("Error in main essence mining loop: {}", ex.getMessage(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleWalkingToAubury() {
        log.info("State: WALKING_TO_AUBURY");
        Microbot.status = "Walking to Aubury";
        
        // validate current distance before walking
        int currentDistance = Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION);
        log.info("Current distance to Aubury: {} tiles", currentDistance);
        
        if (currentDistance <= 8) {
            log.info("Already near Aubury, no need to walk");
            return;
        }
        
        // attempt to walk to Aubury
        if (Rs2Walker.walkTo(AUBURY_LOCATION)) {
            log.info("Started walking to Aubury location");
        } else {
            log.info("Failed to start walking to Aubury");
        }
    }

    private void handleTeleportingWithAubury() {
        log.info("State: TELEPORTING_WITH_AUBURY");
        Microbot.status = "Teleporting with Aubury";
        
        // validate we're close enough to Aubury before attempting teleport
        if (Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION) > 8) {
            log.info("Too far from Aubury for teleport, distance: {}", Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION));
            return;
        }
        
        // find Aubury NPC
        Rs2NpcModel aubury = Rs2Npc.getNpc("Aubury");
        if (aubury != null) {
            log.info("Found Aubury, attempting teleport");
            if (Rs2Npc.interact(aubury, "Teleport")) {
                log.info("Clicked teleport, waiting for animation");
                Rs2Player.waitForAnimation(3000);
                log.info("Teleport animation completed");
                hasTeleportedWithAubury = true;
            } else {
                log.info("Failed to interact with Aubury for teleport");
            }
        } else {
            log.info("Aubury NPC not found nearby");
        }
    }

    private void handleMiningEssence() {
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
        GameObject essenceRock = Rs2GameObject.getGameObject("Rune Essence", false);
        
        if (essenceRock != null) {
            log.info("Found rune essence rock, attempting to mine");
            if (Rs2GameObject.interact(essenceRock, "Mine")) {
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
        log.info("State: USING_PORTAL");
        Microbot.status = "Using portal to exit";
        
        // If we're already using the portal, wait for it to complete
        if (isUsingPortal) {
            log.info("Portal use in progress, waiting for teleport to complete");
            // Check if we've left the mine
            if (!isInEssenceMine) {
                log.info("Successfully teleported out of essence mine");
                isUsingPortal = false;
                portalUseStartTime = 0;
                hasTeleportedWithAubury = false;
            }
            return;
        }
        
        // validate we're in the essence mine before looking for portal
        if (Rs2Player.getWorldLocation().getRegionID() != ESSENCE_MINE_REGION) {
            log.info("Not in essence mine, cannot use portal");
            return;
        }

        // Check cooldown before attempting portal interaction
        long timeSinceLastAttempt = System.currentTimeMillis() - lastPortalAttemptTime;
        if (timeSinceLastAttempt < PORTAL_RETRY_COOLDOWN_MS) {
            log.info("Portal retry cooldown active ({}ms remaining), waiting", 
                    PORTAL_RETRY_COOLDOWN_MS - timeSinceLastAttempt);
            return;
        }

        // Don't try to interact if player is already moving or animating
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            log.info("Player is moving or animating, waiting before portal interaction");
            return;
        }

        // find the portal to exit
        GameObject portal = Rs2GameObject.getGameObject("Portal", false);

        if (portal != null) {
            log.info("Found portal, attempting to use it");
            lastPortalAttemptTime = System.currentTimeMillis();
            if (Rs2GameObject.interact(portal)) {
                log.info("Clicked portal, setting portal use flag");
                isUsingPortal = true;
                portalUseStartTime = System.currentTimeMillis();
                // Wait a bit for the teleport to start
                sleep(500);
            } else {
                log.info("Failed to interact with portal, will retry after cooldown");
            }
        } else {
            log.info("Portal not found in essence mine, will retry after cooldown");
            lastPortalAttemptTime = System.currentTimeMillis();
        }
    }


    private void handleBanking() {
        log.info("State: BANKING");
        Microbot.status = "Banking at Varrock East";
        
        WorldPoint varrockEastBank = new WorldPoint(3253, 3420, 0);
        int distanceToBank = Rs2Player.getWorldLocation().distanceTo(varrockEastBank);
        
        log.info("=== Banking State Check ===");
        log.info("Distance to bank: {} tiles", distanceToBank);
        log.info("Bank open: {}", Rs2Bank.isOpen());
        log.info("Inventory full: {}", Rs2Inventory.isFull());
        
        // walk to bank if too far
        if (!Rs2Bank.isOpen()) {
            if (distanceToBank > 10) {
                log.info("Too far from bank, walking there");
                Rs2Walker.walkTo(varrockEastBank);
                return;
            }
            
            // attempt to open bank
            log.info("Near bank, attempting to open");
            if (!Rs2Bank.walkToBankAndUseBank()) {
                log.info("Failed to open bank");
                return;
            }
        }

        // perform banking operations
        if (Rs2Bank.isOpen()) {
            log.info("Bank is open, depositing essence except pickaxe");
            Rs2Bank.depositAllExcept("pickaxe");
            
            // verify items were deposited
            boolean inventoryCleared = sleepUntil(() -> !Rs2Inventory.isFull(), 3000);
            if (inventoryCleared) {
                log.info("Successfully deposited essence");
            } else {
                log.info("Inventory still full after deposit attempt");
            }
            
            log.info("Closing bank after deposit");
            Rs2Bank.closeBank();
            
            // reset teleport flag for next trip
            hasTeleportedWithAubury = false;
        }
    }

    // Determine the target state based on current conditions
    private AutoEssenceMiningState determineTargetState() {
        if (isInEssenceMine) {
            if (!needsToBank) {
                log.info("In mine with space, target state: MINING_ESSENCE");
                hasTeleportedWithAubury = true;
                return AutoEssenceMiningState.MINING_ESSENCE;
            } else {
                log.info("In mine but inventory full, target state: USING_PORTAL");
                return AutoEssenceMiningState.USING_PORTAL;
            }
        } else {
            if (needsToBank) {
                log.info("Need to bank, target state: BANKING");
                return AutoEssenceMiningState.BANKING;
            } else {
                if (Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION) <= 8) {
                    log.info("Near Aubury, target state: TELEPORTING_WITH_AUBURY");
                    if (hasTeleportedWithAubury) {
                        hasTeleportedWithAubury = false;
                    }
                    return AutoEssenceMiningState.TELEPORTING_WITH_AUBURY;
                } else {
                    log.info("Far from Aubury, target state: WALKING_TO_AUBURY");
                    return AutoEssenceMiningState.WALKING_TO_AUBURY;
                }
            }
        }
    }

    // helper method to change state with timeout reset and cooldown protection
    private void changeState(AutoEssenceMiningState newState) {
        if (newState == state) {
            return; // Already in this state
        }
        
        // Check cooldown to prevent rapid state changes
        long timeSinceLastChange = System.currentTimeMillis() - lastStateChangeTime;
        if (timeSinceLastChange < STATE_CHANGE_COOLDOWN_MS) {
            log.info("State change cooldown active ({}ms remaining), skipping change from {} to {}", 
                    STATE_CHANGE_COOLDOWN_MS - timeSinceLastChange, state, newState);
            return;
        }
        
        log.info("State change: {} -> {}", state, newState);
        state = newState;
        stateStartTime = System.currentTimeMillis();
        lastStateChangeTime = System.currentTimeMillis();
    }

    @Override
    public void shutdown() {
        log.info("Shutting down essence mining script");
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        log.info("Essence mining script shutdown complete");
    }
}