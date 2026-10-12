package net.runelite.client.plugins.microbot.mmcaves;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** A fixed point inside a wall tile, shared by the glide and its eventual click. */
final class MmCavesWallTarget {
    static final int MAX_OFFSET = 24; // Local units; a tile extends 64 units from its centre.
    final WorldPoint tile;
    final int offsetX;
    final int offsetY;

    MmCavesWallTarget(WorldPoint tile, int offsetX, int offsetY) {
        this.tile = tile;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    static MmCavesWallTarget sample(WorldPoint tile) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(-MAX_OFFSET, MAX_OFFSET + 1);
        int y = random.nextInt(-MAX_OFFSET, MAX_OFFSET + 1);
        if (x == 0 && y == 0) x = 1;
        return new MmCavesWallTarget(tile, x, y);
    }

    Point canvasPoint(Client client) {
        if (client.getTopLevelWorldView() == null) return null;
        LocalPoint center = LocalPoint.fromWorld(client.getTopLevelWorldView(), tile);
        if (center == null) return null;
        LocalPoint offset = new LocalPoint(center.getX() + offsetX, center.getY() + offsetY,
                client.getTopLevelWorldView());
        return Perspective.localToCanvas(client, offset, client.getTopLevelWorldView().getPlane());
    }
}
