package net.runelite.client.plugins.microbot.valetotems.handlers;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.valetotems.enums.BankLocation;
import net.runelite.client.plugins.microbot.valetotems.enums.GameObjectId;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.microbot.valetotems.utils.CoordinateUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.GameObjectUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.InventoryUtils;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.valetotems.handlers.FletchingHandler;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.List;

import org.apache.commons.lang3.RandomUtils;

/**
 * Handles navigation between different locations in the Vale Totems minigame
 */
public class NavigationHandler {

    private static final int ARRIVAL_TOLERANCE = 7; // Distance to consider "arrived"
    private static final int ENT_TRAIL_SEARCH_RADIUS = 10;
    private static final long WALK_TIMEOUT_MS = 30000; // 30 seconds
    
    // Track processed ent trail pairs to avoid repeating them
    private static final java.util.Set<String> processedEntTrailPairs = new java.util.HashSet<>();
    
    // Track failed ent trail pairs to avoid immediate retries
    private static final java.util.Set<String> failedEntTrailPairs = new java.util.HashSet<>();
    
    // Prevent immediate fletching after walking over ent trails
    private static boolean justWalkedOverEntTrails = false;
    
    // Track if we've already walked over ent trails during this navigation session
    private static boolean hasWalkedOverEntTrailsThisNavigation = false;
    
    // Timer-based cooldowns for camera and walking actions (non-blocking)
    private static long lastCameraTurnTime = 0;
    private static long lastWalkActionTime = 0;
    private static final long CAMERA_TURN_COOLDOWN_MS = 800; // Minimum time between camera turns
    private static final long WALK_ACTION_COOLDOWN_MS = 1200; // Minimum time between walk actions

    /**
     * Checks if camera turn action is on cooldown
     */
    private static boolean isCameraTurnOnCooldown() {
        return System.currentTimeMillis() - lastCameraTurnTime < CAMERA_TURN_COOLDOWN_MS + RandomUtils.nextInt(0,2000);
    }
    
    /**
     * Checks if walking action is on cooldown
     */
    private static boolean isWalkActionOnCooldown() {
        return System.currentTimeMillis() - lastWalkActionTime < WALK_ACTION_COOLDOWN_MS + RandomUtils.nextInt(0,1200);
    }
    
    /**
     * Updates the camera turn timer
     */
    private static void updateCameraTurnTimer() {
        lastCameraTurnTime = System.currentTimeMillis();
    }
    
    /**
     * Updates the walk action timer
     */
    private static void updateWalkActionTimer() {
        lastWalkActionTime = System.currentTimeMillis();
    }

    /**
     * Checks the current pathfinder configuration and provides feedback if agility shortcuts are disabled.
     * This helps users understand why paths might not include agility shortcuts.
     */
    private static void checkPathfinderSettings() {
        try {
            // Get the current pathfinder configuration
            net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig pathfinderConfig = 
                ShortestPathPlugin.getPathfinderConfig();
            
            if (pathfinderConfig != null) {
                // Read the current agility shortcuts setting
                // Note: We need to call refresh() first to ensure settings are up to date
                pathfinderConfig.refresh(CoordinateUtils.getPlayerLocation());
            } else {
                System.out.println("⚠ Could not access pathfinder configuration to check agility shortcuts setting");
            }
        } catch (Exception e) {
            System.err.println("Error checking pathfinder settings: " + e.getMessage());
        }
    }

