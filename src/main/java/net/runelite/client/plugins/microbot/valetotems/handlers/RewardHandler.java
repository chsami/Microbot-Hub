package net.runelite.client.plugins.microbot.valetotems.handlers;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.enums.GameObjectId;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.microbot.valetotems.models.GameSession;
import net.runelite.client.plugins.microbot.valetotems.models.TotemProgress;
import net.runelite.client.plugins.microbot.valetotems.utils.CoordinateUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.GameObjectUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.InventoryUtils;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

import org.apache.commons.lang3.RandomUtils;

/**
 * Handles reward collection from offerings piles in the Vale Totems minigame
 */
public class RewardHandler {

    private static final int OFFERINGS_SEARCH_RADIUS = 5;
    private static final long COLLECTION_TIMEOUT_MS = 5000; // 5 seconds
    public static int COLLECTION_FREQUENCY = 5; // Collect every 5 totems (approximately)

    /**
     * Check if offerings are available near a totem location
     * @param totemLocation the totem location to check around
     * @return true if claimable offerings are found
     */
    public static boolean areOfferingsAvailable(TotemLocation totemLocation) {
        try {
            WorldPoint location = totemLocation.getLocation();
            GameObject offerings = GameObjectUtils.findClaimableOfferings(location, OFFERINGS_SEARCH_RADIUS);
            return offerings != null;

        } catch (Exception e) {
            System.err.println("Error checking offerings availability: " + e.getMessage());
            return false;
        }
    }

