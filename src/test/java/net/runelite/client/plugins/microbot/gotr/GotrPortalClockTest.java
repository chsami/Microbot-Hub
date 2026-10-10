package net.runelite.client.plugins.microbot.gotr;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GotrPortalClockTest
{
    private static final Instant T0 = Instant.parse("2026-10-05T12:00:00Z");

    private static Instant at(long seconds)
    {
        return T0.plusSeconds(seconds);
    }

    @Test
    void unknownBeforeAnyRoundEvent()
    {
        assertEquals(-1, new GotrPortalClock().secondsSincePortal(T0));
    }

    @Test
    void firstPortalOfRoundIsAdjusted()
    {
        GotrPortalClock clock = new GotrPortalClock();
        clock.roundStarted(at(0));
        assertTrue(clock.isFirstPortal());
        assertEquals(60, clock.secondsSincePortal(at(100)));
    }

    @Test
    void portalSpawnClearsFirstPortalAdjustment()
    {
        GotrPortalClock clock = new GotrPortalClock();
        clock.roundStarted(at(0));
        clock.portalSpawned(at(160));
        assertFalse(clock.isFirstPortal());
        assertEquals(30, clock.secondsSincePortal(at(190)));
        clock.portalDespawned(at(200));
        assertEquals(10, clock.secondsSincePortal(at(210)));
    }

    @Test
    void roundEndDoesNotLeakPortalTimeIntoNextRound()
    {
        GotrPortalClock clock = new GotrPortalClock();
        clock.roundStarted(at(0));
        clock.portalSpawned(at(160));
        clock.portalDespawned(at(190));
        clock.reset();

        int missedStartMessage = clock.secondsSincePortal(at(190 + 120));
        assertEquals(-1, missedStartMessage);
        assertTrue(clock.isFirstPortal());

        clock.roundStarted(at(400));
        assertEquals(20, clock.secondsSincePortal(at(460)));
    }

    @Test
    void staleTimestampWithoutResetIsTreatedAsUnknown()
    {
        GotrPortalClock clock = new GotrPortalClock();
        clock.roundStarted(at(0));
        clock.portalSpawned(at(160));
        assertTrue(clock.secondsSincePortal(at(160 + GotrPortalClock.STALE_AFTER_SECONDS)) > 85);
        assertEquals(-1, clock.secondsSincePortal(at(160 + GotrPortalClock.STALE_AFTER_SECONDS + 1)));
        assertEquals(-1, clock.secondsSincePortal(at(160 + 30 * 60)));
    }

    @Test
    void portalDespawnAfterRoundEndDoesNotRestartClock()
    {
        GotrPortalClock clock = new GotrPortalClock();
        clock.roundStarted(at(0));
        clock.portalSpawned(at(160));
        clock.reset();
        clock.portalDespawned(at(170));
        assertEquals(-1, clock.secondsSincePortal(at(300)));
        assertTrue(clock.isFirstPortal());
    }

    @Test
    void clockIsNotAffectedByFutureTimestamps()
    {
        GotrPortalClock clock = new GotrPortalClock();
        clock.portalSpawned(at(100));
        assertEquals(-1, clock.secondsSincePortal(at(50)));
    }
}
