package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;

public class SailingMapDestinationPolicyTest {
    @Test public void onlyKnownOceanGetsASailOption() {
        OceanAtlas atlas = new OceanAtlas();
        atlas.merge(new SceneChart(1, 3000, 3000, 3, 1, new byte[]{SeaGrid.OPEN, SeaGrid.BLOCKED, SeaGrid.UNKNOWN}));
        SeaGrid grid = atlas.snapshot(null, Collections.emptyList());
        assertTrue(SailingMapDestinationPolicy.ocean(new WorldPoint(3000, 3000, 0), grid, null));
        assertFalse(SailingMapDestinationPolicy.ocean(new WorldPoint(3001, 3000, 0), grid, null));
        assertFalse(SailingMapDestinationPolicy.ocean(new WorldPoint(3002, 3000, 0), grid, null));
        assertFalse(SailingMapDestinationPolicy.ocean(new WorldPoint(3200, 3200, 0), grid, null));
        assertFalse(SailingMapDestinationPolicy.ocean(new WorldPoint(3000, 3000, 1), grid, null));
        assertFalse(SailingMapDestinationPolicy.ocean(null, grid, null));
        for (byte current : new byte[]{SeaGrid.BLOCKED, SeaGrid.UNKNOWN}) {
            assertFalse(SailingMapDestinationPolicy.ocean(new WorldPoint(3000, 3000, 0), grid,
                    new SceneChart(2, 3000, 3000, 1, 1, new byte[]{current})));
        }
    }
}
