package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;

/** Verified Deep Wilderness Dungeon staircase handoff, independent of ordinary cannon tiles. */
final class DroSlayerDeepWildernessStairs {
    static final WorldPoint SURFACE = new WorldPoint(3044, 3927, 0);
    static final int STAIRCASE_OBJECT_ID = 16664;

    private DroSlayerDeepWildernessStairs() {}

    static boolean isDungeonTask(String location, WorldPoint destination) {
        return location != null && location.toLowerCase(java.util.Locale.ROOT).contains("deep wilderness dungeon")
            && isInside(destination);
    }

    static boolean isInside(WorldPoint point) {
        return point != null && point.getPlane() == 0
            && point.getX() >= 3008 && point.getX() <= 3071
            && point.getY() >= 10304 && point.getY() <= 10367;
    }

    static boolean nearSurfaceStairs(WorldPoint point) {
        return point != null && point.getPlane() == 0
            && point.distanceTo2D(SURFACE) <= 8;
    }
}
