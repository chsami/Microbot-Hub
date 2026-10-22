package net.runelite.client.plugins.microbot.gotr;

import org.junit.jupiter.api.Test;

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
}
