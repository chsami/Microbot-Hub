package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.api.coords.WorldPoint;

/** The recorded local bank approaches, with one outstanding movement/interaction at a time. */
final class ZulrahRecoveryBankRoute {
    interface Actions {
        boolean walk(WorldPoint target);
        boolean interact(int id, WorldPoint object, String action);
    }
    private boolean enteredCastle;
    private int lastPlane = -1;
    private long nextAttemptAt, dispatchedAt;
    private boolean pending;

    void tick(ZulrahDeathRecovery.Spawn spawn, WorldPoint here, boolean moving, long now, Actions actions) {
        if (here == null) return;
        if (here.getPlane() != lastPlane) {
            lastPlane = here.getPlane();
            pending = false;
            nextAttemptAt = 0;
        }
        if (now < nextAttemptAt || (pending && moving && now - dispatchedAt < 12000)) return;

        WorldPoint approach;
        WorldPoint object;
        int id;
        String action;
        if (spawn == ZulrahDeathRecovery.Spawn.EDGEVILLE) {
            if (here.getPlane() != 0) return;
            approach = new WorldPoint(3090, 3490, 0);
            object = new WorldPoint(3094, 3492, 0);
            id = 10355;
            action = "Bank";
        } else if (here.getPlane() < 2) {
            if (here.getPlane() == 0 && !enteredCastle) {
                WorldPoint entry = new WorldPoint(3214, 3211, 0);
                enteredCastle = here.distanceTo(entry) <= 2 || here.getX() < 3212;
                if (!enteredCastle) {
                    record(actions.walk(entry), now);
                    return;
                }
            }
            approach = new WorldPoint(3206, 3208, here.getPlane());
            object = new WorldPoint(3204, 3207, here.getPlane());
            id = here.getPlane() == 0 ? 56230 : 16672;
            action = "Climb-up";
        } else if (here.getPlane() == 2) {
            approach = new WorldPoint(3209, 3220, 2);
            object = new WorldPoint(3209, 3221, 2);
            id = 27291;
            action = "Bank";
        } else return;

        // A visible object click lets the game path the remainder, including the stair approach.
        boolean issued = actions.interact(id, object, action);
        if (!issued && here.distanceTo(approach) > 1) issued = actions.walk(approach);
        record(issued, now);
    }

    private void record(boolean issued, long now) {
        pending = issued;
        dispatchedAt = now;
        nextAttemptAt = now + (issued ? 1800 : 700);
    }
}
