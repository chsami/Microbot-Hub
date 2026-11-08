package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import net.runelite.api.coords.WorldPoint;

/**
 * Single-combat rooms whose spawns hold one Tormented Demon each. Each room is the area around its
 * spawns plus an edge margin, so a fire bomb step or a demon following the player out of that area
 * stays in the room.
 */
public enum DemonRoom
{
    PLINTH_CHAMBER("Plinth chamber", new WorldPoint(4133, 4382, 0), 4110, 4156, 4356, 4406),
    WEST_NORTH("West room, north", new WorldPoint(4038, 4449, 0), 4030, 4046, 4441, 4457),
    WEST_SOUTH("West room, south", new WorldPoint(4040, 4419, 0), 4032, 4048, 4411, 4427);

    /** Wider than the two tiles a fire bomb step can move the player. */
    static final int EDGE_MARGIN = 3;
    private final String displayName;
    private final WorldPoint center;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;

    DemonRoom(String displayName, WorldPoint center, int minX, int maxX, int minY, int maxY)
    {
        this.displayName = displayName;
        this.center = center;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public WorldPoint getCenter()
    {
        return center;
    }

    public boolean contains(WorldPoint point)
    {
        return point != null && point.getPlane() == center.getPlane()
            && point.getX() >= minX - EDGE_MARGIN && point.getX() <= maxX + EDGE_MARGIN
            && point.getY() >= minY - EDGE_MARGIN && point.getY() <= maxY + EDGE_MARGIN;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
