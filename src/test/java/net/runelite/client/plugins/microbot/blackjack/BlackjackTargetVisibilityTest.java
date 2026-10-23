package net.runelite.client.plugins.microbot.blackjack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackTargetVisibilityTest
{
    @Test
    void zoomEnforcementIsRestrictedToBlackjacking()
    {
        for (BlackjackState state : BlackjackState.values())
        {
            boolean expected = state == BlackjackState.FINDING_TARGET
                    || state == BlackjackState.KNOCKING_OUT
                    || state == BlackjackState.PICKPOCKETING
                    || state == BlackjackState.WAITING_FOR_TARGET_CLEAR;
            assertEquals(expected, BlackjackScript.isBlackjackingCameraState(state), state.name());
        }
    }

    @Test
    void disarmedVisibilityRecoveryHasBoundedWindow()
    {
        assertTrue(BlackjackScript.combatVisibilityRecoveryAllowed(1000, 1000));
        assertTrue(BlackjackScript.combatVisibilityRecoveryAllowed(5999, 1000));
        assertFalse(BlackjackScript.combatVisibilityRecoveryAllowed(6000, 1000));
        assertFalse(BlackjackScript.combatVisibilityRecoveryAllowed(10000, 1000));
    }

    @Test
    void recoveryZoomsOutGraduallyAndStopsAtMinimum()
    {
        assertEquals(663, BlackjackScript.zoomForHiddenTarget(736));
        assertEquals(597, BlackjackScript.zoomForHiddenTarget(663));
        assertEquals(200, BlackjackScript.zoomForHiddenTarget(210));
        assertEquals(200, BlackjackScript.zoomForHiddenTarget(200));
    }
}
