package net.runelite.client.plugins.microbot.autogauntletprayer;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrayerClickThrottleTest {

    @Test
    void clicksAPrayerOnlyOncePerTickAcrossManyFrames() {
        PrayerClickThrottle throttle = new PrayerClickThrottle();
        int clicks = 0;
        for (int frame = 0; frame < 13; frame++) {
            if (throttle.tryClick(Rs2PrayerEnum.PROTECT_RANGE, 62776)) {
                clicks++;
            }
        }
        assertEquals(1, clicks);
    }

    @Test
    void waitsForTheVarbitToUpdateBeforeClickingAgain() {
        PrayerClickThrottle throttle = new PrayerClickThrottle();
        assertTrue(throttle.tryClick(Rs2PrayerEnum.PROTECT_MAGIC, 100));
        assertFalse(throttle.tryClick(Rs2PrayerEnum.PROTECT_MAGIC, 101));
        assertTrue(throttle.tryClick(Rs2PrayerEnum.PROTECT_MAGIC, 100 + PrayerClickThrottle.COOLDOWN_TICKS));
    }

    @Test
    void tracksEachPrayerSeparately() {
        PrayerClickThrottle throttle = new PrayerClickThrottle();
        assertTrue(throttle.tryClick(Rs2PrayerEnum.PROTECT_RANGE, 200));
        assertTrue(throttle.tryClick(Rs2PrayerEnum.RIGOUR, 200));
        assertFalse(throttle.tryClick(Rs2PrayerEnum.PROTECT_RANGE, 200));
        assertFalse(throttle.tryClick(Rs2PrayerEnum.RIGOUR, 200));
    }

    @Test
    void allowsAClickWhenTheTickCounterGoesBackwards() {
        PrayerClickThrottle throttle = new PrayerClickThrottle();
        assertTrue(throttle.tryClick(Rs2PrayerEnum.PIETY, 5000));
        assertTrue(throttle.tryClick(Rs2PrayerEnum.PIETY, 3));
    }

    @Test
    void resetForgetsEarlierClicks() {
        PrayerClickThrottle throttle = new PrayerClickThrottle();
        assertTrue(throttle.tryClick(Rs2PrayerEnum.AUGURY, 300));
        throttle.reset();
        assertTrue(throttle.tryClick(Rs2PrayerEnum.AUGURY, 300));
    }
}
