package net.runelite.client.plugins.microbot.droslayer;

/** A retained animation object is not proof that a cannon is still turning. */
final class DroCannonRotation {
    private static final long MOTION_GRACE_MS = 6_000L;
    private int world;
    private net.runelite.api.coords.WorldPoint tile;
    private int animation;
    private int frame;
    private int cycle;
    private int ammunition;
    private long lastMotionAt;

    synchronized boolean observe(int observedWorld, net.runelite.api.coords.WorldPoint observedTile,
                                 int ammo, int animationId, int animFrame, int animCycle, long now) {
        if (observedTile == null || ammo <= 0
                || animationId < net.runelite.api.gameval.AnimationID.MCANNON_N_FIRE
                || animationId > net.runelite.api.gameval.AnimationID.MCANNON_NW_TURN) {
            reset();
            return false;
        }
        // Allow the first sample a short observation window; never toggle a moving
        // cannon just because this script has only now started observing it.
        if (tile == null || observedWorld != world || !observedTile.equals(tile)
                || now < lastMotionAt) {
            lastMotionAt = now;
        } else if (animationId != animation || animFrame != frame || animCycle != cycle
                || ammo < ammunition) {
            lastMotionAt = now;
        }
        world = observedWorld;
        tile = observedTile;
        animation = animationId;
        frame = animFrame;
        cycle = animCycle;
        ammunition = ammo;
        return now - lastMotionAt <= MOTION_GRACE_MS;
    }

    synchronized void reset() {
        tile = null;
    }
}
