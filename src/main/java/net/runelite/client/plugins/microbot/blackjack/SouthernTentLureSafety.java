package net.runelite.client.plugins.microbot.blackjack;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/** Spatial and follow checks shared by both directions of the southern tent lure. */
final class SouthernTentLureSafety
{
    private static final WorldArea MAIN_ROOM = new WorldArea(3348, 2953, 4, 4, 0);
    private static final WorldArea REAR_ROOM = new WorldArea(3349, 2947, 3, 5, 0);
    private static final WorldPoint HALLWAY = new WorldPoint(3350, 2952, 0);
    private static final WorldArea ENTRANCE_STREET = new WorldArea(3346, 2958, 18, 9, 0);

    private SouthernTentLureSafety()
    {
    }

    static boolean inside(WorldPoint point)
    {
        return point != null && (MAIN_ROOM.contains(point) || REAR_ROOM.contains(point)
                || HALLWAY.equals(point));
    }

    static boolean onDestinationSide(WorldPoint point, boolean entering)
    {
        // The curtain tile (3350,2957) is neither side of a completed crossing.
        return entering ? inside(point) : point != null && ENTRANCE_STREET.contains(point);
    }

    static boolean canClose(WorldPoint player, WorldPoint target, boolean entering)
    {
        return onDestinationSide(player, entering) && onDestinationSide(target, entering);
    }

    static boolean crossesInnerCurtain(WorldPoint from, WorldPoint to)
    {
        return inside(from) && inside(to) && (from.getY() <= 2952) != (to.getY() <= 2952);
    }

    static boolean followingNearby(WorldPoint player, WorldPoint target, boolean interacting, int range)
    {
        return interacting && player != null && target != null
                && player.getPlane() == target.getPlane() && player.distanceTo2D(target) <= range;
    }
}
