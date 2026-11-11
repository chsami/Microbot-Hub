package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;

/** Named targets for explicit user travel; selecting one never starts a route. */
public enum WalkerDestination
{
    NONE("Use existing map destination", null),
    TEARS_OF_GUTHIX("Tears of Guthix (Juna)", TearsOfGuthixLocation.JUNA_APPROACH),
    TEMPOROSS("Tempoross (Ruins of Unkah)", TemporossAccess.LOBBY_BANK);

    private final String label;
    private final WorldPoint worldPoint;

    WalkerDestination(String label, WorldPoint worldPoint)
    {
        this.label = label;
        this.worldPoint = worldPoint;
    }

    public WorldPoint getWorldPoint() { return worldPoint; }

    @Override public String toString() { return label; }
}
