package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;

/** Derives entrance progress from the current scene, including after a restart or escape. */
final class DroSlayerGodWarsAccess {
    static final WorldPoint CAVE = new WorldPoint(3017, 3740, 0);
    static final WorldPoint BOULDER = new WorldPoint(3056, 10165, 3);
    static final WorldPoint CREVICE = new WorldPoint(3049, 10165, 3);

    enum Step { CAVE, BOULDER, CREVICE, INSIDE }

    static Step step(WorldPoint player) {
        if (player != null && player.getX() >= 3008 && player.getX() <= 3071
                && player.getY() >= 10112 && player.getY() <= 10175) {
            if (player.getPlane() == 0) return Step.INSIDE;
            if (player.getPlane() == 3) {
                // Recording: Move NPC 6621 crosses west from 3055 to 3053/3052.
                // 3058,10163 is still on the approach side; proximity is not passage.
                return player.getX() <= 3053 && player.getY() >= 10163
                    ? Step.CREVICE : Step.BOULDER;
            }
        }
        return Step.CAVE;
    }

    static WorldPoint approach(Step step) {
        switch (step) {
            case BOULDER: return BOULDER;
            case CREVICE: return CREVICE;
            default: return CAVE;
        }
    }

    static String objectName(Step step) {
        switch (step) {
            case BOULDER: return "Boulder";
            case CREVICE: return "Crevice";
            default: return "Cave";
        }
    }

    static String action(Step step) {
        switch (step) {
            case BOULDER: return "Move";
            case CREVICE: return "Use";
            default: return "Enter";
        }
    }
}
