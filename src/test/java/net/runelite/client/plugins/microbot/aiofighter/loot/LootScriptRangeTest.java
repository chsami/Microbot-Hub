package net.runelite.client.plugins.microbot.aiofighter.loot;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class LootScriptRangeTest {

    private static final WorldPoint UNSET = new WorldPoint(0, 0, 0);
    private static final WorldPoint CENTER = new WorldPoint(2460, 9165, 1);

    private static boolean engineAccepts(WorldPoint item, WorldPoint player, int range) {
        return item.distanceTo(player) < range;
    }

    @Test
    void radiusOneReachesAdjacentTilesFromTheCenterTile() {
        int range = LootScript.lootRange(1, CENTER, CENTER);

        assertEquals(2, range);
        assertTrue(engineAccepts(CENTER.dx(1), CENTER, range));
        assertTrue(engineAccepts(CENTER.dx(-1).dy(1), CENTER, range));
        assertFalse(engineAccepts(CENTER.dx(2), CENTER, range));
    }

    @Test
    void wholeFightAreaIsInRangeWhenThePlayerIsOffCenter() {
        for (int radius = 1; radius <= 5; radius++) {
            for (int px = -3; px <= 3; px++) {
                for (int py = -3; py <= 3; py++) {
                    WorldPoint player = CENTER.dx(px).dy(py);
                    int range = LootScript.lootRange(radius, CENTER, player);
                    for (int ix = -radius; ix <= radius; ix++) {
                        for (int iy = -radius; iy <= radius; iy++) {
                            assertTrue(engineAccepts(CENTER.dx(ix).dy(iy), player, range));
                        }
                    }
                }
            }
        }
    }

    @Test
    void withoutCenterTheRadiusIsInclusiveAroundThePlayer() {
        assertEquals(2, LootScript.lootRange(1, UNSET, CENTER));
        assertEquals(2, LootScript.lootRange(1, null, CENTER));
        assertEquals(6, LootScript.lootRange(5, UNSET, CENTER));
    }

    @Test
    void unknownPlayerOrOtherPlaneFallsBackToTheRadius() {
        assertEquals(2, LootScript.lootRange(1, CENTER, null));
        assertEquals(2, LootScript.lootRange(1, CENTER, new WorldPoint(2460, 9165, 0)));
    }
}
