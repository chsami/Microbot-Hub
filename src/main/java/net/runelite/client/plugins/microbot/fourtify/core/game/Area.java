package net.runelite.client.plugins.microbot.fourtify.core.game;

import net.runelite.api.coords.WorldPoint;

/** A place to work: a centre tile and how far around it the work happens, in tiles. */
public interface Area {
    WorldPoint getCentre();

    int getRadius();

    /** The tiles within {@code radius} of {@code centre}, such as a quest NPC's room. */
    static Area around(WorldPoint centre, int radius) {
        return new Area() {
            @Override
            public WorldPoint getCentre() {
                return centre;
            }

            @Override
            public int getRadius() {
                return radius;
            }

            @Override
            public String toString() {
                return centre.getX() + "," + centre.getY() + "," + centre.getPlane() + " (" + radius + " tiles)";
            }
        };
    }
}
