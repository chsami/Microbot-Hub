package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.List;

public class BrimhavenSpikeCourse implements AgilityCourseHandler {
    
    private static final WorldPoint START_POINT = new WorldPoint(2809, 3192, 0);
    private static final int CAPTAIN_IZZY_NPC_ID = 5789;
    private static final int COINS_ID = 995;
    private static final int REQUIRED_COINS = 200;
    
    private boolean hasPaid = false;
    private boolean hasClimbedLadder = false;
    private int lastAgilityXpForStep = -1;
    private long lastStepAtMs = 0;
    private int currentObstacleIndex = 0;
    
    /**
     * Reset all flags when script shuts down
     */
    public void reset() {
        hasPaid = false;
        hasClimbedLadder = false;
        lastAgilityXpForStep = -1;
        lastStepAtMs = 0;
        currentObstacleIndex = 0;
        Microbot.log("BrimhavenSpike course flags reset");
    }

    @Override
    public WorldPoint getStartPoint() {
        return START_POINT;
    }

    @Override
    public List<AgilityObstacleModel> getObstacles() {
        return List.of(
            new AgilityObstacleModel(3566), // Brimhaven spike obstacle 1 (rope swing)
            new AgilityObstacleModel(3578), // Brimhaven spike obstacle 2 (pillar jump)
            new AgilityObstacleModel(9999)  // Brimhaven spike obstacle 3 (spikes - placeholder, handled by timed tile-walking)
        );
    }

    @Override
    public Integer getRequiredLevel() {
        return 30;
    }

    @Override
    public boolean canBeBoosted() {
        return true;
    }

    /**
     * Check if player has required coins for the course
     */
    public boolean hasRequiredCoins() {
        return Rs2Inventory.itemQuantity(COINS_ID) >= REQUIRED_COINS;
    }

    public boolean hasPaid() {
        return hasPaid;
    }

    /**
     * Handle payment to Cap'n Izzy No-Beard
     */
    public boolean handlePayment() {
        if (hasPaid) {
            return false; // Already paid
        }

        // Check if we have enough coins
        if (!hasRequiredCoins()) {
            Microbot.showMessage("You need 200 coins to enter the Brimhaven Spike course!");
            return false;
        }

        // Find and interact with Cap'n Izzy No-Beard
        var captain = Rs2Npc.getNpc(CAPTAIN_IZZY_NPC_ID);
        if (captain == null) {
            Microbot.log("Cap'n Izzy No-Beard not found!");
            return false;
        }

        // Click "Pay" option
        if (Rs2Npc.interact(captain, "Pay")) {
            Microbot.log("Attempting to pay Cap'n Izzy No-Beard...");
            
            // Wait for coins to be deducted
            int initialCoins = Rs2Inventory.itemQuantity(COINS_ID);
            boolean paymentSuccess = Global.sleepUntil(() -> {
                int currentCoins = Rs2Inventory.itemQuantity(COINS_ID);
                return currentCoins < initialCoins;
            }, 5000);
            
            if (paymentSuccess) {
                hasPaid = true;
                Microbot.log("Successfully paid for Brimhaven Spike course!");
                return true;
            } else {
                Microbot.log("Payment failed - coins not deducted");
                return false;
            }
        }
        
        return false;
    }

    /**
     * Handle climbing down the ladder - simplified approach
     */
    public boolean handleLadderDescent() {
        if (hasClimbedLadder) {
            return false; // Already climbed
        }

        // Just click the ladder directly without detection
        Microbot.log("Force clicking ladder...");
        Rs2GameObject.interact(3617, "Climb-down");
        
        // Wait a bit for the interaction
        Global.sleep(1000);
        
        // Check if we're now on plane 3
        WorldPoint currentLoc = Rs2Player.getWorldLocation();
        if (currentLoc.getPlane() == 3) {
            hasClimbedLadder = true;
            lastAgilityXpForStep = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
            Microbot.log("Successfully climbed down ladder to plane 3!");
            return true;
        }
        
        Microbot.log("Ladder interaction failed - still on plane " + currentLoc.getPlane());
        return false;
    }

