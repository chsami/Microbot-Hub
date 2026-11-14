package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Arrays;
import java.util.Set;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SailingSceneOverlayTest {
    @Test public void shallowHeadingsDrawIntermediateSeaTiles() {
        Set<SeaPoint> tiles = EfficientSailingSceneOverlay.tiles(Arrays.asList(
                new SeaPoint(2620, 2582), new SeaPoint(2618, 2583), new SeaPoint(2616, 2584)));
        assertEquals(5, tiles.size());
        assertTrue(tiles.contains(new SeaPoint(2619, 2583)));
        assertTrue(tiles.contains(new SeaPoint(2617, 2584)));
    }
}
