package net.runelite.client.plugins.microbot.fourtifysgemmining.mining;

import net.runelite.api.coords.WorldPoint;

public enum GemMine
{
    SURFACE("Surface", "the surface gem mine", 2816, 2993, 2831, 3008, new WorldPoint(2825, 3000, 0), new WorldPoint(2852, 2954, 0)),
    UNDERGROUND("Underground", "the underground gem mine", 2818, 9375, 2858, 9405, new WorldPoint(2838, 9387, 0), new WorldPoint(2842, 9383, 0));

    private final String displayName;
    private final String placeName;
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final WorldPoint mineDestination;
    private final WorldPoint bankDestination;

    GemMine(String name, String placeName, int minX, int minY, int maxX, int maxY, WorldPoint mineDestination, WorldPoint bankDestination)
    {
        this.displayName = name;
        this.placeName = placeName;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.mineDestination = mineDestination;
        this.bankDestination = bankDestination;
    }

    public WorldPoint getMineDestination()
    {
        return mineDestination;
    }

    public WorldPoint getBankDestination()
    {
        return bankDestination;
    }

    public boolean contains(WorldPoint point)
    {
        return point != null && point.getPlane() == 0 && point.getX() >= minX && point.getX() < maxX
            && point.getY() >= minY && point.getY() < maxY;
    }

    public boolean isUnderground()
    {
        return this == UNDERGROUND;
    }

    /** The mine as a status names it, such as "the surface gem mine". */
    public String getPlaceName()
    {
        return placeName;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
