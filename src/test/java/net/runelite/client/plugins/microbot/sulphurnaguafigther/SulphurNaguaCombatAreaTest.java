package net.runelite.client.plugins.microbot.sulphurnaguafigther;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SulphurNaguaCombatAreaTest {

    private final WorldArea area = new WorldArea(1350, 9570, 10, 10, 0);

    @Test
    void missingPlayerLocationIsNotInArea() {
        assertDoesNotThrow(() -> SulphurNaguaScript.isInArea(area, null));
        assertFalse(SulphurNaguaScript.isInArea(area, null));
    }

    @Test
    void missingAreaIsNotInArea() {
        assertFalse(SulphurNaguaScript.isInArea(null, new WorldPoint(1355, 9575, 0)));
    }

    @Test
    void pointInsideAreaIsInArea() {
        assertTrue(SulphurNaguaScript.isInArea(area, new WorldPoint(1355, 9575, 0)));
    }

    @Test
    void pointOutsideAreaIsNotInArea() {
        assertFalse(SulphurNaguaScript.isInArea(area, new WorldPoint(1400, 9575, 0)));
    }
}
