package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmergencyTaskTest
{
    private final FakeActions actions = new FakeActions();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(10);
    private final TormentedDemonsConfig config = new TormentedDemonsConfig() { };
    private final EmergencyTask task = new EmergencyTask(actions, config, trip, tick::get);

    public EmergencyTaskTest()
    {
        trip.setPhase(TripPhase.FIGHTING);
        actions.maxHp = 100;
        actions.hp = 100;
    }

    @Test
    public void becomesValidAtOrBelowThePercentageOfMaximumHitpoints()
    {
        actions.hp = 26;
        assertFalse(task.isValid());
        actions.hp = 25;
        assertTrue(task.isValid());
        actions.hp = 1;
        assertTrue(task.isValid());
    }

    @Test
    public void usesTheConfiguredPercentage()
    {
        TormentedDemonsConfig custom = new TormentedDemonsConfig()
        {
            @Override
            public int emergencyAt()
            {
                return 40;
            }
        };
        EmergencyTask strict = new EmergencyTask(actions, custom, trip, tick::get);
        actions.hp = 40;
        assertTrue(strict.isValid());
        assertFalse(task.isValid());
    }

    @Test
    public void isNotValidAtZeroHitpointsAtFeroxOrWhileBanking()
    {
        actions.hp = 0;
        assertFalse(task.isValid());
        actions.hp = 10;
        actions.point = TempleData.FEROX_BANK;
        assertFalse(task.isValid());
        actions.point = new WorldPoint(4133, 4382, 0);
        trip.setPhase(TripPhase.BANKING);
        assertFalse(task.isValid());
        trip.setPhase(TripPhase.TRAVELLING);
        assertTrue(task.isValid());
    }

    @Test
    public void failsWithoutARingAndRequestsNothing()
    {
        actions.hp = 10;
        task.execute();
        assertEquals("Hitpoints are low and no Ring of dueling is worn or carried; teleport out yourself, then restart the plugin", actions.getFailure());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void teleportsWithTheRingAndMarksTheTripAsLeaving()
    {
        actions.hp = 10;
        actions.have(2552, 1);
        task.execute();
        assertEquals("ring", actions.requests.get(0));
        assertEquals(TripPhase.LEAVING, trip.getPhase());
        assertEquals("Emergency teleport", trip.getLeaveReason());
        assertNull(actions.getFailure());
        assertEquals("Emergency teleport to Ferox Enclave", task.getStatus());
    }

    @Test
    public void aWornRingCounts()
    {
        actions.hp = 10;
        actions.worn.add(2552);
        task.execute();
        assertEquals(1, actions.requests.size());
    }

    @Test
    public void doesNotResendWhileTheTeleportIsStillLanding()
    {
        actions.hp = 10;
        actions.have(2552, 1);
        task.execute();
        tick.set(13);
        task.execute();
        tick.set(15);
        task.execute();
        assertEquals(1, actions.requests.size());
        tick.set(16);
        task.execute();
        assertEquals(2, actions.requests.size());
    }

    @Test
    public void aRefusedRequestIsTriedAgainOnTheNextTick()
    {
        actions.hp = 10;
        actions.have(2552, 1);
        actions.refused.add("ring");
        task.execute();
        actions.refused.clear();
        tick.set(11);
        task.execute();
        assertEquals(1, actions.requests.size());
    }
}
