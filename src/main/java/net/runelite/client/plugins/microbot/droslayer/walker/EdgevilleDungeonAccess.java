package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;

/** Access requirements cover the approach to the ladder, not just the door edge. */
final class EdgevilleDungeonAccess {
    private EdgevilleDungeonAccess() {}

    static boolean requiresCabinAccess(int objectId, WorldPoint origin) {
        boolean cabinDoor = objectId == 1804 && origin != null && origin.getPlane() == 0
            && origin.getX() == 3115 && (origin.getY() == 3450 || origin.getY() == 3449);
        boolean cabinLadder = objectId == 17384 && new WorldPoint(3115, 3452, 0).equals(origin);
        return cabinDoor || cabinLadder;
    }

    static boolean allowed(int objectId, WorldPoint origin, WorldPoint player, boolean brassKey) {
        if (!requiresCabinAccess(objectId, origin)) return true;
        // Already inside the cabin may descend or leave without a key.
        boolean inside = player != null && player.getPlane() == 0
            && player.getX() >= 3114 && player.getX() <= 3117
            && player.getY() >= 3451 && player.getY() <= 3453;
        return brassKey || inside;
    }

    static boolean isTrapdoor(Transport transport) {
        return transport != null && transport.getObjectId() == 1581
            && new WorldPoint(3094, 3469, 0).equals(transport.getOrigin());
    }
}
