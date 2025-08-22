package net.runelite.client.plugins.microbot.valetotems.utils;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.enums.BankLocation;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for coordinate calculations and pathfinding in the Vale Totems minigame
 */
public class CoordinateUtils {

    /**
     * Get the current player's world location
     * @return player's current WorldPoint
     */
    public static WorldPoint getPlayerLocation() {
        return Microbot.getClient().getLocalPlayer().getWorldLocation();
    }

    /**
     * Calculate distance between two world points
     * @param point1 first world point
     * @param point2 second world point
     * @return distance in tiles
     */
    public static int getDistance(WorldPoint point1, WorldPoint point2) {
        return point1.distanceTo(point2);
    }

    /**
     * Get distance from player to a specific world point
     * @param destination the destination point
     * @return distance in tiles
     */
    public static int getDistanceToPlayer(WorldPoint destination) {
        return getDistance(getPlayerLocation(), destination);
    }

    /**
     * Check if player is within a certain distance of a location
     * @param location the target location
     * @param maxDistance maximum allowed distance
     * @return true if within range
     */
    public static boolean isPlayerNear(WorldPoint location, int maxDistance) {
        return getDistanceToPlayer(location) <= maxDistance;
    }

    /**
     * Get the nearest totem location to the player
     * @return the closest TotemLocation enum
     */
    public static TotemLocation getNearestTotemLocation() {
        WorldPoint playerPos = getPlayerLocation();
        TotemLocation nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (TotemLocation location : TotemLocation.values()) {
            int distance = getDistance(playerPos, location.getLocation());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = location;
            }
        }

        return nearest;
    }

    /**
     * Get distance from player to the nearest totem location
     * @return distance to nearest totem in tiles
     */
    public static int getDistanceToNearestTotem() {
        TotemLocation nearest = getNearestTotemLocation();
        return nearest != null ? getDistanceToPlayer(nearest.getLocation()) : -1;
    }

    /**
     * Get distance from player to the bank
     * @return distance to bank in tiles
     */
    public static int getDistanceToBank() {
        return getDistanceToPlayer(BankLocation.BANK_BOOTH.getLocation());
    }

    /**
     * Check if player is near the optimal banking position
     * @return true if player is within 5 tiles of the banking tile
     */
    public static boolean isAtBankingPosition() {
        return getDistanceToPlayer(BankLocation.PLAYER_STANDING_TILE.getLocation()) <= 5;
    }

    /**
     * Check if player is near the banking area
     * @param maxDistance maximum distance to consider "near"
     * @return true if near banking area
     */
    public static boolean isNearBank(int maxDistance) {
        return isPlayerNear(BankLocation.BANK_BOOTH.getLocation(), maxDistance);
    }

    /**
     * Check if player is at a specific totem location
     * @param totemLocation the totem location to check
     * @param tolerance distance tolerance in tiles
     * @return true if player is at the totem location
     */
    public static boolean isAtTotemLocation(TotemLocation totemLocation, int tolerance) {
        return isPlayerNear(totemLocation.getLocation(), tolerance);
    }

    /**
     * Get estimated time to walk to a destination
     * @param destination the target world point
     * @param averageSpeed tiles per second (typically 1-2 for walking)
     * @return estimated time in seconds
     */
    public static double getEstimatedWalkTime(WorldPoint destination, double averageSpeed) {
        int distance = getDistanceToPlayer(destination);
        return distance / averageSpeed;
    }

    /**
     * Get the optimal route order for visiting remaining totems
     * @param currentLocation current player location
     * @param remainingTotems list of totems still to visit
     * @return ordered list of totems for optimal route
     */
    public static List<TotemLocation> getOptimalRoute(WorldPoint currentLocation, List<TotemLocation> remainingTotems) {
        // For Vale Totems, the order is fixed (1-5), but this method could be useful
        // for other optimizations or if the order becomes flexible
        List<TotemLocation> orderedRoute = new ArrayList<>(remainingTotems);
        orderedRoute.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        return orderedRoute;
    }

    /**
     * Check if a location is within the Vale of Anima area
     * @param location the world point to check
     * @return true if within the minigame area
     */
    public static boolean isInValeOfAnima(WorldPoint location) {
        // Define the bounds of the Vale of Anima area
        // Based on the coordinates in the documentation
        int minX = 1340, maxX = 1420;
        int minY = 3270, maxY = 3380;
        int plane = 0;

        return location.getX() >= minX && location.getX() <= maxX &&
               location.getY() >= minY && location.getY() <= maxY &&
               location.getPlane() == plane;
    }

    /**
     * Get a safe walking position near a totem (avoiding NPCs/obstacles)
     * @param totemLocation the target totem location
     * @return a nearby safe position to walk to
     */
    public static WorldPoint getSafeTotemPosition(TotemLocation totemLocation) {
        // Return a position 1-2 tiles away from the exact totem location
        // to avoid potential pathfinding issues
        WorldPoint totemPos = totemLocation.getLocation();
        return new WorldPoint(totemPos.getX() - 1, totemPos.getY() - 1, totemPos.getPlane());
    }

    /**
     * Check if player should start moving to the next totem
     * @param currentTotem the current totem being worked on
     * @param isFletchingWhileMoving whether the bot supports fletching while walking
     * @return true if should start moving now
     */
    public static boolean shouldStartMovingToNextTotem(TotemLocation currentTotem, boolean isFletchingWhileMoving) {
        if (currentTotem == null || currentTotem.isLast()) {
            return false;
        }

        // If we can fletch while moving, start moving earlier
        // If not, wait until current totem is completely finished
        return isFletchingWhileMoving;
    }

    /**
     * Get the center point of all totem locations (useful for area checks)
     * @return the center world point of the totem area
     */
    public static WorldPoint getTotemAreaCenter() {
        int totalX = 0, totalY = 0;
        int count = TotemLocation.values().length;

        for (TotemLocation location : TotemLocation.values()) {
            totalX += location.getLocation().getX();
            totalY += location.getLocation().getY();
        }

        return new WorldPoint(totalX / count, totalY / count, 0);
    }
} 