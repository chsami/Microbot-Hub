package net.runelite.client.plugins.microbot.AmmoniteCrabs.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

public enum AmmoniteCrabLocations {
    NORTH_WEST("North West (3 spot)", new WorldPoint(3657, 3875, 0), new WorldPoint(3664, 3875, 0), new WorldPoint(3689, 3897, 0)),
    NORTH_EAST("North East (2 spot)", new WorldPoint(3718, 3881, 0), new WorldPoint(3708, 3874, 0), new WorldPoint(3690, 3874, 0)),
    SOUTH_WEST("South West (2 spot)", new WorldPoint(3717, 3846, 0), new WorldPoint(3726, 3839, 0), new WorldPoint(3680, 3844, 0)),
    SOUTH_EAST("South East (2 spot)", new WorldPoint(3733, 3846, 0), new WorldPoint(3734, 3839, 0), new WorldPoint(3736, 3818, 0));
    @Getter
    private final String LocationName;
    @Getter
    private final WorldPoint FightLocation;
    @Getter
    private final WorldPoint WorldhopLocation;
    @Getter
    private WorldPoint ResetLocation;

    AmmoniteCrabLocations(String locationName, WorldPoint fightLocation, WorldPoint worldhopLocation, WorldPoint resetLocation) {
        LocationName = locationName;
        FightLocation = fightLocation;
        WorldhopLocation = worldhopLocation;
        ResetLocation = resetLocation;
    }
}
