package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmergencyTaskTest
{
    @Test
    public void waitsForObservedArrivalEvenIfHealthRecovers()
    {
        FakeActions actions = new FakeActions();
        EmergencyTask task = new EmergencyTask(actions);
        task.execute();
        assertEquals(1, actions.requests);
        assertEquals("", actions.failure);
        actions.hp = 60;
        assertTrue(task.isValid());
        task.execute();
        assertEquals("", actions.failure);
        actions.point = new WorldPoint(3510, 3480, 0);
        task.execute();
        assertTrue(actions.failure.contains("emergency level"));
        assertEquals(0, actions.bankRequests);
    }

    @Test
    public void arrivalTurnsOffProtectionBeforeStopping()
    {
        FakeActions actions = new FakeActions();
        actions.protection = Prayer.PROTECT_FROM_MELEE;
        EmergencyTask task = new EmergencyTask(actions);
        task.execute();
        assertEquals(0, actions.prayerOffs, "Protection stays on until the ring lands");
        actions.point = new WorldPoint(3151, 3636, 0);
        task.execute();
        assertEquals(1, actions.prayerOffs);
        assertEquals("", actions.failure);
        actions.protection = null;
        task.execute();
        assertTrue(actions.failure.contains("emergency level"));
    }

    @Test
    public void unobservedProtectionOffStillStopsWithinTheObservationWindow()
    {
        FakeActions actions = new FakeActions();
        actions.protection = Prayer.PROTECT_FROM_MAGIC;
        EmergencyTask task = new EmergencyTask(actions);
        task.execute();
        actions.point = new WorldPoint(3151, 3636, 0);
        task.execute();
        actions.now = 8;
        task.execute();
        assertTrue(actions.failure.contains("emergency level"));
    }

    @Test
    public void ordinarySmallMovementDoesNotProveTeleportArrival()
    {
        FakeActions actions = new FakeActions();
        EmergencyTask task = new EmergencyTask(actions);
        task.execute();
        actions.point = new WorldPoint(3558, 9704, 3);
        task.execute();
        assertEquals("", actions.failure);
        assertEquals(1, actions.requests);
    }

    @Test
    public void movementBeforeDispatchDoesNotCountAsTeleportArrival()
    {
        FakeActions actions = new FakeActions();
        actions.dispatch = false;
        EmergencyTask task = new EmergencyTask(actions);
        task.execute();
        actions.point = new WorldPoint(3510, 3480, 0);
        task.execute();
        assertEquals("", actions.failure);
        assertEquals(0, actions.requests);
        actions.now = 24;
        task.execute();
        assertEquals(1, actions.bankRequests);
        assertFalse(task.isValid());
    }

    @Test
    public void missingRingReleasesSuppliesAndEscapeWithoutRepeatedBankRequests()
    {
        FakeActions actions = new FakeActions();
        actions.ring = false;
        EmergencyTask task = new EmergencyTask(actions);
        task.execute();
        task.execute();
        assertEquals(1, actions.bankRequests);
        assertEquals(0, actions.requests);
        assertFalse(task.isValid());
        assertEquals("", actions.failure);
    }

    @Test
    public void threeDispatchedFailuresFallBackToEscape()
    {
        FakeActions actions = new FakeActions();
        EmergencyTask task = new EmergencyTask(actions);
        for (int tick : new int[] {0, 8, 16, 24})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.requests);
        assertEquals(1, actions.bankRequests);
        assertFalse(task.isValid());
        assertEquals("", actions.failure);
    }

    @Test
    public void zeroHitpointsCannotInitiateTeleportAndBankOpenReleasesBanking()
    {
        FakeActions actions = new FakeActions();
        actions.hp = 0;
        EmergencyTask task = new EmergencyTask(actions);
        assertFalse(task.isValid());
        actions.hp = 20;
        actions.open = true;
        task.execute();
        assertEquals(0, actions.requests);
        assertEquals(1, actions.bankRequests);
        assertFalse(task.isValid());
    }

    @Test
    public void theRingIsNotUsedAwayFromBarrows()
    {
        FakeActions actions = new FakeActions();
        actions.atBarrows = false;
        EmergencyTask task = new EmergencyTask(actions);
        assertFalse(task.isValid(), "Ferox already restores Hitpoints; teleporting there wastes a charge");
        task.execute();
        assertEquals(0, actions.requests);
        assertEquals(0, actions.bankRequests);
    }

    @Test
    public void thresholdIsAPercentageOfMaximumHitpoints()
    {
        FakeActions actions = new FakeActions();
        actions.maxHp = 40;
        actions.hp = 11;
        assertFalse(new EmergencyTask(actions).isValid(), "11 of 40 is above 25%");
        actions.hp = 10;
        assertTrue(new EmergencyTask(actions).isValid(), "10 of 40 is 25%");
    }

    @Test
    public void aPendingStopTeleportsOutAtFullHealth()
    {
        FakeActions actions = new FakeActions();
        actions.hp = 99;
        actions.stopping = "Combat stalled";
        EmergencyTask task = new EmergencyTask(actions);
        assertTrue(task.isValid());
        task.execute();
        assertEquals(1, actions.requests);
        actions.point = new WorldPoint(3151, 3636, 0);
        task.execute();
        assertTrue(actions.failure.contains("emergency level"));
        assertEquals(0, actions.bankRequests);
    }

    @Test
    public void aPendingStopAwayFromBarrowsDoesNotUseTheRing()
    {
        FakeActions actions = new FakeActions();
        actions.stopping = "Combat stalled";
        actions.atBarrows = false;
        assertFalse(new EmergencyTask(actions).isValid());
    }

    @Test
    public void aPendingStopWithoutTheRingEndsInsteadOfStartingABankTrip()
    {
        FakeActions actions = new FakeActions();
        actions.stopping = "Combat stalled";
        actions.ring = false;
        EmergencyTask task = new EmergencyTask(actions);
        task.execute();
        assertEquals(1, actions.escapeFailures);
        assertEquals(0, actions.bankRequests);
        assertEquals(0, actions.requests);
    }

    @Test
    public void aPendingStopEndsAfterThreeUnobservedTeleports()
    {
        FakeActions actions = new FakeActions();
        actions.stopping = "Combat stalled";
        EmergencyTask task = new EmergencyTask(actions);
        for (int tick : new int[] {0, 8, 16, 24})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.requests);
        assertEquals(1, actions.escapeFailures);
        assertEquals(0, actions.bankRequests);
    }

    private static final class FakeActions extends BarrowsActions
    {
        private String stopping;
        private int escapeFailures;
        private int now;
        private int hp = 20;
        private int maxHp = 99;
        private boolean ring = true;
        private int requests;
        private int bankRequests;
        private boolean dispatch = true;
        private boolean atBarrows = true;
        private boolean open;
        private String failure = "";
        private WorldPoint point = new WorldPoint(3557, 9703, 3);
        private Prayer protection;
        private int prayerOffs;
        private final BarrowsConfig config = new BarrowsConfig()
        {
        };

        @Override
        public BarrowsConfig config()
        {
            return config;
        }

        @Override
        public boolean ready()
        {
            return failure.isEmpty();
        }

        @Override
        public int hp()
        {
            return hp;
        }

        @Override
        public int maxHp()
        {
            return maxHp;
        }

        @Override
        public int tick()
        {
            return now;
        }

        @Override
        public boolean hasBankTravelRing()
        {
            return ring;
        }

        @Override
        public WorldPoint position()
        {
            return point;
        }

        @Override
        public boolean atBarrows()
        {
            return atBarrows;
        }

        @Override
        public boolean prayerActive(Prayer prayer)
        {
            return prayer == protection;
        }

        @Override
        public boolean protect(Prayer prayer, boolean active, Runnable callback)
        {
            if (!active && prayer == protection)
            {
                prayerOffs++;
            }
            return true;
        }

        @Override
        public boolean bankingOpen()
        {
            return open;
        }

        @Override
        public void requestBank()
        {
            bankRequests++;
        }

        @Override
        public void fail(String reason)
        {
            failure = reason;
        }

        @Override
        public String pendingStop()
        {
            return failure.isEmpty() ? stopping : null;
        }

        @Override
        public void failEscape()
        {
            escapeFailures++;
            stopping = null;
        }

        @Override
        public boolean requestEmergencyTravel(Runnable callback)
        {
            if (dispatch)
            {
                requests++;
                callback.run();
            }
            return dispatch;
        }
    }
}
