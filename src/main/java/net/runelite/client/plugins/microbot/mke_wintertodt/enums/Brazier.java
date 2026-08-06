package net.runelite.client.plugins.microbot.mke_wintertodt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@AllArgsConstructor
public enum Brazier {
    SOUTH_EAST(new WorldPoint(1638, 3996, 0), new WorldPoint(1639, 3998, 0), new WorldPoint(1638, 3995, 0)),
    SOUTH_WEST(new WorldPoint(1621, 3996, 0), new WorldPoint(1621, 3998, 0), new WorldPoint(1621, 3995, 0));

    @Getter
    public final WorldPoint BRAZIER_LOCATION;
    @Getter
    public final WorldPoint OBJECT_BRAZIER_LOCATION;
    // One tile south of the brazier-stand tile — the same offset the dodge logic uses.
    // Snowfall AoE around the brazier doesn't reach here, so it's safe to fletch on.
    @Getter
    public final WorldPoint FLETCH_LOCATION;
}
