package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.MutableConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonRoom;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TravelTaskTest
{
    private static final int RING = 2552;
    private static final String STALLED = "Could not reach the temple; walk to Ferox Enclave or into the temple, then restart the plugin";
    private static final WorldPoint LEDGE_ONE = new WorldPoint(4062, 4550, 1);
    private static final WorldPoint LEDGE_TWO = new WorldPoint(4062, 4550, 2);
    private final FakeActions actions = new FakeActions();
    private final FakeWalking walking = new FakeWalking(actions.world);
    private final MutableConfig config = new MutableConfig();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(100);
    private final TravelTask task = new TravelTask(actions, config, trip, walking, tick::get);

    @Test
    public void isValidWhileLeavingTravellingOrFightingOutsideTheSelectedRoom()
    {
        actions.point = DemonRoom.PLINTH_CHAMBER.getCenter();
        trip.setPhase(TripPhase.LEAVING);
        assertTrue(task.isValid());
        trip.setPhase(TripPhase.TRAVELLING);
        assertTrue(task.isValid());
        trip.setPhase(TripPhase.BANKING);
        assertFalse(task.isValid());
        trip.setPhase(TripPhase.FIGHTING);
        assertFalse(task.isValid(), "Inside the room");
        actions.point = TempleData.TEMPLE_ENTRY;
        assertTrue(task.isValid());
        config.room = DemonRoom.WEST_NORTH;
        actions.point = DemonRoom.WEST_NORTH.getCenter();
        assertFalse(task.isValid());
        actions.point = DemonRoom.PLINTH_CHAMBER.getCenter();
        assertTrue(task.isValid(), "Inside the temple but not the selected room");
    }

    @Test
    public void doesNotWalkBackToTheRoomWhileADemonIsEngaged()
    {
        trip.setPhase(TripPhase.FIGHTING);
        actions.point = TempleData.TEMPLE_ENTRY;
        trip.engage(actions.world.demon(3).npc, 100);
        assertFalse(task.isValid());
        trip.engage(null, 101);
        assertTrue(task.isValid());
    }

    @Test
    public void leavingTeleportsWithTheRingAndKeepsTheReasonInTheStatus()
    {
        actions.have(RING, 1);
        actions.point = DemonRoom.PLINTH_CHAMBER.getCenter();
        trip.leave("Out of food");
        task.execute();
        assertEquals(List.of("ring"), actions.requests);
        assertEquals("Teleporting to Ferox Enclave: Out of food", task.getStatus());
        assertEquals(TripPhase.LEAVING, trip.getPhase());
        assertEquals(1, walking.cancellations);
    }

    @Test
    public void leavingBecomesBankingOnArrivalAtFerox()
    {
        actions.have(RING, 1);
        actions.point = TempleData.FEROX_ARRIVAL;
        trip.leave("Out of food");
        task.execute();
        assertEquals(TripPhase.BANKING, trip.getPhase());
        assertNull(trip.getLeaveReason());
        assertTrue(actions.requests.isEmpty());
        assertEquals("Arrived at Ferox Enclave", task.getStatus());
    }

    @Test
    public void leavingWithoutARingFailsWithAReason()
    {
        actions.point = DemonRoom.PLINTH_CHAMBER.getCenter();
        trip.leave("Out of food");
        task.execute();
        assertEquals("No Ring of dueling to leave the temple; teleport out yourself, then restart the plugin", actions.getFailure());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void aWornRingCanBeUsedToLeave()
    {
        actions.worn.add(RING);
        actions.point = DemonRoom.PLINTH_CHAMBER.getCenter();
        trip.leave("Inventory full");
        task.execute();
        assertEquals(List.of("ring"), actions.requests);
    }

    @Test
    public void leavingResendsTheTeleportAfterSixTicksOnly()
    {
        actions.have(RING, 1);
        actions.point = DemonRoom.PLINTH_CHAMBER.getCenter();
        trip.leave("Out of food");
        task.execute();
        for (int elapsed = 1; elapsed <= 5; elapsed++)
        {
            tick.set(100 + elapsed);
            task.execute();
            assertEquals(1, actions.requests.size(), "Tick " + elapsed);
        }
        tick.set(106);
        task.execute();
        assertEquals(2, actions.requests.size());
        tick.set(107);
        actions.point = new WorldPoint(actions.point.getX() + 1, actions.point.getY(), 0);
        task.execute();
        assertEquals(2, actions.requests.size(), "Moving inside the temple is not a new step");
    }

    @Test
    public void travellingReadsTheScrollAtFerox()
    {
        actions.have(TempleData.TEMPLE_TELEPORT, 3);
        actions.point = TempleData.FEROX_BANK;
        trip.setPhase(TripPhase.TRAVELLING);
        task.execute();
        assertEquals(List.of("inventory:" + TempleData.TEMPLE_TELEPORT + ":Teleport"), actions.requests);
        assertEquals("Reading a Guthixian temple teleport", task.getStatus());
    }

    @Test
    public void travellingWithoutAScrollAtFeroxFailsWithAReason()
    {
        actions.point = TempleData.FEROX_BANK;
        trip.setPhase(TripPhase.TRAVELLING);
        task.execute();
        assertEquals("No Guthixian temple teleports carried; carry some, then restart the plugin", actions.getFailure());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void climbsTheFirstWallFromTheArrivalAreaOnPlaneZero()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = TempleData.SCROLL_ARRIVAL;
        task.execute();
        assertTrue(actions.requests.isEmpty(), "No wall object known yet");
        assertEquals("Climbing the first wall", task.getStatus());
        put(TempleData.FIRST_WALL);
        tick.addAndGet(1);
        task.execute();
        assertEquals(List.of("object:" + TempleData.FIRST_WALL + ":Climb-up"), actions.requests);
    }

    @Test
    public void climbsTheSecondWallFromTheLedgeOnPlaneOne()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = LEDGE_ONE;
        put(TempleData.FIRST_WALL);
        put(TempleData.SECOND_WALL);
        task.execute();
        assertEquals(List.of("object:" + TempleData.SECOND_WALL + ":Climb-up"), actions.requests);
        assertEquals("Climbing the second wall", task.getStatus());
    }

    @Test
    public void climbsThroughTheTempleOpeningFromThePlaneTwoLedge()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = LEDGE_TWO;
        put(TempleData.TEMPLE_DOOR);
        task.execute();
        assertEquals(List.of("object:" + TempleData.TEMPLE_DOOR + ":Climb-through"), actions.requests);
        assertEquals("Climbing through the opening", task.getStatus());
    }

    @Test
    public void theOtherOpeningIdWorksToo()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = LEDGE_TWO;
        put(TempleData.TEMPLE_OPENING);
        task.execute();
        assertEquals(List.of("object:" + TempleData.TEMPLE_OPENING + ":Climb-through"), actions.requests);
    }

    @Test
    public void insideTheTempleTheTripBecomesFighting()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = TempleData.TEMPLE_ENTRY;
        task.execute();
        assertEquals(TripPhase.FIGHTING, trip.getPhase());
        assertEquals("Entered the temple", task.getStatus());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void anUnknownPlaceWaitsForTheTeleport()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = new WorldPoint(3200, 3200, 0);
        task.execute();
        assertEquals("Waiting for the teleport", task.getStatus());
        assertTrue(actions.requests.isEmpty());
        assertNull(actions.getFailure());
    }

    @Test
    public void followsTheWholeRouteInOrderOnePlaneAtATime()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.have(TempleData.TEMPLE_TELEPORT, 1);
        put(TempleData.FIRST_WALL);
        put(TempleData.SECOND_WALL);
        put(TempleData.TEMPLE_DOOR);
        WorldPoint[] route = {TempleData.FEROX_BANK, TempleData.SCROLL_ARRIVAL, LEDGE_ONE, LEDGE_TWO};
        String[] expected = {"inventory:" + TempleData.TEMPLE_TELEPORT + ":Teleport",
            "object:" + TempleData.FIRST_WALL + ":Climb-up", "object:" + TempleData.SECOND_WALL + ":Climb-up",
            "object:" + TempleData.TEMPLE_DOOR + ":Climb-through"};
        for (int index = 0; index < route.length; index++)
        {
            actions.point = route[index];
            tick.addAndGet(1);
            task.execute();
            assertEquals(expected[index], actions.requests.get(index));
        }
        actions.point = TempleData.TEMPLE_ENTRY;
        tick.addAndGet(1);
        task.execute();
        assertEquals(TripPhase.FIGHTING, trip.getPhase());
    }

    @Test
    public void doesNotResendWithinSixTicksUnlessTheStepChanged()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.have(TempleData.TEMPLE_TELEPORT, 1);
        actions.point = TempleData.FEROX_BANK;
        task.execute();
        for (int elapsed = 1; elapsed <= 5; elapsed++)
        {
            tick.set(100 + elapsed);
            task.execute();
            assertEquals(1, actions.requests.size(), "Tick " + elapsed);
        }
        tick.set(106);
        task.execute();
        assertEquals(2, actions.requests.size());
        tick.set(107);
        task.execute();
        assertEquals(2, actions.requests.size());
        actions.point = new WorldPoint(TempleData.FEROX_BANK.getX() + 1, TempleData.FEROX_BANK.getY(), 0);
        task.execute();
        assertEquals(2, actions.requests.size(), "Still at Ferox Enclave");
    }

    @Test
    public void waitsForTheLandingAnimationBeforeTheNextClimb()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        put(TempleData.SECOND_WALL);
        actions.point = LEDGE_ONE;
        actions.world.animation = 11291;
        task.execute();
        assertTrue(actions.requests.isEmpty(), "Still landing from the first wall");
        actions.world.animation = -1;
        tick.set(101);
        task.execute();
        assertEquals(List.of("object:" + TempleData.SECOND_WALL + ":Climb-up"), actions.requests);
    }

    @Test
    public void doesNotClimbAgainWhileWalkingToTheWallOrClimbingIt()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        put(TempleData.FIRST_WALL);
        actions.point = TempleData.SCROLL_ARRIVAL;
        task.execute();
        assertEquals(1, actions.requests.size());
        tick.set(101);
        actions.point = new WorldPoint(TempleData.SCROLL_ARRIVAL.getX(), TempleData.SCROLL_ARRIVAL.getY() - 1, 0);
        task.execute();
        actions.world.animation = 11290;
        for (int elapsed = 2; elapsed <= 8; elapsed++)
        {
            tick.set(100 + elapsed);
            task.execute();
            assertEquals(1, actions.requests.size(), "Tick " + elapsed);
        }
        actions.world.animation = -1;
        tick.set(109);
        task.execute();
        assertEquals(2, actions.requests.size(), "A climb that never happened is sent again");
    }

    @Test
    public void failsWhenThirtyOneTicksPassWithoutMovement()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = new WorldPoint(3200, 3200, 0);
        task.execute();
        tick.set(130);
        task.execute();
        assertNull(actions.getFailure(), "Thirty ticks is still within the limit");
        tick.set(131);
        task.execute();
        assertEquals(STALLED, actions.getFailure());
    }

    @Test
    public void movementRestartsTheStallTimer()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = new WorldPoint(3200, 3200, 0);
        task.execute();
        tick.set(125);
        actions.point = new WorldPoint(3201, 3200, 0);
        task.execute();
        tick.set(155);
        task.execute();
        assertNull(actions.getFailure());
        tick.set(156);
        task.execute();
        assertEquals(STALLED, actions.getFailure());
    }

    @Test
    public void aPhaseChangeRestartsTheStallTimer()
    {
        actions.have(RING, 1);
        actions.point = new WorldPoint(3200, 3200, 0);
        trip.setPhase(TripPhase.TRAVELLING);
        task.execute();
        tick.set(129);
        trip.setPhase(TripPhase.LEAVING);
        task.execute();
        tick.set(130);
        trip.setPhase(TripPhase.TRAVELLING);
        task.execute();
        tick.set(160);
        task.execute();
        assertNull(actions.getFailure());
        tick.set(161);
        task.execute();
        assertEquals(STALLED, actions.getFailure());
    }

    @Test
    public void resetForgetsTheOldPositionSoTheStallTimerStartsAtTheNextDecision()
    {
        trip.setPhase(TripPhase.TRAVELLING);
        actions.point = new WorldPoint(3200, 3200, 0);
        task.execute();
        tick.set(125);
        task.reset();
        tick.set(155);
        task.execute();
        tick.set(185);
        task.execute();
        assertNull(actions.getFailure());
        tick.set(186);
        task.execute();
        assertEquals(STALLED, actions.getFailure());
    }

    @Test
    public void fightingOutsideTheRoomWalksToTheRoomCentre()
    {
        trip.setPhase(TripPhase.FIGHTING);
        actions.point = TempleData.TEMPLE_ENTRY;
        task.execute();
        assertEquals(List.of(DemonRoom.PLINTH_CHAMBER.getCenter()), walking.destinations);
        assertEquals("Walking to Plinth chamber", task.getStatus());
        assertNull(actions.getFailure());
        config.room = DemonRoom.WEST_SOUTH;
        task.execute();
        assertEquals(DemonRoom.WEST_SOUTH.getCenter(), walking.destinations.get(1));
        assertEquals("Walking to West room, south", task.getStatus());
    }

    @Test
    public void aWalkingFailureWhileFightingOutsideTheRoomEndsTheSession()
    {
        trip.setPhase(TripPhase.FIGHTING);
        actions.point = TempleData.TEMPLE_ENTRY;
        walking.state = Walking.State.FAILED;
        task.execute();
        assertEquals("Could not walk to Plinth chamber; walk there yourself, then restart the plugin", actions.getFailure());
    }

    @Test
    public void fightingOutsideTheTempleFailsInsteadOfWalking()
    {
        trip.setPhase(TripPhase.FIGHTING);
        actions.point = TempleData.FEROX_BANK;
        task.execute();
        assertEquals("Left the temple; walk to Ferox Enclave, then restart the plugin", actions.getFailure());
        assertTrue(walking.destinations.isEmpty());
    }

    private void put(int id)
    {
        TileObject object = actions.world.object(id, 2, 2, "Climb-up", "Climb-through").object;
        actions.objects.put(id, object);
    }
}
