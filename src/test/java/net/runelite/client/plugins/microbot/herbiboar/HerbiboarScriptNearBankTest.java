package net.runelite.client.plugins.microbot.herbiboar;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HerbiboarScriptNearBankTest {

    @Test
    void nullPlayerLocationIsNotNearBank() {
        assertFalse(HerbiboarScript.isNearBank(null));
    }

    @Test
    void locationWithinFiveTilesOfBankIsNearBank() {
        assertTrue(HerbiboarScript.isNearBank(new WorldPoint(3768, 3901, 0)));
    }

    @Test
    void locationFarFromBankIsNotNearBank() {
        assertFalse(HerbiboarScript.isNearBank(new WorldPoint(3703, 3878, 0)));
    }
}
