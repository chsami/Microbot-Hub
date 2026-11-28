package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Set;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/** Not the client's isReachable(): Rs2Reachable floods from the target itself, so it says yes behind a fence. */
public final class Reach {
    private static final int TILES = 32;

    private static int tick = -1;
    private static WorldPoint from;
    private static Set<WorldPoint> tiles = Set.of();

    private Reach() {}

    static synchronized Set<WorldPoint> tiles() {
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me == null) return Set.of();
        int now = Microbot.getClient().getTickCount();
        if (now != tick || !me.equals(from)) {
            tiles = Rs2Tile.getReachableTilesFromTile(me, TILES).keySet();
            tick = now;
            from = me;
        }
        return tiles;
    }

    public static boolean touches(WorldArea area, Set<WorldPoint> reach) {
        if (area == null) return false;
        int x0 = area.getX(), y0 = area.getY(), x1 = x0 + area.getWidth() - 1, y1 = y0 + area.getHeight() - 1;
        int z = area.getPlane();
        for (int x = x0; x <= x1; x++) {
            if (reach.contains(new WorldPoint(x, y0 - 1, z)) || reach.contains(new WorldPoint(x, y1 + 1, z))) return true;
        }
        for (int y = y0; y <= y1; y++) {
            if (reach.contains(new WorldPoint(x0 - 1, y, z)) || reach.contains(new WorldPoint(x1 + 1, y, z))) return true;
        }
        // a wall between that side tile and the object is not checked
        return false;
    }
}
