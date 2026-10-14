package net.runelite.client.plugins.microbot.nmz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrayerPotionScriptTest {

    @Test
    void zeroRealLevelSkipsDrinkingInsteadOfDividingByZero() {
        assertTrue(PrayerPotionScript.isPrayerAboveThreshold(0, 0, 25));
        assertTrue(PrayerPotionScript.isPrayerAboveThreshold(10, 0, 30));
    }

    @Test
    void prayerAboveThresholdSkipsDrinking() {
        assertTrue(PrayerPotionScript.isPrayerAboveThreshold(50, 70, 30));
    }

    @Test
    void prayerAtOrBelowThresholdAllowsDrinking() {
        assertFalse(PrayerPotionScript.isPrayerAboveThreshold(21, 70, 30));
        assertFalse(PrayerPotionScript.isPrayerAboveThreshold(0, 70, 25));
    }
}
