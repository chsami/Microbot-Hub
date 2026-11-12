package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallingRocksTest {
    private final WorldPoint tile = new WorldPoint(3295, 9868, 0);

    @Test
    void tileIsDangerousFromCreationEvenBeforeTheSceneListsIt() {
        FallingRocks rocks = new FallingRocks();
        rocks.add(tile, 10);
        rocks.refresh(10, Set.of());
        assertTrue(rocks.contains(tile));
    }

    @Test
    void tileStaysWhileItsGraphicsObjectExists() {
        FallingRocks rocks = new FallingRocks();
        rocks.add(tile, 10);
        rocks.refresh(12, Set.of(tile));
        assertTrue(rocks.contains(tile));
    }

    @Test
    void tileIsDroppedOnceTheGraphicsObjectIsGone() {
        FallingRocks rocks = new FallingRocks();
        rocks.add(tile, 10);
        rocks.refresh(13, Set.of());
        assertFalse(rocks.contains(tile));
        assertTrue(rocks.isEmpty());
    }

    @Test
    void tileExpiresEvenIfTheObjectNeverGoes() {
        FallingRocks rocks = new FallingRocks();
        rocks.add(tile, 10);
        rocks.refresh(10 + FallingRocks.MAX_LIFE_TICKS - 1, Set.of(tile));
        assertTrue(rocks.contains(tile));
        rocks.refresh(10 + FallingRocks.MAX_LIFE_TICKS, Set.of(tile));
        assertFalse(rocks.contains(tile));
    }
}
