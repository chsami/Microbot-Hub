package net.runelite.client.plugins.microbot.valetotems.enums;

import net.runelite.api.coords.WorldPoint;

/**
 * Enum representing the totem construction sites in the Vale of Anima
 * Supports both standard (5 totems) and extended (8 totems) routes
 */
public enum TotemLocation {
    // Standard route totems (original 5)
    TOTEM_1(RouteType.STANDARD, 1, new WorldPoint(1398, 3329, 0), "First totem site"),
    TOTEM_2(RouteType.STANDARD, 2, new WorldPoint(1413, 3286, 0), "Second totem site"),
    TOTEM_3(RouteType.STANDARD, 3, new WorldPoint(1385, 3274, 0), "Third totem site"),
    TOTEM_4(RouteType.STANDARD, 4, new WorldPoint(1346, 3319, 0), "Fourth totem site"),
    TOTEM_5(RouteType.STANDARD, 5, new WorldPoint(1370, 3375, 0), "Fifth totem site"),
    
    // Extended route totems (8 totems total)
    EXTENDED_TOTEM_1(RouteType.EXTENDED, 1, new WorldPoint(1453, 3341, 0), "Extended first totem site"),
    EXTENDED_TOTEM_2(RouteType.EXTENDED, 2, new WorldPoint(1477, 3332, 0), "Extended second totem site"),
    EXTENDED_TOTEM_3(RouteType.EXTENDED, 3, new WorldPoint(1438, 3305, 0), "Extended third totem site"),
    EXTENDED_TOTEM_4(RouteType.EXTENDED, 4, new WorldPoint(1413, 3286, 0), "Extended fourth totem site"),
    EXTENDED_TOTEM_5(RouteType.EXTENDED, 5, new WorldPoint(1385, 3274, 0), "Extended fifth totem site"),
    EXTENDED_TOTEM_6(RouteType.EXTENDED, 6, new WorldPoint(1346, 3319, 0), "Extended sixth totem site"),
    EXTENDED_TOTEM_7(RouteType.EXTENDED, 7, new WorldPoint(1370, 3375, 0), "Extended seventh totem site"),
    EXTENDED_TOTEM_8(RouteType.EXTENDED, 8, new WorldPoint(1398, 3329, 0), "Extended eighth totem site");

    /**
     * Enum to distinguish between different route types
     */
    public enum RouteType {
        STANDARD(5, "Standard Route"),
        EXTENDED(8, "Extended Route");
        
        private final int maxTotems;
        private final String description;
        
        RouteType(int maxTotems, String description) {
            this.maxTotems = maxTotems;
            this.description = description;
        }
        
        public int getMaxTotems() {
            return maxTotems;
        }
        
        public String getDescription() {
            return description;
        }
    }

    private final RouteType routeType;
    private final int order;
    private final WorldPoint location;
    private final String description;

    TotemLocation(RouteType routeType, int order, WorldPoint location, String description) {
        this.routeType = routeType;
        this.order = order;
        this.location = location;
        this.description = description;
    }

    public RouteType getRouteType() {
        return routeType;
    }

    public int getOrder() {
        return order;
    }

    public WorldPoint getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the next totem location in sequence for the same route
     * @return next totem location, or null if this is the last one
     */
    public TotemLocation getNext() {
        if (order >= routeType.getMaxTotems()) return null;
        return getByOrder(routeType, order + 1);
    }

    /**
     * Get totem location by order number for a specific route
     * @param routeType the route type
     * @param order the sequence number
     * @return the corresponding totem location
     */
    public static TotemLocation getByOrder(RouteType routeType, int order) {
        for (TotemLocation location : values()) {
            if (location.getRouteType() == routeType && location.getOrder() == order) {
                return location;
            }
        }
        return null;
    }

    /**
     * Get the first totem location for a specific route
     * @param routeType the route type
     * @return first totem for the route
     */
    public static TotemLocation getFirst(RouteType routeType) {
        return getByOrder(routeType, 1);
    }

    /**
     * Get the first totem location (default to standard route for backward compatibility)
     * @return TOTEM_1
     */
    public static TotemLocation getFirst() {
        return getFirst(RouteType.STANDARD);
    }

    /**
     * Get all totem locations for a specific route
     * @param routeType the route type
     * @return array of totem locations for the route
     */
    public static TotemLocation[] getRouteLocations(RouteType routeType) {
        return java.util.Arrays.stream(values())
                .filter(totem -> totem.getRouteType() == routeType)
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .toArray(TotemLocation[]::new);
    }

    /**
     * Check if this is the last totem in the sequence for its route
     * @return true if this is the last totem in its route
     */
    public boolean isLast() {
        return order >= routeType.getMaxTotems();
    }

    /**
     * Check if this is the last totem in a specific route
     * @param routeType the route type to check against
     * @return true if this is the last totem in the specified route
     */
    public boolean isLastInRoute(RouteType routeType) {
        if (this.routeType != routeType) return false;
        return order >= routeType.getMaxTotems();
    }
} 