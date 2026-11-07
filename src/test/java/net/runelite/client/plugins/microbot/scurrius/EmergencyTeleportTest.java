package net.runelite.client.plugins.microbot.scurrius;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmergencyTeleportTest {
    private static ScurriusConfig cfg(int foodLeft, int hpPercent) {
        ScurriusConfig config = mock(ScurriusConfig.class);
        when(config.emergencyFoodLeft()).thenReturn(foodLeft);
        when(config.emergencyHpPercent()).thenReturn(hpPercent);
        return config;
    }

    @Test
    void teleportsWhenFoodIsLowAndHealthUnderThePercent() {
        assertTrue(ScurriusScript.shouldEmergencyTeleport(2, 49, 100, cfg(2, 50)));
        assertTrue(ScurriusScript.shouldEmergencyTeleport(0, 10, 99, cfg(2, 50)));
        assertTrue(ScurriusScript.shouldEmergencyTeleport(1, 24, 50, cfg(2, 50)));
    }

    @Test
    void healthExactlyAtThePercentDoesNotTrip() {
        assertFalse(ScurriusScript.shouldEmergencyTeleport(2, 50, 100, cfg(2, 50)));
        assertFalse(ScurriusScript.shouldEmergencyTeleport(0, 25, 50, cfg(2, 50)));
    }

    @Test
    void enoughFoodNeverTrips() {
        assertFalse(ScurriusScript.shouldEmergencyTeleport(3, 1, 100, cfg(2, 50)));
    }

    @Test
    void usesRealMaxHitpointsNotBoosted() {
        // 80 boosted would be 80% of 100 real: safe; the percent is read against the real level passed in
        assertFalse(ScurriusScript.shouldEmergencyTeleport(1, 60, 100, cfg(2, 50)));
        assertTrue(ScurriusScript.shouldEmergencyTeleport(1, 60, 130, cfg(2, 50)));
    }

    @Test
    void invalidMaxHitpointsNeverTrips() {
        assertFalse(ScurriusScript.shouldEmergencyTeleport(0, 0, 0, cfg(2, 50)));
    }

    @Test
    void thresholdsComeFromTheConfig() {
        assertTrue(ScurriusScript.shouldEmergencyTeleport(5, 70, 100, cfg(5, 75)));
        assertFalse(ScurriusScript.shouldEmergencyTeleport(5, 70, 100, cfg(4, 75)));
    }
}
