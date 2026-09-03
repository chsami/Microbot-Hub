package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** Immutable tile-object data copied on the client thread for use by the script executor. */
final class TemporossObjectSnapshot
{
    final int id;
    final LocalPoint localLocation;
    final WorldPoint worldLocation;

    TemporossObjectSnapshot(int id, LocalPoint localLocation, WorldPoint worldLocation)
    {
        this.id = id;
        this.localLocation = localLocation;
        this.worldLocation = worldLocation;
    }
}
