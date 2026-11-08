package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TripRulesTest
{
    @Test
    public void outOfFoodNeedsBothNoFoodAndAReasonToEat()
    {
        assertEquals("Out of food", TripRules.leaveReason(true, 0, false, 4, 5));
        assertNull(TripRules.leaveReason(false, 0, false, 4, 5));
        assertNull(TripRules.leaveReason(true, 1, false, 4, 5));
    }

    @Test
    public void outOfRestoresNeedsBothNoRestoresAndAReasonToDrink()
    {
        assertEquals("Out of prayer restores", TripRules.leaveReason(false, 5, true, 0, 5));
        assertNull(TripRules.leaveReason(false, 5, false, 0, 5));
        assertNull(TripRules.leaveReason(false, 5, true, 1, 5));
    }

    @Test
    public void foodIsReportedBeforeRestores()
    {
        assertEquals("Out of food", TripRules.leaveReason(true, 0, true, 0, 0));
    }

    @Test
    public void aFullInventoryWithNoFoodEndsTheTrip()
    {
        assertEquals("Inventory full", TripRules.leaveReason(false, 0, false, 4, 0));
        assertNull(TripRules.leaveReason(false, 3, false, 4, 0));
        assertNull(TripRules.leaveReason(false, 0, false, 4, 1));
    }

    @Test
    public void startingInsideTheTempleFightsOrLeavesByTheReason()
    {
        assertEquals(TripPhase.FIGHTING, TripRules.startPhase(false, true, false, null));
        assertEquals(TripPhase.LEAVING, TripRules.startPhase(false, true, false, "Out of food"));
        assertEquals(TripPhase.LEAVING, TripRules.startPhase(false, true, true, "Out of food"));
    }

    @Test
    public void startingOnTheRouteTravelsAndStartingAtFeroxBanks()
    {
        assertEquals(TripPhase.TRAVELLING, TripRules.startPhase(false, false, true, null));
        assertEquals(TripPhase.TRAVELLING, TripRules.startPhase(true, false, true, "Out of food"));
        assertEquals(TripPhase.BANKING, TripRules.startPhase(true, false, false, null));
        assertEquals(TripPhase.BANKING, TripRules.startPhase(true, false, false, "Out of food"));
    }

    @Test
    public void startingAnywhereElseHasNoPhase()
    {
        assertNull(TripRules.startPhase(false, false, false, null));
    }
}
