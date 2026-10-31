package net.runelite.client.plugins.microbot.gotr;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GotrSupplyDecisionTest
{
    @Test
    void portalIsTakenBelowTheEssenceThreshold()
    {
        assertTrue(GotrScript.shouldTakePortal(0, 20));
        assertTrue(GotrScript.shouldTakePortal(19, 20));
    }

    @Test
    void portalIsSkippedAtOrAboveTheEssenceThreshold()
    {
        assertFalse(GotrScript.shouldTakePortal(20, 20));
        assertFalse(GotrScript.shouldTakePortal(26, 20));
    }

    @Test
    void heldFragmentsAreUsedBeforeMiningAgain()
    {
        assertFalse(GotrScript.isOutOfFragments(true, false));
        assertFalse(GotrScript.isOutOfFragments(true, true));
    }

    @Test
    void outOfFragmentsOnlyWhenNoneHeldAndRoomLeft()
    {
        assertTrue(GotrScript.isOutOfFragments(false, false));
        assertFalse(GotrScript.isOutOfFragments(false, true));
    }

    @Test
    void miningStopsOnceFragmentsCoverInventoryAndPouches()
    {
        assertFalse(GotrScript.hasFragmentsToFillInventory(39, 13, 27));
        assertTrue(GotrScript.hasFragmentsToFillInventory(40, 13, 27));
        assertTrue(GotrScript.hasFragmentsToFillInventory(120, 13, 27));
    }

    @Test
    void largeMineIsLeftOnceFragmentsReachTheCap()
    {
        assertTrue(GotrScript.shouldMineLargeRemains(129, 130, true, 30));
        assertFalse(GotrScript.shouldMineLargeRemains(130, 130, true, 30));
        assertFalse(GotrScript.shouldMineLargeRemains(732, 130, true, -1));
        assertFalse(GotrScript.shouldMineLargeRemains(732, 130, false, -1));
    }

    @Test
    void unknownPortalTimeDuringARoundLeavesTheLargeMine()
    {
        assertFalse(GotrScript.shouldMineLargeRemains(50, 130, true, -1));
    }

    @Test
    void unknownPortalTimeBeforeARoundStillMinesTheLargeMine()
    {
        assertTrue(GotrScript.shouldMineLargeRemains(0, 130, false, -1));
    }

    @Test
    void largeMineWindowClosesEightyFiveSecondsAfterThePortal()
    {
        assertTrue(GotrScript.shouldMineLargeRemains(50, 130, true, 0));
        assertTrue(GotrScript.shouldMineLargeRemains(50, 130, true, 84));
        assertFalse(GotrScript.shouldMineLargeRemains(50, 130, true, 85));
    }

    @Test
    void largeMineWindowFollowsThePortalClockThroughARound()
    {
        GotrPortalClock clock = new GotrPortalClock();
        Instant start = Instant.parse("2026-10-31T06:39:02Z");
        clock.roundStarted(start);
        assertTrue(GotrScript.shouldMineLargeRemains(50, 130, clock.isRoundRunning(), clock.secondsSincePortal(start.plusSeconds(39))));
        assertTrue(GotrScript.shouldMineLargeRemains(50, 130, clock.isRoundRunning(), clock.secondsSincePortal(start.plusSeconds(124))));
        assertFalse(GotrScript.shouldMineLargeRemains(50, 130, clock.isRoundRunning(), clock.secondsSincePortal(start.plusSeconds(125))));
        assertFalse(GotrScript.shouldMineLargeRemains(50, 130, clock.isRoundRunning(), clock.secondsSincePortal(start.plusSeconds(GotrPortalClock.STALE_AFTER_SECONDS + 1))));
    }
}
