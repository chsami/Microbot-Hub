package net.runelite.client.plugins.microbot.tutorialisland;

import net.runelite.api.coords.WorldPoint;

final class RatPen {
    static final int GATE_ID = 9719;
    static final WorldPoint GATE_OUTSIDE = new WorldPoint(3111, 9518, 0);
    static final WorldPoint GATE_INSIDE = new WorldPoint(3110, 9518, 0);

    enum Step {
        ATTACK,
        OPEN_GATE,
        WALK_TO_GATE
    }

    private RatPen() {
    }

    static Step next(WorldPoint playerLoc, boolean ratReachable) {
        if (ratReachable) {
            return Step.ATTACK;
        }
        if (playerLoc != null && playerLoc.getPlane() == GATE_OUTSIDE.getPlane()
                && playerLoc.distanceTo2D(GATE_OUTSIDE) <= 1) {
            return Step.OPEN_GATE;
        }
        return Step.WALK_TO_GATE;
    }
}
