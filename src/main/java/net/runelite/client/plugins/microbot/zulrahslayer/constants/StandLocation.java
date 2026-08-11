package net.runelite.client.plugins.custom.zulrah.constants;

import net.runelite.api.coords.WorldPoint;

public enum StandLocation {
    NORTHEAST_NORTH(2272, 3078), //starting position
    NORTHEAST_TOP(2274, 3077),
    EAST_PILLAR_N(2272, 3072),
    WEST_PILLAR_N(2264, 3072);

    private final int localX;
    private final int localY;

    StandLocation(int localX, int localY) {
        this.localX = localX;
        this.localY = localY;
    }

    public WorldPoint toWorldPoint() {
        return new WorldPoint(this.localX, this.localY, 0);
    }
}
