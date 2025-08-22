package net.runelite.client.plugins.microbot.valetotems.enums;

import net.runelite.api.coords.WorldPoint;

/**
 * Enum representing banking locations and related positions in the Vale of Anima
 */
public enum BankLocation {
    BANK_BOOTH(new WorldPoint(1414, 3353, 0), "Bank booth object location"),
    PLAYER_STANDING_TILE(new WorldPoint(1413, 3353, 0), "Optimal tile for player to stand on when banking");

    private final WorldPoint location;
    private final String description;

    BankLocation(WorldPoint location, String description) {
        this.location = location;
        this.description = description;
    }

    public WorldPoint getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the distance between two bank locations
     * @param other the other bank location
     * @return distance in tiles
     */
    public int getDistanceTo(BankLocation other) {
        return this.location.distanceTo(other.location);
    }

    /**
     * Check if the given world point matches this bank location
     * @param point the world point to check
     * @return true if coordinates match
     */
    public boolean matches(WorldPoint point) {
        return this.location.equals(point);
    }
} 