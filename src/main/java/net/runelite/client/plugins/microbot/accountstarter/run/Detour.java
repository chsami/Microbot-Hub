package net.runelite.client.plugins.microbot.accountstarter.run;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** Skirts the Varrock stone-circle dark wizards. */
final class Detour {
    private static final int WAYPOINT_REACH = 4;

    private Detour() {}

    static boolean walkTo(WorldPoint to, int distance) {
        WorldPoint me = Rs2Player.getWorldLocation();
        // an unreadable level comes back 0, which keeps the detour
        for (WorldPoint w : Spots.waypoints(me, to, Rs2Player.getCombatLevel())) {
            WorldPoint at = Rs2Player.getWorldLocation();
            if (at != null && at.distanceTo2D(w) <= WAYPOINT_REACH) continue;
            if (!Rs2Walker.walkTo(w, WAYPOINT_REACH)) return false;
        }
        return Rs2Walker.walkTo(to, distance);
    }
}
