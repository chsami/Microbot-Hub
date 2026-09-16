package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** Immutable NPC data copied on the client thread for use by the script executor. */
final class TemporossNpcSnapshot
{
    final int id;
    final int index;
    final LocalPoint localLocation;
    final WorldPoint worldLocation;
    final String name;

    TemporossNpcSnapshot(int id, int index, LocalPoint localLocation, WorldPoint worldLocation, String name)
    {
        this.id = id;
        this.index = index;
        this.localLocation = localLocation;
        this.worldLocation = worldLocation;
        this.name = name;
    }

    int getId()
    {
        return id;
    }
}
