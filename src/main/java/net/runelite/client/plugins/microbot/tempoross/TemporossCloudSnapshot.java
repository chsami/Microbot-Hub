package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** Immutable lightning-shadow data copied on the client thread for use by the script executor. */
final class TemporossCloudSnapshot
{
    final int id;
    final LocalPoint localLocation;
    final WorldPoint worldLocation;

    TemporossCloudSnapshot(int id, LocalPoint localLocation, WorldPoint worldLocation)
    {
        this.id = id;
        this.localLocation = localLocation;
        this.worldLocation = worldLocation;
    }
}
