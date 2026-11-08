package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TripStateTest
{
    @Test
    public void leavingRecordsTheReasonAndArrivingAtTheBankClearsIt()
    {
        TripState trip = new TripState();
        trip.leave("Out of food");
        assertEquals(TripPhase.LEAVING, trip.getPhase());
        assertEquals("Out of food", trip.getLeaveReason());
        trip.arrivedAtBank();
        assertEquals(TripPhase.BANKING, trip.getPhase());
        assertNull(trip.getLeaveReason());
    }

    @Test
    public void engagingRemembersTheLastTickAndLosingTheTargetKeepsIt()
    {
        TripState trip = new TripState();
        assertTrue(trip.ticksSinceEngaged(0) > 1000);
        NPC demon = new TestWorld().demon(1).npc;
        trip.engage(demon, 5);
        assertSame(demon, trip.getTarget());
        assertEquals(3, trip.ticksSinceEngaged(8));
        trip.engage(null, 9);
        assertNull(trip.getTarget());
        assertEquals(4, trip.ticksSinceEngaged(9));
    }

    @Test
    public void onlyAHitAfterTheSwitchShowsTheAttackGoesOn()
    {
        TripState trip = new TripState();
        assertFalse(trip.isAttackNeeded(100));
        trip.equipmentChanged(100);
        trip.hitLanded(103);
        assertTrue(trip.isAttackNeeded(108), "a hit from the old weapon");
        trip.hitLanded(104);
        assertFalse(trip.isAttackNeeded(108));
        trip.equipmentChanged(110);
        trip.attackSent();
        assertFalse(trip.isAttackNeeded(118));
    }

    @Test
    public void tripsAreCounted()
    {
        TripState trip = new TripState();
        trip.tripStarted();
        trip.tripStarted();
        assertEquals(2, trip.getTrips());
        trip.setStyle(CombatStyle.RANGED);
        assertEquals(CombatStyle.RANGED, trip.getStyle());
    }
}