    /**
     * Navigate to a specific totem location
     * @param totemLocation the target totem location
     * @return true if successfully arrived at the location
     */
    public static boolean navigateToTotem(TotemLocation totemLocation, net.runelite.client.plugins.microbot.valetotems.models.GameSession gameSession) {
        try {
            if (totemLocation == null) {
                return false;
            }

            // Configure pathfinder to use agility shortcuts before generating the path
            checkPathfinderSettings();

            WorldPoint destination = totemLocation.getLocation();

            // Check if already at destination
            if (CoordinateUtils.isAtTotemLocation(totemLocation, ARRIVAL_TOLERANCE)) {
                return true;
            }

            System.out.println("Navigating to " + totemLocation.getDescription());
            
            // Clear processed ent trails for this new navigation run
            clearProcessedEntTrails();
            
            // Reset ent trail flag for new navigation
            justWalkedOverEntTrails = false;

            List<WorldPoint> path = Rs2Walker.getWalkPath(destination);
            if (path == null || path.isEmpty()) {
                System.err.println("Could not generate initial path to " + totemLocation.getDescription());
                return false;
            }

            long startTime = System.currentTimeMillis();
            // Main walking loop
            while (!CoordinateUtils.isAtTotemLocation(totemLocation, ARRIVAL_TOLERANCE) &&
                    System.currentTimeMillis() - startTime < WALK_TIMEOUT_MS) {

                // Priority 1: Check for and handle ent trails.
                // This has ABSOLUTE priority - even if we're fletching, we stop and handle ent trails first
                if (checkAndWalkOverEntTrails()) {
                    System.out.println("Detoured for ent trails. Recalculating path to totem...");
                    path = Rs2Walker.getWalkPath(destination);
                    if (path == null || path.isEmpty()) {
                        System.err.println("Could not recalculate path after ent trails");
                        return false;
                    }
                    continue; // Restart loop with new path
                }

                // If we've strayed too far from our path, recalculate it
                if (isTooFarFromPath(path, 15)) {
                    System.out.println("Strayed from path. Recalculating...");
                    path = Rs2Walker.getWalkPath(destination);
                    if (path == null || path.isEmpty()) {
                        System.err.println("Could not recalculate path to " + totemLocation.getDescription());
                        return false;
                    }
                }

                int currentIndexOnPath = Rs2Walker.getClosestTileIndex(path);
                int tilesForward = RandomUtils.nextInt(25, 32);
                // Reducing lookahead distance for more reliable walking and to prevent getting stuck
                WorldPoint nextCheckpoint = path.get(Math.min(currentIndexOnPath + tilesForward, path.size() - 1));

                // Priority 2: Handle agility shortcuts and doors.
                if (handlePathTransports(path, currentIndexOnPath)) {
                    System.out.println("Used a transport. Recalculating path...");
                    path = Rs2Walker.getWalkPath(destination);
                    if (path == null || path.isEmpty()) {
                        System.err.println("Could not recalculate path after transport");
                        return false;
                    }
                    continue; // Restart loop with new path
                }

                // Priority 3: Fletching on long walks.
                // Only start fletching if the next step is a significant distance away.
                if (CoordinateUtils.getPlayerLocation().distanceTo(nextCheckpoint) > 13) {
                    
                    // Don't start fletching immediately after walking over ent trails
                    // Let the player walk a bit first to resume normal navigation
                    if (justWalkedOverEntTrails) {
                        System.out.println("Skipping fletching this loop - just walked over ent trails, resuming navigation first");
                        justWalkedOverEntTrails = false; // Reset the flag after one walking cycle
                    } else {
                        int needed = 4 - InventoryUtils.getBowCount();
                        if (FletchingHandler.startFletchingWhileWalking(needed, gameSession)) { // Start fletching 4 bows
                            System.out.println("Long walk detected. Pausing to fletch...");
                            
                            // Wait until fletching is complete before continuing to walk
                            // BUT also check for ent trails periodically and interrupt if found
                            long fletchStartTime = System.currentTimeMillis();
                            while (FletchingHandler.isFletchingWhileWalking() &&
                                   System.currentTimeMillis() - fletchStartTime < 20000) { // 20 sec timeout
                                
                                // Check for ent trails during fletching - they have absolute priority
                                List<GameObject> nearbyEntTrails = GameObjectUtils.findGameObjectsByName(
                                        GameObjectId.ENT_TRAIL_1.getSearchTerm(), 
                                        CoordinateUtils.getPlayerLocation(), 
                                        ENT_TRAIL_SEARCH_RADIUS);
                                
                                if (!hasWalkedOverEntTrailsThisNavigation && nearbyEntTrails.size() >= 2) {
                                    System.out.println("Ent trails detected during fletching - interrupting to prioritize trails");
                                    FletchingHandler.stopFletchingWhileWalking();
                                    break; // Exit fletching loop to handle ent trails in main loop
                                }

                                // check if we have fletched the target number of bows
                                if (InventoryUtils.getBowCount() >= 4) {
                                    FletchingHandler.stopFletchingWhileWalking();
                                    break; // Exit fletching loop to handle ent trails in main loop
                                }

                                sleep(200);
                            }
                            
                            System.out.println("Fletching complete. Resuming navigation...");
                            // After fletching, recalculate path as we've been standing still
                            path = Rs2Walker.getWalkPath(destination);
                            continue; // Restart loop
                        }
                    }
                }

                // Priority 4: Regular walking.
                // Only perform actions if not on cooldown (timer-based, non-blocking)
                if (!isWalkActionOnCooldown()) {
                    LocalPoint localNextCheckpoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), nextCheckpoint);
                    if (localNextCheckpoint != null && Rs2Camera.isTileOnScreen(localNextCheckpoint)) {
                        // Camera turn with cooldown check
                        if (!isCameraTurnOnCooldown()) {
                            int turnCameraChance = RandomUtils.nextInt(1, 100);
                            if (turnCameraChance < 30) {
                                Rs2Camera.turnTo(localNextCheckpoint);
                                updateCameraTurnTimer();
                            }
                        }
                        
                        boolean walkSuccess = Rs2Walker.walkFastCanvas(nextCheckpoint);
                        updateWalkActionTimer();
                        
                        // Implement fallback system if walkFastCanvas fails (likely due to unloaded tiles)
                        if (!walkSuccess) {
                            handleWalkingFallback(path, currentIndexOnPath, tilesForward);
                        }
                    } else {
                        // Camera turn with cooldown check
                        if (localNextCheckpoint != null && !isCameraTurnOnCooldown()) {
                            Rs2Camera.turnTo(localNextCheckpoint);
                            updateCameraTurnTimer();
                        }
                        
                        boolean walkSuccess = Rs2Walker.walkFastCanvas(nextCheckpoint);
                        updateWalkActionTimer();
                        
                        // Implement the same fallback system for this branch
                        if (!walkSuccess) {
                            handleWalkingFallback(path, currentIndexOnPath, tilesForward);
                        }
                    }
                }

                // Non-blocking short sleep to prevent excessive CPU usage
                sleep(50); 
            }

