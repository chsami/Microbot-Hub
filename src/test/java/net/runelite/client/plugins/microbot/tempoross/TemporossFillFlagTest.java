package net.runelite.client.plugins.microbot.tempoross;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporossFillFlagTest
{
    @Test
    void loadingAtTheCrateHoldsTheFire()
    {
        assertTrue(TemporossScript.isLoadInProgress(true, true, false));
    }

    @Test
    void walkingToTheCrateHoldsTheFire()
    {
        assertTrue(TemporossScript.isLoadInProgress(true, false, true));
    }

    @Test
    void interruptedLoadLeavesAStaleFlagThatMustNotHoldTheFire()
    {
        assertFalse(TemporossScript.isLoadInProgress(true, false, false));
    }

    @Test
    void animatingWithoutAFillIsNotALoad()
    {
        assertFalse(TemporossScript.isLoadInProgress(false, true, true));
    }
}
