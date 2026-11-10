package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;

/** Session confirmation of the cave deposit, independent of trips and world hops. */
final class DroSlayerRevenantEntry {
    private boolean entryConfirmed;
    private boolean awaitingRespawn;

    synchronized void reset() {
        entryConfirmed = false;
        awaitingRespawn = false;
    }

    synchronized void observeLocation(WorldPoint location) {
        if (awaitingRespawn) {
            if (location != null && !isInsideCaves(location)) awaitingRespawn = false;
            return;
        }
        // Actual entry confirms both a newly paid deposit and one paid before this session.
        if (isInsideCaves(location)) entryConfirmed = true;
    }

    synchronized void onDeath(WorldPoint location) {
        if (isInsideCaves(location)) {
            entryConfirmed = false;
            awaitingRespawn = true;
        }
    }

    synchronized boolean needsCoins() {
        return !entryConfirmed;
    }

    private static boolean isInsideCaves(WorldPoint location) {
        if (location == null || location.getPlane() != 0) return false;
        // All six cave regions, including the route between the entrances and goblins.
        switch (location.getRegionID()) {
            case 12701:
            case 12702:
            case 12703:
            case 12957:
            case 12958:
            case 12959:
                return true;
            default:
                return false;
        }
    }
}
