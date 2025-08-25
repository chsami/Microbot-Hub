package net.runelite.client.plugins.microbot.valetotems.utils;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.enums.GameObjectId;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for interacting with game objects in the Vale Totems minigame
 * Uses Rs2GameObject ready-made methods instead of reinventing functionality
 * Updated to use string-based searching for totem sites and offerings
 */
public class GameObjectUtils {
    
    // Cache configuration
    private static final long CACHE_EXPIRY_MS = 10000; // 10 seconds cache expiry
    private static final int MAX_CACHE_SIZE = 10; // Prevent memory leaks
    
    // GameObject cache to avoid expensive repeated searches
    private static final Map<WorldPoint, CachedGameObject> gameObjectCache = new ConcurrentHashMap<>();
    
    /**
     * Inner class to store cached GameObject with timestamp
     */
    private static class CachedGameObject {
        private final GameObject gameObject;
        private final long timestamp;
        
        public CachedGameObject(GameObject gameObject) {
            this.gameObject = gameObject;
            this.timestamp = System.currentTimeMillis();
        }
        
        public GameObject getGameObject() {
            return gameObject;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }

    /**
     * Get cached GameObject or perform expensive search and cache result
     * @param searchTerm the search term to look for
     * @param location the exact world point location
     * @return the game object at that location, or null if not found
     */
    private static GameObject getCachedObjectAtLocationByName(String searchTerm, WorldPoint location) {
        // Check cache first
        CachedGameObject cached = gameObjectCache.get(location);
        if (cached != null && !cached.isExpired()) {
            Microbot.log("Cache HIT for location: " + location + " (searchTerm: " + searchTerm + ")");
            return cached.getGameObject();
        }
        
        // Cache miss or expired - perform expensive search
        Microbot.log("Cache MISS for location: " + location + " (searchTerm: " + searchTerm + ") - performing search");
        long startTime = System.currentTimeMillis();
        GameObject gameObject = Rs2GameObject.getGameObject(searchTerm, false, location);
        long searchTime = System.currentTimeMillis() - startTime;
        Microbot.log("Search completed in " + searchTime + "ms for location: " + location);
        
        // Cache the result (even if null to avoid repeated searches)
        cacheGameObject(location, gameObject);
        
        return gameObject;
    }
    
    /**
     * Cache a GameObject at a location
     * @param location the world point location
     * @param gameObject the game object to cache (can be null)
     */
    private static void cacheGameObject(WorldPoint location, GameObject gameObject) {
        // Clean up cache if it's getting too large
        if (gameObjectCache.size() >= MAX_CACHE_SIZE) {
            cleanExpiredCache();
            
            // If still too large after cleanup, remove oldest entries
            if (gameObjectCache.size() >= MAX_CACHE_SIZE) {
                gameObjectCache.clear(); // Simple cleanup - remove all
            }
        }
        
        gameObjectCache.put(location, new CachedGameObject(gameObject));
    }
    
    /**
     * Clean expired entries from cache
     */
    private static void cleanExpiredCache() {
        gameObjectCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Clear the entire cache (useful for testing or reset)
     */
    public static void clearCache() {
        gameObjectCache.clear();
    }
    
    /**
     * Invalidate cache for a specific location
     * @param location the world point to invalidate
     */
    public static void invalidateCacheAtLocation(WorldPoint location) {
        gameObjectCache.remove(location);
    }
    
    /**
     * Get cache statistics for debugging
     * @return string with cache info
     */
    public static String getCacheStats() {
        int totalEntries = gameObjectCache.size();
        int expiredEntries = (int) gameObjectCache.values().stream()
                .mapToLong(cached -> cached.isExpired() ? 1 : 0)
                .sum();
        return String.format("Cache: %d total entries, %d expired", totalEntries, expiredEntries);
    }

    /**
     * Find the nearest game object by ID
     * @param objectId the game object ID to search for
     * @return the nearest game object, or null if not found
     */
    public static GameObject findNearestObject(int objectId) {
        return Rs2GameObject.getGameObject(objectId);
    }

    /**
     * Find the nearest game object by search term (string-based)
     * @param searchTerm the search term to look for
     * @return the nearest game object, or null if not found
     */
    public static GameObject findNearestObjectByName(String searchTerm) {
        return Rs2GameObject.getGameObject(searchTerm, false);
    }

    /**
     * Find game object by ID and exact location
     * @param objectId the game object ID
     * @param location the exact world point location
     * @return the game object at that location, or null if not found
     */
    public static GameObject findObjectAtLocation(int objectId, WorldPoint location) {
        return Rs2GameObject.getGameObject(objectId, location);
    }

    /**
     * Find game object by search term and location
     * @param searchTerm the search term to look for
     * @param location the exact world point location
     * @return the game object at that location, or null if not found
     */
    public static GameObject findObjectAtLocationByName(String searchTerm, WorldPoint location) {
        return getCachedObjectAtLocationByName(searchTerm, location);
    }

    /**
     * Find all game objects by ID within a radius
     * @param objectId the game object ID to search for
     * @param location the center point to search from
     * @param radius the search radius in tiles
     * @return list of matching game objects
     */
    public static List<GameObject> findGameObjects(int objectId, WorldPoint location, int radius) {
        return Rs2GameObject.getGameObjects(obj -> obj.getId() == objectId, location, radius);
    }

    /**
     * Find all game objects by search term within a radius
     * @param searchTerm the search term to look for
     * @param location the center point to search from
     * @param radius the search radius in tiles
     * @return list of matching game objects
     */
    public static List<GameObject> findGameObjectsByName(String searchTerm, WorldPoint location, int radius) {
        return Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches(searchTerm, false), location, radius);
    }

