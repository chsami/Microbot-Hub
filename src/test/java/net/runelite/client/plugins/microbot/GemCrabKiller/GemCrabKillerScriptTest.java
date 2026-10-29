package net.runelite.client.plugins.microbot.GemCrabKiller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GemCrabKillerScriptTest {

    @Test
    void dharokModeAtTenHpIsNotLowEvenBelowQuarterHealth() {
        assertFalse(GemCrabKillerScript.isLowHp(true, 10, 10.1d));
    }

    @Test
    void dharokModeIsLowAtTwoHpOrLess() {
        assertTrue(GemCrabKillerScript.isLowHp(true, 2, 2.0d));
        assertTrue(GemCrabKillerScript.isLowHp(true, 1, 1.0d));
        assertFalse(GemCrabKillerScript.isLowHp(true, 3, 3.0d));
    }

    @Test
    void normalModeKeepsQuarterHealthThreshold() {
        assertTrue(GemCrabKillerScript.isLowHp(false, 20, 24.9d));
        assertFalse(GemCrabKillerScript.isLowHp(false, 25, 25.0d));
        assertFalse(GemCrabKillerScript.isLowHp(false, 2, 50.0d));
    }
}