    /**
     * Handle walking to start if needed
     */
    @Override
    public boolean handleWalkToStart(WorldPoint playerWorldLocation) {
        Microbot.log("BrimhavenSpike handleWalkToStart called - Player at: " + playerWorldLocation + ", hasPaid: " + hasPaid + ", hasClimbedLadder: " + hasClimbedLadder);
        
        // If we're not at the start point (ground level), walk there first
        if (playerWorldLocation.getPlane() != 0 || playerWorldLocation.distanceTo(START_POINT) > 5) {
            // If we're already in the course area (plane 3), check if we need to handle obstacle 3
            if (playerWorldLocation.getPlane() == 3) {
                Microbot.log("Already in the spike course area on plane 3 - checking obstacle 3");
                
                // If we've completed obstacles 1 and 2, force index to 2 for obstacle 3 (spikes)
                if (currentObstacleIndex >= 2) {
                    currentObstacleIndex = 2; // Ensure we're on obstacle 3
                    Microbot.log("Forcing to obstacle 3 (spikes) - using timed tile-walking");
                    return handleSpikeTileWalking();
                }
                
                Microbot.log("In the spike course area on plane 3 - ready for obstacles (currentObstacleIndex: " + currentObstacleIndex + ")");
                return false; // Let the main script handle obstacles
            }
            Microbot.log("Walking to Brimhaven Spike course start point");
            Rs2Walker.walkTo(START_POINT, 2);
            return true;
        }
        
        // If we haven't paid yet, handle payment first
        if (!hasPaid) {
            Microbot.log("Attempting to pay Cap'n Izzy...");
            boolean paymentResult = handlePayment();
            Microbot.log("Payment result: " + paymentResult);
            return paymentResult;
        }
        
        // If we haven't climbed the ladder yet, handle ladder descent
        if (!hasClimbedLadder) {
            Microbot.log("Attempting to climb down ladder...");
            boolean ladderResult = handleLadderDescent();
            Microbot.log("Ladder result: " + ladderResult);
            return ladderResult;
        }
        
        // If we're on plane 3, we're in the course area
        if (playerWorldLocation.getPlane() == 3) {
            Microbot.log("On plane 3 - currentObstacleIndex: " + currentObstacleIndex);
            
            // If we've completed obstacles 1 and 2, force index to 2 for obstacle 3 (spikes)
            if (currentObstacleIndex >= 2) {
                currentObstacleIndex = 2; // Ensure we're on obstacle 3
                Microbot.log("Forcing to obstacle 3 (spikes) - using timed tile-walking");
                return handleSpikeTileWalking();
            }
            
            Microbot.log("In the spike course area on plane 3 - ready for obstacles (currentObstacleIndex: " + currentObstacleIndex + ")");
            return false; // Let the main script handle obstacles
        }
        
        Microbot.log("BrimhavenSpike handleWalkToStart returning false - no action needed");
        return false;
    }
    