    /**
     * Interact with a game object using Rs2GameObject ready-made method
     * @param gameObject the game object to interact with
     * @param action the action to perform (e.g., "Build", "Decorate", "Claim")
     * @return true if the interaction was successful
     */
    public static boolean interactWithObject(GameObject gameObject, String action) {
        return Rs2GameObject.interact(gameObject, action);
    }

    /**
     * Find and interact with the nearest object by ID
     * @param objectId the game object ID
     * @param action the action to perform
     * @return true if successful
     */
    public static boolean findAndInteract(int objectId, String action) {
        return Rs2GameObject.interact(objectId, action);
    }

    /**
     * Find and interact with the nearest object by search term
     * @param searchTerm the search term to look for
     * @param action the action to perform
     * @return true if successful
     */
    public static boolean findAndInteractByName(String searchTerm, String action) {
        return Rs2GameObject.interact(searchTerm, action);
    }

    /**
     * Find and interact with object at specific location
     * @param objectId the game object ID
     * @param location the world point location
     * @param action the action to perform
     * @return true if successful
     */
    public static boolean findAndInteractAtLocation(int objectId, WorldPoint location, String action) {
        GameObject obj = Rs2GameObject.getGameObject(objectId, location);
        return Rs2GameObject.interact(obj, action);
    }

    /**
     * Find and interact with object at specific location by search term
     * @param searchTerm the search term to look for
     * @param location the world point location
     * @param action the action to perform
     * @return true if successful
     */
    public static boolean findAndInteractAtLocationByName(String searchTerm, WorldPoint location, String action) {
        GameObject obj = Rs2GameObject.getGameObject(searchTerm, false, location);
        return Rs2GameObject.interact(obj, action);
    }

    /**
     * Check if a specific game object exists at a location
     * @param objectId the game object ID to check for
     * @param location the world point to check
     * @return true if the object exists at that location
     */
    public static boolean objectExistsAtLocation(int objectId, WorldPoint location) {
        return Rs2GameObject.getGameObject(objectId, location) != null;
    }

    /**
     * Check if a specific game object exists at a location by search term
     * @param searchTerm the search term to look for
     * @param location the world point to check
     * @return true if the object exists at that location
     */
    public static boolean objectExistsAtLocationByName(String searchTerm, WorldPoint location) {
        return Rs2GameObject.getGameObject(searchTerm, false, location) != null;
    }

    /**
     * Check if a specific game object exists (using Rs2GameObject.exists)
     * @param objectId the game object ID to check for
     * @return true if the object exists anywhere nearby
     */
    public static boolean objectExists(int objectId) {
        return Rs2GameObject.exists(objectId);
    }

    /**
     * Get the distance to the nearest object of a given ID
     * @param objectId the game object ID
     * @return distance in tiles, or -1 if object not found
     */
    public static int getDistanceToNearestObject(int objectId) {
        GameObject obj = Rs2GameObject.getGameObject(objectId);
        if (obj == null) {
            return -1;
        }
        return obj.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation());
    }

