package net.runelite.client.plugins.microbot.karambwans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishTravelFailureTest {
    private static final int MAX = 5;

    @Test
    void houseWithoutRingStopsOnTheFirstAttemptAfterTheTeleportLanded() {
        assertTrue(FishTravelFailure.shouldStop(FishTravelFailure.HOUSE_RING_NOT_FOUND, true, 1, MAX));
    }

    @Test
    void failedHouseTeleportIsRetriedUntilTheCap() {
        assertFalse(FishTravelFailure.shouldStop(FishTravelFailure.HOUSE_RING_NOT_FOUND, false, 1, MAX));
        assertFalse(FishTravelFailure.shouldStop(FishTravelFailure.HOUSE_RING_NOT_FOUND, false, 4, MAX));
        assertTrue(FishTravelFailure.shouldStop(FishTravelFailure.HOUSE_RING_NOT_FOUND, false, 5, MAX));
    }

    @Test
    void ringTravelFailuresAreRetriedUntilTheCap() {
        assertFalse(FishTravelFailure.shouldStop(FishTravelFailure.HOUSE_RING_TRAVEL, true, 1, MAX));
        assertFalse(FishTravelFailure.shouldStop(FishTravelFailure.ZANARIS_RING, false, 4, MAX));
        assertTrue(FishTravelFailure.shouldStop(FishTravelFailure.HOUSE_RING_TRAVEL, false, 5, MAX));
        assertTrue(FishTravelFailure.shouldStop(FishTravelFailure.ZANARIS_RING, false, 5, MAX));
    }

    @Test
    void eachCauseTellsThePlayerWhatToFix() {
        assertTrue(FishTravelFailure.HOUSE_RING_NOT_FOUND.getMessage().contains("house teleport"));
        assertTrue(FishTravelFailure.HOUSE_RING_TRAVEL.getMessage().contains("Dial DKP"));
        assertTrue(FishTravelFailure.ZANARIS_RING.getMessage().contains("last destination"));
        assertNotEquals(FishTravelFailure.HOUSE_RING_TRAVEL.getMessage(), FishTravelFailure.ZANARIS_RING.getMessage());
    }
}
