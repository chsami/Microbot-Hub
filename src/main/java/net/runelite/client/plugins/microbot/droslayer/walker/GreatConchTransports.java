package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;

/** Walker-local additions: live-confirmed Great Conch dock and ring (2026-09-30). */
public final class GreatConchTransports
{
    public static final WorldPoint DOCK = new WorldPoint(3187, 2368, 0);
    public static final WorldPoint FAIRY_RING = new WorldPoint(3178, 2447, 0);

    private GreatConchTransports() {}

    public static boolean unlocked(QuestState state)
    {
        // Deliberately stricter than the game's partial-quest unlock, as requested.
        return state == QuestState.FINISHED;
    }

    public static boolean isRingConnection(WorldPoint origin, WorldPoint destination)
    {
        return FAIRY_RING.equals(origin) || FAIRY_RING.equals(destination);
    }

    public static boolean isCharterConnection(WorldPoint origin, WorldPoint destination)
    {
        return DOCK.equals(origin) || DOCK.equals(destination);
    }

    public static boolean onArrivalDeck(WorldPoint player)
    {
        return player != null && player.getPlane() == 1 && player.distanceTo2D(DOCK) <= 8;
    }

}
