package net.runelite.client.plugins.microbot.blackjack;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SouthernTentLureSafetyTest
{
    private static final WorldPoint INSIDE = new WorldPoint(3350, 2956, 0);
    private static final WorldPoint CURTAIN = new WorldPoint(3350, 2957, 0);
    private static final WorldPoint OUTSIDE = new WorldPoint(3350, 2958, 0);

    @Test
    void doorwayDoesNotCountAsCompletedEviction()
    {
        assertFalse(SouthernTentLureSafety.canClose(OUTSIDE, CURTAIN, false));
        assertFalse(SouthernTentLureSafety.canClose(CURTAIN, OUTSIDE, false));
        assertTrue(SouthernTentLureSafety.canClose(OUTSIDE, new WorldPoint(3352, 2960, 0), false));
    }

    @Test
    void arrivalRequiresBothPlayerAndTargetOnTheCorrectSide()
    {
        assertFalse(SouthernTentLureSafety.canClose(INSIDE, OUTSIDE, true));
        assertFalse(SouthernTentLureSafety.canClose(OUTSIDE, INSIDE, true));
        assertFalse(SouthernTentLureSafety.canClose(INSIDE, CURTAIN, true));
        assertTrue(SouthernTentLureSafety.canClose(INSIDE, new WorldPoint(3349, 2954, 0), true));
        assertFalse(SouthernTentLureSafety.canClose(OUTSIDE, INSIDE, false));
    }

    @Test
    void rearRoomAndInternalHallwayRemainInside()
    {
        for (int x = 3349; x <= 3351; x++)
        {
            for (int y = 2947; y <= 2951; y++)
            {
                assertTrue(SouthernTentLureSafety.inside(new WorldPoint(x, y, 0)));
            }
        }
        assertTrue(SouthernTentLureSafety.inside(new WorldPoint(3350, 2952, 0)));
        assertFalse(SouthernTentLureSafety.inside(new WorldPoint(3349, 2952, 0)));
    }

    @Test
    void rearRoomRoutesOpenOnlyTheInnerCurtain()
    {
        WorldPoint rear = new WorldPoint(3350, 2949, 0);
        assertTrue(SouthernTentLureSafety.crossesInnerCurtain(INSIDE, rear));
        assertTrue(SouthernTentLureSafety.crossesInnerCurtain(rear, INSIDE));
        assertTrue(SouthernTentLureSafety.crossesInnerCurtain(new WorldPoint(3350, 2952, 0), INSIDE));
        assertFalse(SouthernTentLureSafety.crossesInnerCurtain(INSIDE, OUTSIDE));
        assertFalse(SouthernTentLureSafety.crossesInnerCurtain(INSIDE, new WorldPoint(3349, 2954, 0)));
        assertFalse(SouthernTentLureSafety.crossesInnerCurtain(rear, new WorldPoint(3350, 2951, 0)));
        assertFalse(SouthernTentLureSafety.crossesInnerCurtain(null, rear));
    }

    @Test
    void otherTentsAndPlanesDoNotCountAsStreetRelease()
    {
        assertFalse(SouthernTentLureSafety.onDestinationSide(new WorldPoint(3345, 2955, 0), false));
        assertFalse(SouthernTentLureSafety.onDestinationSide(new WorldPoint(3350, 2958, 1), false));
        assertFalse(SouthernTentLureSafety.onDestinationSide(null, false));
        assertFalse(SouthernTentLureSafety.canClose(INSIDE, null, true));
    }

    @Test
    void proximityWithoutFollowSignalCannotAdvanceRoute()
    {
        assertFalse(SouthernTentLureSafety.followingNearby(OUTSIDE, CURTAIN, false, 3));
        assertTrue(SouthernTentLureSafety.followingNearby(OUTSIDE, CURTAIN, true, 3));
        assertFalse(SouthernTentLureSafety.followingNearby(OUTSIDE, new WorldPoint(3350, 2962, 0), true, 3));
        assertFalse(SouthernTentLureSafety.followingNearby(OUTSIDE, new WorldPoint(3350, 2958, 1), true, 3));
        assertFalse(SouthernTentLureSafety.followingNearby(null, CURTAIN, true, 3));
    }

    @Test
    void repeatedVisibleCurtainAndDialogueRetriesHaveDeadlines()
    {
        for (BlackjackScript.SouthernTentPhase phase : new BlackjackScript.SouthernTentPhase[]{
                BlackjackScript.SouthernTentPhase.OPENING_CURTAIN,
                BlackjackScript.SouthernTentPhase.CLOSING_CURTAIN,
                BlackjackScript.SouthernTentPhase.POSITIONING_TO_CLOSE,
                BlackjackScript.SouthernTentPhase.ADVANCING_DIALOGUE,
                BlackjackScript.SouthernTentPhase.VERIFYING_FOLLOW})
        {
            assertFalse(phase.timedOut(11_999), phase.name());
            assertTrue(phase.timedOut(12_000), phase.name());
        }
        assertTrue(BlackjackScript.SouthernTentPhase.WAITING_FOR_DIALOGUE.timedOut(4_000));
        assertTrue(BlackjackScript.SouthernTentPhase.WAITING_FOR_RELEASE.timedOut(12_000));
        assertTrue(BlackjackScript.SouthernTentPhase.LEADING_THROUGH_CURTAIN.timedOut(20_000));
    }
}
