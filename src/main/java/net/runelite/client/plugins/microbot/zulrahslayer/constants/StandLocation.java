package net.runelite.client.plugins.microbot.zulrahslayer.constants;

import net.runelite.api.coords.WorldPoint;

public enum StandLocation {
    NORTHEAST_NORTH(2272, 3078), //starting position
    NORTHEAST_SOUTH(2272, 3076),
    EAST_PILLAR_N(2272, 3072),
    EAST_PILLAR_MELEE(2272, 3074),
    WEST_PILLAR_N(2264, 3072),
    WEST_PILLAR_MELEE(2262, 3074);

    private final int localX;
    private final int localY;

    StandLocation(int localX, int localY) {
        this.localX = localX;
        this.localY = localY;
    }

    public WorldPoint toWorldPoint() {
        return new WorldPoint(this.localX, this.localY, 0);
    }

    public StandLocation meleeDodge() {
        switch (this) {
            case NORTHEAST_NORTH:
                return NORTHEAST_SOUTH;
            case EAST_PILLAR_N:
                return EAST_PILLAR_MELEE;
            case WEST_PILLAR_N:
                return WEST_PILLAR_MELEE;
            default:
                return null;
        }
    }
}