    /**
     * Check what type of totem site/state exists at a location using string search
     * @param location the world point to check
     * @return the GameObjectId enum for the totem state, or null if no totem found
     */
    public static GameObjectId getTotemStateAtLocation(WorldPoint location) {
        // Search for any totem object at the location using cached method
        Microbot.log("getTotemStateAtLocation started: " + (System.currentTimeMillis()));
        GameObject totem = getCachedObjectAtLocationByName(GameObjectId.TOTEM_SITE.getSearchTerm(), location);
        Microbot.log("getTotemStateAtLocation finished: " + (System.currentTimeMillis()));
        if (totem != null) {
            // Get the ObjectComposition to access actions
            try {
                Microbot.log("getTotemStateAtLocation findObjectComposition started: " + (System.currentTimeMillis()));
                String[] actions = Rs2GameObject.findObjectComposition(totem.getId()).getActions();
                Microbot.log("getTotemStateAtLocation findObjectComposition finished: " + (System.currentTimeMillis()));
                if (actions != null) {
                    List<String> actionList = Arrays.asList(actions);
                    
                    // Check for specific actions to determine totem state
                    if (actionList.contains("Build")) {
                        return GameObjectId.TOTEM_SITE;
                    } else if (actionList.contains("Decorate")) {
                        return GameObjectId.TOTEM_READY_FOR_DECORATION;
                    } else {
                        // If no Build or Decorate action, assume it's ready for carving
                        return GameObjectId.EMPTY_TOTEM;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error getting actions for totem: " + e.getMessage());
                return null;
            }
        }

        return null; // No totem object found at the location.
    }

    /**
     * Check if offerings are available at a location using string search
     * @param location the world point to check near
     * @param radius the radius to search within
     * @return the GameObjectId for the offerings state, or null if no offerings found
     */
    public static GameObjectId getOfferingsStateNearLocation(WorldPoint location, int radius) {
        // Use string-based search for offerings
        String offeringsSearchTerm = GameObjectId.OFFERINGS_MANY.getSearchTerm();
        
        List<GameObject> nearbyObjects = Rs2GameObject.getGameObjects(
            Rs2GameObject.nameMatches(offeringsSearchTerm, false), location, radius
        );
        
        if (!nearbyObjects.isEmpty()) {
            // Default to assuming offerings are available since we found an offerings pile
            // May need refinement to distinguish between different offering states
            return GameObjectId.OFFERINGS_MANY;
        }
        return null;
    }

    /**
     * Find claimable offerings near a location using string search
     * @param location the world point to search near
     * @param radius the search radius
     * @return the offerings game object if claimable, null otherwise
     */
    public static GameObject findClaimableOfferings(WorldPoint location, int radius) {
        // Use string-based search for offerings
        String offeringsSearchTerm = GameObjectId.OFFERINGS_MANY.getSearchTerm();
        
        return Rs2GameObject.getGameObject(
            offeringsSearchTerm, false, location, radius
        );
    }

    /**
     * Check if player is close enough to interact with an object
     * @param gameObject the game object to check
     * @param maxDistance maximum interaction distance
     * @return true if within interaction range
     */
    public static boolean isWithinInteractionRange(GameObject gameObject, int maxDistance) {
        if (gameObject == null) {
            return false;
        }
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return gameObject.getWorldLocation().distanceTo(playerLocation) <= maxDistance;
    }

    /**
     * Wait for a game object to appear at a location using string search
     * @param searchTerm the search term to look for
     * @param location the location to check
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if object appeared within timeout
     */
    public static boolean waitForObjectAtLocationByName(String searchTerm, WorldPoint location, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (Rs2GameObject.getGameObject(searchTerm, false, location) != null) {
                return true;
            }
            
            try {
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }

    /**
     * Wait for a game object to appear at a location
     * @param objectId the expected object ID
     * @param location the location to check
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if object appeared within timeout
     */
    public static boolean waitForObjectAtLocation(int objectId, WorldPoint location, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (Rs2GameObject.getGameObject(objectId, location) != null) {
                return true;
            }
            
            try {
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }

    /**
     * Wait for a specific totem state to appear at a location.
     * @param expectedState the expected totem state (GameObjectId)
     * @param location the location to check
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if state appeared within timeout
     */
    public static boolean waitForTotemStateAtLocation(GameObjectId expectedState, WorldPoint location, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        invalidateCacheAtLocation(location);

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (getTotemStateAtLocation(location) == expectedState) {
                return true;
            }

            invalidateCacheAtLocation(location);

            try {
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Check if the player can reach a specific game object
     * @param gameObject the game object to check
     * @return true if the object is reachable
     */
    public static boolean canReachObject(GameObject gameObject) {
        return Rs2GameObject.isReachable(gameObject);
    }

    /**
     * Check if there's line of sight to a game object
     * @param gameObject the game object to check
     * @return true if there's line of sight
     */
    public static boolean hasLineOfSight(GameObject gameObject) {
        return Rs2GameObject.hasLineOfSight(gameObject);
    }
} 