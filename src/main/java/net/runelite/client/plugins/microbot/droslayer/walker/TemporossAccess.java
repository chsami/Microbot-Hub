package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/** Shared overworld lobby approach and the two maintained, requirement-gated arena shortcuts. */
public final class TemporossAccess
{
    public static final WorldPoint LOBBY_BANK = new WorldPoint(3156, 2836, 0);
    private static final WorldPoint ARENA_NORTH = new WorldPoint(3295, 3159, 0);
    private static final WorldPoint ARENA_SOUTH = new WorldPoint(3295, 3157, 0);

    public static boolean isArenaShortcut(Transport transport)
    {
        if (transport == null || transport.getType() != TransportType.AGILITY_SHORTCUT) return false;
        return transport.getObjectId() == 33348 && "Enter".equals(transport.getAction())
            && ARENA_NORTH.equals(transport.getOrigin()) && ARENA_SOUTH.equals(transport.getDestination())
            || transport.getObjectId() == 33344 && "Climb".equals(transport.getAction())
            && ARENA_SOUTH.equals(transport.getOrigin()) && ARENA_NORTH.equals(transport.getDestination());
    }

    private TemporossAccess() {}
}
