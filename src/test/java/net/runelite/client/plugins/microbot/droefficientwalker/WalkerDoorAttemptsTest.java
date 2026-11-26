package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WalkerDoorAttemptsTest {
    private static final int WARGUILD_DOOR = 24306;
    private static final WorldPoint DOOR = new WorldPoint(2854, 3546, 0);

    @Test
    void crossingFromDoorTileResetsAttemptsForReturnTrip() {
        WalkerDoorAttempts attempts = new WalkerDoorAttempts();
        WorldPoint northSideOnDoorTile = new WorldPoint(2854, 3546, 0);
        WorldPoint southSideInsideRoom = new WorldPoint(2854, 3545, 0);

        assertEquals(WalkerDoorAttempts.Decision.ALLOW,
                attempts.request(WARGUILD_DOOR, DOOR, 8, northSideOnDoorTile, 337, false, 0));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW,
                attempts.request(WARGUILD_DOOR, DOOR, 8, northSideOnDoorTile, 337, false, 2_500));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW,
                attempts.request(WARGUILD_DOOR, DOOR, 8, northSideOnDoorTile, 337, false, 5_000));

        // Moving sideways on the north side must not clear the exhausted attempt.
        attempts.observe(new WorldPoint(2853, 3546, 0), 337, 6_000);
        assertEquals(WalkerDoorAttempts.Decision.BLOCK,
                attempts.request(WARGUILD_DOOR, DOOR, 8, new WorldPoint(2853, 3546, 0), 337, false, 7_500));
        assertNotNull(attempts.takeFailure());

        // Crossing to the south side resets the door so leaving the room dispatches normally.
        assertEquals(WalkerDoorAttempts.Decision.ALLOW,
                attempts.request(WARGUILD_DOOR, DOOR, 8, southSideInsideRoom, 337, false, 8_000));
        assertNull(attempts.takeFailure());
    }

    @Test
    void walkingAwayToAnimatorAndReturningClearsPreviousDoorAttempts() {
        WalkerDoorAttempts attempts = new WalkerDoorAttempts();
        WorldPoint insideDoor = new WorldPoint(2854, 3545, 0);
        WorldPoint animator = new WorldPoint(2851, 3537, 0);

        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 0));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 2_500));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 5_000));
        assertEquals(WalkerDoorAttempts.Decision.BLOCK, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 7_500));
        assertNotNull(attempts.takeFailure());

        // Observing the player > 5 tiles away releases the door even if no other door is clicked.
        attempts.observe(animator, 337, 10_000);

        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 15_000));
        assertNull(attempts.takeFailure());
    }

    @Test
    void staleAttemptsExpireAfterCooldownWhileImmediateReplansStayBlocked() {
        WalkerDoorAttempts attempts = new WalkerDoorAttempts();
        WorldPoint insideDoor = new WorldPoint(2854, 3545, 0);

        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 0));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 2_500));
        assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 5_000));
        assertEquals(WalkerDoorAttempts.Decision.BLOCK, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 7_500));
        assertNotNull(attempts.takeFailure());

        // Immediate replan 10s after the last attempt remains blocked.
        assertEquals(WalkerDoorAttempts.Decision.BLOCK, attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 15_000));
        assertNotNull(attempts.takeFailure());

        // After the staleness window passes, the door can be tried fresh.
        assertEquals(WalkerDoorAttempts.Decision.ALLOW,
                attempts.request(WARGUILD_DOOR, DOOR, 0, insideDoor, 337, false, 5_000 + WalkerDoorAttempts.STALE_ATTEMPT_MS));
        assertNull(attempts.takeFailure());
    }

    @Test
    void allFourWallOrientationsDetectCrossingFromTheDoorTile() {
        WorldPoint door = new WorldPoint(3000, 3000, 0);
        int[][] cases = {
                {1, 2999, 3000}, // west wall
                {2, 3000, 3001}, // north wall
                {4, 3001, 3000}, // east wall
                {8, 3000, 2999}  // south wall
        };
        for (int[] c : cases) {
            WalkerDoorAttempts attempts = new WalkerDoorAttempts();
            assertEquals(WalkerDoorAttempts.Decision.ALLOW, attempts.request(WARGUILD_DOOR, door, c[0], door, 337, false, 0));
            assertEquals(WalkerDoorAttempts.Decision.ALLOW,
                    attempts.request(WARGUILD_DOOR, door, c[0], new WorldPoint(c[1], c[2], 0), 337, false, 600));
        }
    }
}
