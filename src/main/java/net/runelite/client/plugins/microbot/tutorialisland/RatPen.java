package net.runelite.client.plugins.microbot.tutorialisland;

import net.runelite.api.coords.WorldPoint;

final class RatPen {
    static final int GATE_ID = 9719;
    static final WorldPoint GATE_OUTSIDE = new WorldPoint(3111, 9518, 0);
    static final WorldPoint GATE_INSIDE = new WorldPoint(3110, 9518, 0);
    static final WorldPoint RANGE_SPOT = new WorldPoint(3111, 9519, 0);

    enum Step {
        ATTACK,
        OPEN_GATE,
        WALK_TO_GATE
    }

    enum RangedStep {
        ATTACK,
        WAIT,
        WALK_TO_RANGE_SPOT
    }

    private RatPen() {
    }

    static RangedStep nextRanged(WorldPoint playerLoc, boolean ratInSight) {
        if (ratInSight) {
            return RangedStep.ATTACK;
        }
        if (playerLoc != null && playerLoc.getPlane() == RANGE_SPOT.getPlane()
                && playerLoc.distanceTo2D(RANGE_SPOT) <= 1) {
            return RangedStep.WAIT;
        }
        return RangedStep.WALK_TO_RANGE_SPOT;
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