            boolean arrived = CoordinateUtils.isAtTotemLocation(totemLocation, ARRIVAL_TOLERANCE);

            if (arrived) {
                System.out.println("Successfully arrived at " + totemLocation.getDescription());
            } else {
                System.err.println("Failed to reach totem: " + totemLocation.getDescription());
            }

            return arrived;

        } catch (Exception e) {
            System.err.println("Error navigating to totem: " + e.getMessage());
            return false;
        }
    }

    /**
     * Navigate back to the bank from current location
     * @return true if successfully arrived at bank
     */
    public static boolean navigateToBank() {
        try {
            WorldPoint bankLocation = BankLocation.PLAYER_STANDING_TILE.getLocation();

            // Check if already at bank
            if (CoordinateUtils.isAtBankingPosition()) {
                return true;
            }

            System.out.println("Returning to bank");

            // Simple blocking walk to bank - no need for concurrent actions while banking
            boolean walked = Rs2Walker.walkTo(bankLocation);
            if (walked) {
                return CoordinateUtils.isAtBankingPosition();
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error navigating to bank: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check for nearby ent trails and walk over them for bonus points
     * Only processes one pair of trails per navigation session to limit interruptions
     * @return true if walked over any ent trails
     */
    public static boolean checkAndWalkOverEntTrails() {
        try {
            // If we've already walked over ent trails during this navigation, skip
            if (hasWalkedOverEntTrailsThisNavigation) {
                return false;
            }
            
            final WorldPoint playerLocation = CoordinateUtils.getPlayerLocation();
            
            List<GameObject> entTrails = findAndFilterEntTrails(playerLocation, ENT_TRAIL_SEARCH_RADIUS);

            if (entTrails.size() < 2) {
                return false; // Need at least 2 trails to form a pair
            }

            // Check if the closest two trails form a valid pair (within 7 tiles of each other)
            GameObject firstTrail = entTrails.get(0);
            GameObject secondTrail = entTrails.get(1);
            
            if (firstTrail.getWorldLocation().distanceTo(secondTrail.getWorldLocation()) > 7) {
                return false; // Trails are too far apart to be a pair
            }

            // Store locations as they might disappear
            WorldPoint firstTrailLocation = firstTrail.getWorldLocation();
            WorldPoint secondTrailLocation = secondTrail.getWorldLocation();
            String pairId = createTrailPairId(firstTrailLocation, secondTrailLocation);
            
            // Check if we've already processed this pair
            if (processedEntTrailPairs.contains(pairId)) {
                return false; // Already processed this pair
            }
            
            // Check if this pair has failed recently
            if (failedEntTrailPairs.contains(pairId)) {
                return false; // Skip failed pairs until cleared
            }

            // STOP ANY ONGOING FLETCHING - Ent trails have absolute priority
            boolean wasFletchingBeforeEntTrails = FletchingHandler.isFletchingWhileWalking();
            if (wasFletchingBeforeEntTrails) {
                System.out.println("Stopping fletching to prioritize ent trails");
                FletchingHandler.stopFletchingWhileWalking();
                // Wait a moment for fletching to stop
                sleepUntil(() -> !FletchingHandler.isFletchingWhileWalking(), 2000);
            }

            System.out.println("Pausing navigation to walk over ent trail pair: " + pairId);
            
            boolean firstVisited = false;
            boolean secondVisited = false;
            int retryCount = 0;

            // lets add one retry here
            while ((!firstVisited || !secondVisited) && retryCount < 3) {
                retryCount++;
                
                // Get current player location for distance calculations
                WorldPoint currentPlayerLocation = CoordinateUtils.getPlayerLocation();
                
                List<GameObject> entTrailsRescan = findAndFilterEntTrails(currentPlayerLocation, ENT_TRAIL_SEARCH_RADIUS);
                if (entTrailsRescan.size() < 2) {
                    break; // Need at least 2 trails to form a pair, if not found maybe they disappeared
                }

                GameObject firstTrailRescan = entTrailsRescan.get(0);
                GameObject secondTrailRescan = entTrailsRescan.get(1);

                WorldPoint firstTrailLocationRescan = firstTrailRescan.getWorldLocation();
                WorldPoint secondTrailLocationRescan = secondTrailRescan.getWorldLocation();

                if (!((firstTrailLocationRescan.distanceTo(firstTrailLocation) < 1 && secondTrailLocationRescan.distanceTo(secondTrailLocation) < 1) || 
                    (firstTrailLocationRescan.distanceTo(secondTrailLocation) < 1 && secondTrailLocationRescan.distanceTo(firstTrailLocation) < 1))) {
                    break; // Trails have moved, so we need to start over
                }

                // Determine which trail to visit first based on distance to player
                // Only consider trails that haven't been visited yet
                WorldPoint closerTrail = null;
                WorldPoint farterTrail = null;
                boolean firstIsCloser = false;
                
                if (!firstVisited && !secondVisited) {
                    // Both need visiting - choose closest first
                    double distanceToFirst = currentPlayerLocation.distanceTo(firstTrailLocation);
                    double distanceToSecond = currentPlayerLocation.distanceTo(secondTrailLocation);
                    
                    if (distanceToFirst <= distanceToSecond) {
                        closerTrail = firstTrailLocation;
                        farterTrail = secondTrailLocation;
                        firstIsCloser = true;
                    } else {
                        closerTrail = secondTrailLocation;
                        farterTrail = firstTrailLocation;
                        firstIsCloser = false;
                    }
                    
                    // Visit closer trail first
                    if (firstIsCloser) {
                        firstVisited = walkOverEntTrail(closerTrail);
                        if (!firstVisited) {
                            continue; // Retry if closer trail failed
                        }
                        secondVisited = walkOverEntTrail(farterTrail);
                    } else {
                        secondVisited = walkOverEntTrail(closerTrail);
                        if (!secondVisited) {
                            continue; // Retry if closer trail failed
                        }
                        firstVisited = walkOverEntTrail(farterTrail);
                    }
                } else {
                    // Only one needs visiting - visit the remaining one
                    if (!firstVisited) {
                        firstVisited = walkOverEntTrail(firstTrailLocation);
                    }
                    if (!secondVisited) {
                        secondVisited = walkOverEntTrail(secondTrailLocation);
                    }
                }
            }
            
            // If we visited both, mark the pair as processed to avoid retrying.
            // This handles cases where one trail might disappear.
            if (firstVisited && secondVisited) {
                processedEntTrailPairs.add(pairId);
                hasWalkedOverEntTrailsThisNavigation = true; // Mark that we've walked over trails this navigation
                System.out.println("Completed ent trail pair attempt: " + pairId + 
                                   " (First: " + firstVisited + ", Second: " + secondVisited + ")");
                System.out.println("Ent trails completed for this navigation session - no more will be processed");
                
                // Set flag to prevent immediate fletching restart
                justWalkedOverEntTrails = true;
                
                // Note: We don't restart fletching here - let the main navigation loop handle that
                // based on the distance to the next checkpoint
                return true; // Return true to force path recalculation
            } else if (!firstVisited) {
                System.out.println("Failed to visit first ent trail: " + pairId + 
                                   " (First: " + firstVisited + ", Second: " + secondVisited + ")");
                // Mark this pair as failed so we don't immediately retry it
                failedEntTrailPairs.add(pairId);
                return false;
            } else if (!secondVisited) {
                System.out.println("Failed to visit second ent trail: " + pairId + 
                                   " (First: " + firstVisited + ", Second: " + secondVisited + ")");
                // Mark this pair as failed so we don't immediately retry it
                failedEntTrailPairs.add(pairId);
                return false;
            }

            // If we failed to visit either (e.g., got stuck), don't mark as processed.
            // Mark as failed so we don't immediately retry
            System.out.println("Failed to complete ent trail pair: " + pairId);
            failedEntTrailPairs.add(pairId);
            return false;

        } catch (Exception e) {
            System.err.println("Error checking ent trails: " + e.getMessage());
            return false;
        }
    }

    private static List<GameObject> findAndFilterEntTrails(WorldPoint playerLocation, int searchRadius) {
        // Search for ent trails nearby using string search
        List<GameObject> entTrails = GameObjectUtils.findGameObjectsByName(
            GameObjectId.ENT_TRAIL_1.getSearchTerm(), playerLocation, searchRadius);
                
        // Filter trails that are within the search radius of the player
        entTrails = entTrails.stream()
            .filter(trail -> trail.getWorldLocation().distanceTo(playerLocation) <= searchRadius)
            .sorted((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(playerLocation),
                    b.getWorldLocation().distanceTo(playerLocation)))
            .collect(java.util.stream.Collectors.toList());

        return entTrails;
    }

    public static boolean walkOverEntTrail(WorldPoint trailLocation) {
        Rs2Walker.walkFastCanvas(trailLocation);
        sleepUntil(() -> CoordinateUtils.getPlayerLocation().distanceTo(trailLocation) < 3, 8000);
        Rs2Walker.walkFastCanvas(trailLocation);
        sleepUntil(() -> CoordinateUtils.getPlayerLocation().distanceTo(trailLocation) < 1, 2000);
        return CoordinateUtils.getPlayerLocation().distanceTo(trailLocation) < 1;
    }

    /**
     * Creates a unique identifier for a trail pair based on their locations
     * @param trail1 location of first trail
     * @param trail2 location of second trail  
     * @return unique string identifier for the pair
     */
    private static String createTrailPairId(WorldPoint trail1, WorldPoint trail2) {
        // Sort the coordinates to ensure consistent ID regardless of order
        WorldPoint first = trail1.getX() < trail2.getX() || 
                          (trail1.getX() == trail2.getX() && trail1.getY() <= trail2.getY()) ? 
                          trail1 : trail2;
        WorldPoint second = first == trail1 ? trail2 : trail1;
        
        return String.format("pair_%d_%d_%d_%d", 
                first.getX(), first.getY(), second.getX(), second.getY());
    }

    /**
     * Clear the processed ent trail pairs (call when starting a new navigation run)
     */
    public static void clearProcessedEntTrails() {
        processedEntTrailPairs.clear();
        justWalkedOverEntTrails = false; // Also reset the fletching prevention flag
        hasWalkedOverEntTrailsThisNavigation = false; // Reset for new navigation session
        System.out.println("Cleared processed ent trail pairs for new navigation run");
    }

    /**
     * Clear failed ent trail pairs (call when a totem is completed)
     */
    public static void clearFailedEntTrails() {
        failedEntTrailPairs.clear();
        System.out.println("Cleared failed ent trail pairs - they can be retried now");
    }
    
    /**
     * Clear both processed and failed ent trail pairs (full reset)
     */
    public static void clearAllEntTrails() {
        processedEntTrailPairs.clear();
        failedEntTrailPairs.clear();
        justWalkedOverEntTrails = false;
        hasWalkedOverEntTrailsThisNavigation = false;
        System.out.println("Cleared all ent trail tracking data");
    }

    /**
     * Emergency navigation back to bank (for error recovery)
     * @return true if successfully returned to bank
     */
    public static boolean emergencyReturnToBank() {
        try {
            System.out.println("Emergency return to bank initiated");

            // Cancel any current walking target just in case
            Rs2Walker.setTarget(null);
            
            WorldPoint bankLocation = BankLocation.PLAYER_STANDING_TILE.getLocation();
            long startTime = System.currentTimeMillis();

            // Use a simple walking loop without concurrent actions for emergencies
            while (!CoordinateUtils.isNearBank(5) &&
                   System.currentTimeMillis() - startTime < WALK_TIMEOUT_MS) {
                
                Rs2Walker.walkTo(bankLocation, 5); // Use a standard blocking walk for simplicity and reliability here
                sleep(600); // Pause between walk commands
            }
            
            boolean arrived = CoordinateUtils.isNearBank(5);
            
            if (arrived) {
                System.out.println("Emergency return to bank successful");
            } else {
                System.err.println("Emergency return to bank failed.");
            }
            
            return arrived;

        } catch (Exception e) {
            System.err.println("Error during emergency return to bank: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the player has strayed too far from the calculated path.
     * @param path The path to check against.
     * @param tolerance The maximum allowed distance from the path.
     * @return true if the player is too far from the path.
     */
    private static boolean isTooFarFromPath(List<WorldPoint> path, int tolerance) {
        WorldPoint playerLoc = CoordinateUtils.getPlayerLocation();
        int closestIndex = Rs2Walker.getClosestTileIndex(path);
        if (closestIndex == -1) {
            // If we can't find ourselves on the path, we are "too far"
            return true;
        }
        WorldPoint closestPointOnPath = path.get(closestIndex);
        return playerLoc.distanceTo(closestPointOnPath) > tolerance;
    }

    /**
     * Calculate estimated travel time in seconds between locations
     * @param from starting location
     * @param to destination location
     * @return estimated time in seconds
     */
    public static double getEstimatedTravelTime(WorldPoint from, WorldPoint to) {
        double walkingSpeed = 3.33;
        int distance = CoordinateUtils.getDistance(from, to);
        return distance / walkingSpeed;
    }

    /**
     * Get navigation status summary
     * @return formatted string with current navigation status
     */
    public static String getNavigationStatus() {
        WorldPoint playerPos = CoordinateUtils.getPlayerLocation();
        TotemLocation nearestTotem = CoordinateUtils.getNearestTotemLocation();
        int distanceToBank = CoordinateUtils.getDistanceToBank();
        
        return String.format("Location: (%d,%d) | Nearest Totem: %s (%d tiles) | Bank: %d tiles | Walking: %s",
                playerPos.getX(), playerPos.getY(),
                nearestTotem != null ? nearestTotem.name() : "None",
                nearestTotem != null ? CoordinateUtils.getDistanceToPlayer(nearestTotem.getLocation()) : -1,
                distanceToBank,
                Rs2Player.isWalking() ? "Yes" : "No");
    }

    /**
     * Handles transport methods (agility shortcuts, doors, etc.) along the path
     * This integrates Rs2Walker's transport handling into our custom walker
     * @param path The current walking path
     * @param currentIndex The current position on the path
     * @return true if a transport was used and position changed
     */
    private static boolean handlePathTransports(List<WorldPoint> path, int currentIndex) {
        try {
            WorldPoint currentPosition = CoordinateUtils.getPlayerLocation();
            
            // Check for doors (from Rs2Walker.handleDoors logic)
            if (currentIndex < path.size() - 1) {
                WorldPoint nextPoint = path.get(currentIndex + 1);
                
                // Simple door detection and handling
                List<net.runelite.api.TileObject> doors = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getAll(
                    obj -> {
                        if (!obj.getWorldLocation().equals(nextPoint)) {
                            return false;
                        }
                        // Convert TileObject to ObjectComposition to check actions
                        net.runelite.api.ObjectComposition comp = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.convertToObjectComposition(obj);
                        return net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.hasAction(comp, "Open") ||
                               net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.hasAction(comp, "Close") ||
                               net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.hasAction(comp, "Pick-lock");
                    }
                );
                
                for (net.runelite.api.TileObject door : doors) {
                    if (net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.interact(door)) {
                        sleep(1000); // Wait for door interaction
                        return true;
                    }
                }
            }
            
            // --- New Agility Shortcut Logic ---
            // We now check if the pathfinder's generated path includes a shortcut,
            // rather than searching for any nearby shortcut. This is more reliable.
            java.util.Map<WorldPoint, java.util.Set<net.runelite.client.plugins.microbot.shortestpath.Transport>> allTransports =
                net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin.getTransports();
 
            if (allTransports != null && !allTransports.isEmpty()) {
                // Look ahead on our path to see if any point is a transport origin
                for (int i = currentIndex; i < Math.min(currentIndex + 15, path.size()); i++) {
                    WorldPoint pathPoint = path.get(i);
                    if (allTransports.containsKey(pathPoint)) {
                        // This point on our path is a transport origin. Find the agility shortcut.
                        for (net.runelite.client.plugins.microbot.shortestpath.Transport transport : allTransports.get(pathPoint)) {
                            if (transport.getType().toString().contains("AGILITY") &&
                                transport.getOrigin() != null &&
                                transport.getDestination() != null &&
                                transport.getObjectId() > 0) {
 
                                // --- Direction Check ---
                                // Ensure the transport destination is actually the next step in our path
                                // to avoid going backwards on two-way shortcuts.
                                boolean transportFollowsPath = false;
                                // Check the next few points on the path to see if the destination matches.
                                for (int j = i + 1; j < Math.min(i + 5, path.size()); j++) {
                                    if (path.get(j).equals(transport.getDestination())) {
                                        transportFollowsPath = true;
                                        break;
                                    }
                                }
 
                                if (!transportFollowsPath) {
                                    System.out.println("Skipping shortcut: " + transport.getOrigin() + " -> " + transport.getDestination() + " (does not follow current path direction)");
                                    continue; // This shortcut goes the wrong way, check the next one.
                                }
 
                                // --- If we reach here, the shortcut is valid and in the right direction ---
                                System.out.println("Path wants to use agility shortcut: " + transport.getType() + " at " + transport.getOrigin());
                                int distanceToOrigin = currentPosition.distanceTo(transport.getOrigin());
 
                                // First, walk directly to the agility shortcut origin if we're not already there
                                if (distanceToOrigin > 2) {
                                    System.out.println("Walking to agility shortcut origin...");
                                    LocalPoint localOrigin = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), transport.getOrigin());
                                    if (localOrigin != null && Rs2Camera.isTileOnScreen(localOrigin)) {
                                        Rs2Walker.walkFastCanvas(transport.getOrigin());
                                    } else {
                                        Rs2Walker.walkMiniMap(transport.getOrigin());
                                    }
                                    sleepUntil(() -> CoordinateUtils.getPlayerLocation().distanceTo(transport.getOrigin()) <= 2, 5000);
                                }
 
                                // Now find and interact with the agility shortcut
                                net.runelite.api.TileObject agilityObj = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.findObjectById(transport.getObjectId());
                                if (agilityObj != null &&
                                    agilityObj.getWorldLocation().distanceTo(transport.getOrigin()) <= 3) {
 
                                    System.out.println("Using agility shortcut: " + transport.getType());
                                    if (net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.interact(agilityObj, transport.getAction())) {
                                        
                                        // Wait until the player has been idle (not walking or animating) for 1.5 seconds
                                        System.out.println("Waiting for agility shortcut to complete...");
                                        
                                        long startTime = System.currentTimeMillis();
                                        long timeout = 15000; // 15s overall timeout
                                        long idleTimeStart = -1;
                                        boolean idleConditionMet = false;

                                        //small sleep to allow for animation to start
                                        sleep(1000);

                                        while (System.currentTimeMillis() - startTime < timeout) {
                                            boolean isIdle = !Rs2Player.isWalking() && Microbot.getClient().getLocalPlayer().getAnimation() == -1;

                                            if (isIdle) {
                                                if (idleTimeStart == -1) {
                                                    idleTimeStart = System.currentTimeMillis();
                                                }
                                                if (System.currentTimeMillis() - idleTimeStart >= 500) {
                                                    idleConditionMet = true;
                                                    break; // Success, idle for 1.5s
                                                }
                                            } else {
                                                idleTimeStart = -1; // Reset if moving/animating
                                            }
                                            sleep(50);
                                        }

                                        if (!idleConditionMet) {
                                            System.err.println("Timed out waiting for player to be idle after agility shortcut.");
                                        }

                                        System.out.println("Agility shortcut completed. New position: " + CoordinateUtils.getPlayerLocation());
                                        return true; // Successfully used the transport
                                    } else {
                                        System.err.println("Failed to interact with agility shortcut");
                                    }
                                } else {
                                    System.err.println("Agility shortcut object not found at expected location");
                                }
                                // We found the transport on our path, so we don't need to check further.
                                // Even if we failed, we should stop and let the main loop recalculate.
                                return true;
                            }
                        }
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error handling path transports: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles walking fallback when the initial target fails (likely due to unloaded tiles)
     * Progressively tries closer targets until one succeeds
     * @param path The current walking path
     * @param currentIndexOnPath Current position on the path
     * @param originalTilesForward The original tiles forward distance that failed
     * @return true if any fallback succeeded
     */
    private static boolean handleWalkingFallback(List<WorldPoint> path, int currentIndexOnPath, int originalTilesForward) {
        System.out.println("Failed to walk to target (likely unloaded tiles). Implementing fallback system...");
        
        // Progressive fallback - try closer and closer targets
        int[] fallbackDistances = {15, 10, 7, 5, 3, 2, 1};
        
        for (int fallbackDistance : fallbackDistances) {
            if (fallbackDistance >= originalTilesForward) continue; // Skip if not actually closer
            
            WorldPoint fallbackCheckpoint = path.get(Math.min(currentIndexOnPath + fallbackDistance, path.size() - 1));
            System.out.println("Trying fallback distance: " + fallbackDistance + " tiles");
            
            // walkFastCanvas handles all the internal checks and minimap fallback
            boolean fallbackSuccess = Rs2Walker.walkFastCanvas(fallbackCheckpoint);
            
            if (fallbackSuccess) {
                System.out.println("Fallback successful at distance: " + fallbackDistance + " tiles");
                return true;
            }
        }
        
        // If all fallbacks failed, try walking to current position + 1 as last resort
        System.out.println("All fallbacks failed, trying to walk to adjacent tile...");
        if (currentIndexOnPath + 1 < path.size()) {
            WorldPoint adjacentTile = path.get(currentIndexOnPath + 1);
            return Rs2Walker.walkFastCanvas(adjacentTile); // This will use minimap if needed
        }
        
        return false;
    }
} 