    /**
     * Handle the timed tile-walking for obstacle 3 (spikes)
     */
    public boolean handleSpikeTileWalking() {
        // Choose one of the two specific tiles randomly: (2800,9568,3) or (2799,9568,3)
        boolean chooseFirst = (System.nanoTime() & 1L) == 0L;
        WorldPoint target = chooseFirst
            ? new WorldPoint(2800, 9568, 3)
            : new WorldPoint(2799, 9568, 3);

        Microbot.log("Walking to spike tile: " + target);
        Rs2Walker.walkFastCanvas(target);
        
        // Check if the player actually moved
        Global.sleep(500); // Give a moment for movement to start
        WorldPoint currentPos = Rs2Player.getWorldLocation();
        if (currentPos.distanceTo(target) > 2) {
            Microbot.log("Walk failed - player didn't move to target tile, retrying");
            return false; // Let the script retry
        }
        
        // Check for damage (if player took damage, retry immediately)
        double initialHealth = Rs2Player.getHealthPercentage();
        if (Rs2Player.getHealthPercentage() < initialHealth) {
            Microbot.log("Player took damage - retrying obstacle immediately");
            return false; // Let the script retry
        }
        
        // Wait for XP drop with 4 second timeout
        int initialXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
        boolean xpGained = Global.sleepUntil(() -> {
            int currentXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
            return currentXp > initialXp;
        }, 4000); // 4 second timeout
        
        if (!xpGained) {
            Microbot.log("No XP gained within 4 seconds - walk may have failed, retrying");
            return false; // Let the script retry
        }
        
        // Wait for animation to finish (animation ID changes to -1)
        Microbot.log("XP gained! Waiting for animation to finish...");
        Global.sleepUntil(() -> {
            int animationId = Rs2Player.getAnimation();
            return animationId == -1; // Animation finished when ID is -1
        }, 10000); // 10 second timeout for animation to finish
        
        Microbot.log("Animation finished, ready for next obstacle attempt");
        
        return true; // handled this tick
    }

    /**
     * Override getCurrentObstacle to handle the special case of being in the spike course
     */
    @Override
    public net.runelite.api.TileObject getCurrentObstacle() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        
        // Only look for obstacles if we're actually in the spike course (plane 3)
        // Before that, we need to pay and climb down the ladder first
        if (playerLocation.getPlane() == 3) {
            // Get the current obstacle based on the index
            List<AgilityObstacleModel> obstacles = getObstacles();
            if (currentObstacleIndex < obstacles.size()) {
                AgilityObstacleModel currentObstacle = obstacles.get(currentObstacleIndex);
                
                // For obstacle 3 (spikes), there's no game object - return null to trigger timed tile-walking
                if (currentObstacleIndex == obstacles.size() - 1) {
                    Microbot.log("On obstacle 3 (spikes) - using timed tile-walking instead of game object");
                    return null; // This will trigger the timed tile-walking logic in handleWalkToStart
                }
                
                var gameObject = Rs2GameObject.getGameObject(currentObstacle.getObjectID(), playerLocation, 10);
                if (gameObject != null) {
                    Microbot.log("Looking for obstacle " + (currentObstacleIndex + 1) + "/" + obstacles.size() + " (ID: " + currentObstacle.getObjectID() + ")");
                    return gameObject;
                }
            }
        }
        
        // If we're not in the course yet (plane 0 or 1), return null to avoid "No agility obstacle found" errors
        // The handleWalkToStart method will handle walking, payment, and ladder descent
        return null;
    }
    
    /**
     * Override getCurrentObstacleIndex to return our tracked index
     */
    @Override
    public int getCurrentObstacleIndex() {
        return currentObstacleIndex;
    }
    
    /**
     * Override isObstacleComplete to advance to next obstacle
     */
    @Override
    public boolean isObstacleComplete(int currentXp, int previousXp, long lastMovingTime, int waitDelay) {
        // Check if we got XP (obstacle completed)
        if (currentXp > previousXp) {
            // Advance to next obstacle
            currentObstacleIndex++;
            List<AgilityObstacleModel> obstacles = getObstacles();
            Microbot.log("XP gained! Current obstacle index: " + currentObstacleIndex + ", obstacles size: " + obstacles.size());
            
            if (currentObstacleIndex >= obstacles.size()) {
                // For BrimhavenSpike, stay on obstacle 3 (spikes) forever instead of looping back
                currentObstacleIndex = obstacles.size() - 1; // Stay on last obstacle (spikes)
                Microbot.log("Completed all obstacles, staying on obstacle 3 (spikes) for timed tile-walking");
            } else {
                Microbot.log("Obstacle completed! Moving to obstacle " + (currentObstacleIndex + 1) + "/" + obstacles.size());
            }
            
            return true;
        }
        
        // Check if still moving/animating
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return false;
        }
        
        // Check if we've waited long enough after movement stopped
        return System.currentTimeMillis() - lastMovingTime >= waitDelay;
    }
}
