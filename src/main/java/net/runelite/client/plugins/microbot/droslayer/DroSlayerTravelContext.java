package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;

/** Efficient Walker owns surface and dungeon travel. */
final class DroSlayerTravelContext {
    private DroSlayerTravelContext() {}

    static DroSlayerTravelMode modeFor(DroSlayerConfig config, WorldPoint current, WorldPoint target, String locationName) {
        return DroSlayerTravelMode.FULL_TRAVEL;
    }
}
