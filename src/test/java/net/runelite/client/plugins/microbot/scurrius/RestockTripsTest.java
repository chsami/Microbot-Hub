package net.runelite.client.plugins.microbot.scurrius;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestockTripsTest {
    @Test
    void tripStartsOnlyAfterEnoughKillsWithBossDeadLootDoneAndFoodLeft() {
        assertTrue(RestockTrips.shouldStartRestockTrip(3, 3, false, false, 5));
        assertTrue(RestockTrips.shouldStartRestockTrip(7, 3, false, false, 1));
        assertFalse(RestockTrips.shouldStartRestockTrip(2, 3, false, false, 5)); // not enough kills
        assertFalse(RestockTrips.shouldStartRestockTrip(3, 3, true, false, 5));  // boss alive: never mid-fight
        assertFalse(RestockTrips.shouldStartRestockTrip(3, 3, false, true, 5));  // loot still on the floor
        assertFalse(RestockTrips.shouldStartRestockTrip(3, 3, false, false, 0)); // no food: the normal out-of-food path
        assertFalse(RestockTrips.shouldStartRestockTrip(5, 0, false, false, 5)); // no target
    }

    @Test
    void targetIsUniformInRangeAndCoversEveryValue() {
        Random random = new Random(11);
        boolean[] seen = new boolean[6];
        for (int i = 0; i < 2000; i++) {
            int target = RestockTrips.rollTarget(2, 5, random);
            assertTrue(target >= 2 && target <= 5);
            seen[target] = true;
        }
        assertTrue(seen[2] && seen[3] && seen[4] && seen[5]);
    }

    @Test
    void swappedAndTooSmallRangesArePutRight() {
        Random random = new Random(1);
        for (int i = 0; i < 500; i++) {
            int target = RestockTrips.rollTarget(9, 4, random);
            assertTrue(target >= 4 && target <= 9);
            assertTrue(RestockTrips.rollTarget(0, 0, random) == 1);
        }
        assertTrue(RestockTrips.rollTarget(6, 6, random) == 6);
    }

    @Test
    void bankVisitsWithoutFightingAreNotRestockTrips() {
        assertFalse(RestockTrips.isRealTrip(0, false)); // the visit at the start, or a repeated one
        assertTrue(RestockTrips.isRealTrip(1, false));  // a kill
        assertTrue(RestockTrips.isRealTrip(0, true));   // fought, no kill yet (an emergency teleport)
    }

    @Test
    void startupAndRepeatedVisitsNeverCountTowardABreakOrRerollTheTarget() {
        // mirrors the script: counts and the kill target change only on a real trip
        int trips = 0;
        int targetRolls = 0;
        int kills = 0;
        boolean fought = false;
        for (int visit = 0; visit < 3; visit++) { // three visits with no fighting between them
            if (RestockTrips.isRealTrip(kills, fought)) {
                trips++;
                targetRolls++;
                kills = 0;
            }
            fought = false;
        }
        assertEquals(0, trips);
        assertEquals(0, targetRolls);
        kills = 2;
        fought = true;
        if (RestockTrips.isRealTrip(kills, fought)) {
            trips++;
            targetRolls++;
        }
        assertEquals(1, trips);
        assertEquals(1, targetRolls);
    }
}