    /**
     * Collect offerings from a totem location
     * @param totemLocation the totem location to collect from
     * @param progress the totem progress tracker
     * @return true if offerings were successfully collected
     */
    public static boolean collectOfferings(TotemLocation totemLocation, TotemProgress progress, GameSession gameSession) {
        try {
            WorldPoint location = totemLocation.getLocation();
            
            // Check if already collected for this totem
            if (progress.areOfferingsCollected()) {
                return true;
            }

            if (!shouldCollectOfferings(gameSession)) {
                return false;
            }

            // Find claimable offerings nearby using string search
            GameObject offerings = GameObjectUtils.findClaimableOfferings(location, OFFERINGS_SEARCH_RADIUS);
            
            if (offerings == null) {
                System.out.println("No claimable offerings found at " + totemLocation.getDescription());
                return false;
            }

            // Check offerings state
            GameObjectId offeringsState = GameObjectUtils.getOfferingsStateNearLocation(location, OFFERINGS_SEARCH_RADIUS);
            if (offeringsState == null || !GameObjectId.hasClaimableOfferings(offeringsState.getId())) {
                return false;
            }

            System.out.println("Collecting offerings at " + totemLocation.getDescription());

            // Record initial offering count
            int initialOfferings = InventoryUtils.getValeOfferingCount();

            // Ensure we're close enough
            if (!CoordinateUtils.isAtTotemLocation(totemLocation, 5)) {
                System.err.println("Not close enough to offerings pile");
                return false;
            }

            // Interact with offerings to claim
            boolean claimed = GameObjectUtils.interactWithObject(offerings, "Claim");
            
            if (claimed) {
                // Wait for collection animation and inventory update
                long startTime = System.currentTimeMillis();
                while (InventoryUtils.getValeOfferingCount() == initialOfferings && 
                       System.currentTimeMillis() - startTime < COLLECTION_TIMEOUT_MS) {
                    sleep(100);
                }

                // Check if we collected any offerings
                int finalOfferings = InventoryUtils.getValeOfferingCount();
                int collected = finalOfferings - initialOfferings;
                
                if (collected > 0) {
                    progress.setOfferingsCollected(true);
                    System.out.println("Collected " + collected + " vale offerings");
                    return true;
                } else {
                    System.out.println("No offerings collected - pile may be empty");
                    return false;
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error collecting offerings: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if we should collect offerings based on game session progress
     * @param gameSession the current game session
     * @return true if it's time to collect offerings
     */
    public static boolean shouldCollectOfferings(GameSession gameSession) {
        try {
            int completedRounds = gameSession.getTotalRounds();

            // Collect approximately every configured amount of rounds
            boolean roundBased = (completedRounds % COLLECTION_FREQUENCY) == 0 && completedRounds > 0;

            // Add littre random chance to collect offerings
            boolean randomChance = RandomUtils.nextInt(1, 100) < 10;
            
            return roundBased || randomChance;

        } catch (Exception e) {
            System.err.println("Error checking if should collect offerings: " + e.getMessage());
            return false;
        }
    }

    /**
     * Collect all available offerings from all completed totems in the current round
     * @param gameSession the current game session
     * @return number of totems where offerings were collected
     */
    public static int collectAllAvailableOfferings(GameSession gameSession) {
        int collected = 0;
        
        try {
            for (TotemLocation location : TotemLocation.values()) {
                TotemProgress progress = gameSession.getTotemProgress(location);
                
                if (progress != null && progress.isCompletelyFinished() && !progress.areOfferingsCollected()) {
                    if (areOfferingsAvailable(location)) {
                        // Navigate to totem if needed
                        if (!CoordinateUtils.isAtTotemLocation(location, 3)) {
                            NavigationHandler.navigateToTotem(location, gameSession);
                        }
                        
                        if (collectOfferings(location, progress, gameSession)) {
                            collected++;
                            gameSession.incrementOfferingsCollected();
                        }
                    }
                }
            }

            if (collected > 0) {
                System.out.println("Collected offerings from " + collected + " totems");
            }

        } catch (Exception e) {
            System.err.println("Error collecting all available offerings: " + e.getMessage());
        }
        
        return collected;
    }

    /**
     * Get the total value/count of offerings that could be collected
     * @param gameSession the current game session
     * @return estimated number of offerings available for collection
     */
    public static int getAvailableOfferingsCount(GameSession gameSession) {
        int availableCount = 0;
        
        try {
            for (TotemLocation location : TotemLocation.values()) {
                TotemProgress progress = gameSession.getTotemProgress(location);
                
                if (progress != null && progress.isCompletelyFinished() && !progress.areOfferingsCollected()) {
                    if (areOfferingsAvailable(location)) {
                        // Estimate 1-3 offerings per completed totem
                        availableCount += 2; // Average estimate
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error calculating available offerings: " + e.getMessage());
        }
        
        return availableCount;
    }

    /**
     * Check offerings status at a specific location
     * @param totemLocation the totem location to check
     * @return formatted string with offerings status
     */
    public static String getOfferingsStatus(TotemLocation totemLocation) {
        try {
            WorldPoint location = totemLocation.getLocation();
            GameObjectId offeringsState = GameObjectUtils.getOfferingsStateNearLocation(location, OFFERINGS_SEARCH_RADIUS);
            
            if (offeringsState == null) {
                return "No offerings pile found";
            }
            
            switch (offeringsState) {
                case OFFERINGS_MANY:
                    return "Many offerings available";
                case OFFERINGS_SOME:
                    return "Some offerings available";
                case OFFERINGS_EMPTY:
                    return "Offerings pile empty";
                default:
                    return "Unknown offerings state";
            }

        } catch (Exception e) {
            return "Error checking offerings: " + e.getMessage();
        }
    }

    /**
     * Perform emergency collection of all offerings (for error recovery or end of session)
     * @param gameSession the current game session
     * @return true if emergency collection completed
     */
    public static boolean emergencyCollectAllOfferings(GameSession gameSession) {
        try {
            System.out.println("Emergency collection of all offerings initiated");
            
            int totalCollected = 0;
            
            for (TotemLocation location : TotemLocation.values()) {
                try {
                    // Navigate to each totem location
                    if (NavigationHandler.navigateToTotem(location, gameSession)) {
                        
                        // Try to collect offerings regardless of progress state
                        GameObject offerings = GameObjectUtils.findClaimableOfferings(
                                location.getLocation(), OFFERINGS_SEARCH_RADIUS);
                        
                        if (offerings != null) {
                            if (GameObjectUtils.interactWithObject(offerings, "Claim")) {
                                totalCollected++;
                                sleep(1000); // Brief delay between collections
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error collecting from " + location.name() + ": " + e.getMessage());
                    // Continue with next location
                }
            }
            
            System.out.println("Emergency collection completed. Collected from " + totalCollected + " locations.");
            return totalCollected > 0;

        } catch (Exception e) {
            System.err.println("Error during emergency collection: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get comprehensive reward statistics
     * @param gameSession the current game session
     * @return formatted string with reward statistics
     */
    public static String getRewardStatistics(GameSession gameSession) {
        try {
            int inventoryOfferings = InventoryUtils.getValeOfferingCount();
            int totalCollected = gameSession.getTotalOfferingsCollected();
            int availableForCollection = getAvailableOfferingsCount(gameSession);
            
            // Calculate collection efficiency
            int completedTotems = 0;
            int collectedTotems = 0;
            
            for (TotemLocation location : TotemLocation.values()) {
                TotemProgress progress = gameSession.getTotemProgress(location);
                if (progress != null && progress.isCompletelyFinished()) {
                    completedTotems++;
                    if (progress.areOfferingsCollected()) {
                        collectedTotems++;
                    }
                }
            }
            
            double collectionRate = completedTotems > 0 ? 
                (double) collectedTotems / completedTotems * 100 : 0;
            
            return String.format("Rewards: Inventory=%d, Total=%d, Available=%d, Rate=%.1f%%",
                    inventoryOfferings, totalCollected, availableForCollection, collectionRate);

        } catch (Exception e) {
            return "Error calculating reward statistics: " + e.getMessage();
        }
    }

    /**
     * Check if inventory has space for more offerings
     * @return true if inventory can hold more offerings
     */
    public static boolean hasSpaceForMoreOfferings() {
        return !InventoryUtils.isInventoryFull() && InventoryUtils.hasInventorySpace(5);
    }

    /**
     * Prioritize which totem to collect offerings from first
     * @param gameSession the current game session
     * @return the highest priority totem location for collection, or null if none
     */
    public static TotemLocation getPriorityOfferingsLocation(GameSession gameSession) {
        try {
            TotemLocation currentLocation = gameSession.getCurrentTotem();
            
            // Check current totem first
            if (currentLocation != null) {
                TotemProgress progress = gameSession.getTotemProgress(currentLocation);
                if (progress != null && progress.needsOfferingsCollection() && 
                    areOfferingsAvailable(currentLocation)) {
                    return currentLocation;
                }
            }
            
            // Check other completed totems
            for (TotemLocation location : TotemLocation.values()) {
                TotemProgress progress = gameSession.getTotemProgress(location);
                if (progress != null && progress.needsOfferingsCollection() && 
                    areOfferingsAvailable(location)) {
                    return location;
                }
            }
            
            return null;

        } catch (Exception e) {
            System.err.println("Error finding priority offerings location: " + e.getMessage());
            return null;
        }
    }
} 