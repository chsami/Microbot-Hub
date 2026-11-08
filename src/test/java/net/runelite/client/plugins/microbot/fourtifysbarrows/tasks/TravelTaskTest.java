package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.TravelMethod;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TravelTaskTest
{
    @Test
    public void usesOnlyTheSelectedMethodForEveryChoice()
    {
        for (TravelMethod method : TravelMethod.values())
        {
            FakeActions actions = new FakeActions();
            actions.selected = method;
            new TravelTask(actions).execute();
            assertEquals(List.of(method), actions.requests);
            assertEquals(0, actions.walks);
        }
    }

    @Test
    public void queuedRequestIsNotArrivalAndIsNotRepeatedInTheSameTick()
    {
        FakeActions actions = new FakeActions();
        actions.startImmediately = false;
        TravelTask task = new TravelTask(actions);
        task.execute();
        task.execute();
        assertEquals(1, actions.requests.size());
        actions.now = 23;
        task.execute();
        assertEquals(2, actions.requests.size());
        assertEquals(0, actions.walks);
        actions.now = 24;
        task.execute();
        assertTrue(actions.failure.startsWith("Could not use "));
        actions.callbacks.get(0).run();
        assertFalse(task.isValid());
        assertEquals(0, actions.walks);
    }

    @Test
    public void acceptedTabRequestAllowsTheActualTeleportOnTheNextTick()
    {
        FakeActions actions = new FakeActions();
        actions.startImmediately = false;
        TravelTask task = new TravelTask(actions);
        task.execute();
        assertEquals(1, actions.requests.size());
        actions.startImmediately = true;
        actions.now = 1;
        task.execute();
        assertEquals(2, actions.requests.size());
        actions.now = 24;
        actions.callbacks.get(0).run();
        task.execute();
        assertEquals("", actions.failure);
        assertEquals(2, actions.requests.size());
        assertEquals(0, actions.walks);
        actions.now = 91;
        task.execute();
        assertEquals(3, actions.requests.size());
        actions.arrival = actions.selected;
        task.execute();
        assertEquals(1, actions.walks);
    }

    @Test
    public void startingAtArrivalStillRequiresTheSelectedMethodRequirements()
    {
        FakeActions actions = new FakeActions();
        actions.arrival = actions.selected;
        actions.problem = "Carry a Barrows teleport tablet";
        TravelTask task = new TravelTask(actions);
        task.execute();
        assertEquals(actions.problem, actions.failure);
        assertEquals(0, actions.walks);
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void rejectedControlsStopAfterTwentyFourTicks()
    {
        FakeActions actions = new FakeActions();
        actions.accept = false;
        TravelTask task = new TravelTask(actions);
        for (int tick = 0; tick <= 24; tick++)
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(24, actions.requests.size());
        assertTrue(actions.failure.startsWith("Could not use "));
        assertEquals(0, actions.walks);
    }

    @Test
    public void arrivalDeadlineBeginsWhenWorkerStarts()
    {
        FakeActions actions = new FakeActions();
        actions.startImmediately = false;
        TravelTask task = new TravelTask(actions);
        task.execute();
        actions.now = 20;
        actions.callbacks.get(0).run();
        actions.now = 109;
        task.execute();
        assertEquals(1, actions.requests.size());
        actions.now = 110;
        task.execute();
        assertEquals(2, actions.requests.size());
        assertEquals(0, actions.walks);
    }

    @Test
    public void unsuccessfulTeleportsStopAfterThreeNinetyTickAttempts()
    {
        FakeActions actions = new FakeActions();
        TravelTask task = new TravelTask(actions);
        for (int tick : new int[] {0, 89, 90, 179, 180, 269})
        {
            actions.now = tick;
            task.execute();
            assertEquals("", actions.failure);
        }
        assertEquals(3, actions.requests.size());
        actions.now = 270;
        task.execute();
        assertTrue(actions.failure.contains("three times without arriving"));
        assertEquals(3, actions.requests.size());
        assertEquals(0, actions.walks);
    }

    @Test
    public void shadesCooldownWaitsInsteadOfStoppingThenTeleports()
    {
        FakeActions actions = new FakeActions();
        actions.selected = TravelMethod.SHADES_MINIGAME;
        actions.cooldown = 12;
        actions.problem = "Shades of Mort'ton teleport is on cooldown for 12 more minutes";
        TravelTask task = new TravelTask(actions);
        for (int tick = 0; tick <= 100; tick += 25)
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals("", actions.failure);
        assertTrue(actions.requests.isEmpty());
        assertEquals("Shades teleport ready in 12 min", task.getStatus());
        actions.cooldown = 0;
        actions.problem = null;
        task.execute();
        assertEquals(List.of(TravelMethod.SHADES_MINIGAME), actions.requests);
    }

    @Test
    public void missingRequirementStopsWithoutFallbackWalking()
    {
        FakeActions actions = new FakeActions();
        actions.problem = "Carry a Mort'ton teleport scroll";
        TravelTask task = new TravelTask(actions);
        task.execute();
        assertEquals(actions.problem, actions.failure);
        assertTrue(actions.requests.isEmpty());
        assertEquals(0, actions.walks);
    }

    @Test
    public void consumedItemIsNotRequiredWhileWaitingForArrival()
    {
        FakeActions actions = new FakeActions();
        TravelTask task = new TravelTask(actions);
        task.execute();
        actions.problem = "Carry a Barrows teleport tablet";
        actions.now = 1;
        task.execute();
        assertEquals("", actions.failure);
        assertEquals(1, actions.requests.size());
        actions.arrival = actions.selected;
        task.execute();
        assertEquals(1, actions.walks);
        assertEquals(Brother.DHAROK.getMound(), actions.destination);
    }

    @Test
    public void localWalkingContinuesAfterLeavingTeleportArrivalAreaUntilSurface()
    {
        FakeActions actions = new FakeActions();
        TravelTask task = new TravelTask(actions);
        task.execute();
        actions.arrival = actions.selected;
        task.execute();
        actions.arrival = null;
        actions.problem = "The consumed scroll is gone";
        task.execute();
        assertEquals(2, actions.walks);
        assertEquals(1, actions.requests.size());
        actions.surface = true;
        assertFalse(task.isValid());
        task.execute();
        assertEquals(2, actions.walks);
        assertEquals("", actions.failure);
    }

    @Test
    public void localWalkStopsAfterThreeHundredTicksWithoutReachingBarrows()
    {
        FakeActions actions = new FakeActions();
        actions.arrival = actions.selected;
        TravelTask task = new TravelTask(actions);
        task.execute();
        actions.arrival = null;
        actions.now = 299;
        task.execute();
        assertEquals(2, actions.walks);
        assertEquals("", actions.failure);
        actions.now = 300;
        task.execute();
        assertTrue(actions.failure.contains("Did not reach Barrows"));
        assertEquals(2, actions.walks);
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void sceneSuspensionExtendsTheLocalWalkDeadline()
    {
        FakeActions actions = new FakeActions();
        actions.arrival = actions.selected;
        TravelTask task = new TravelTask(actions);
        task.execute();
        actions.arrival = null;
        actions.now = 250;
        task.suspend();
        actions.now = 549;
        task.execute();
        assertEquals("", actions.failure);
        assertEquals(2, actions.walks);
        actions.now = 550;
        task.execute();
        assertTrue(actions.failure.contains("Did not reach Barrows"));
        assertEquals(2, actions.walks);
    }

    @Test
    public void changingSelectionDiscardsOldQueuedCallbackAndArrival()
    {
        FakeActions actions = new FakeActions();
        actions.startImmediately = false;
        TravelTask task = new TravelTask(actions);
        task.execute();
        Runnable oldStart = actions.callbacks.get(0);
        actions.selected = TravelMethod.MORTTON_SCROLL;
        actions.arrival = TravelMethod.BARROWS_TABLET;
        actions.now = 10;
        task.execute();
        oldStart.run();
        actions.now = 34;
        task.execute();
        assertEquals(List.of(TravelMethod.BARROWS_TABLET, TravelMethod.MORTTON_SCROLL), actions.requests);
        assertTrue(actions.failure.startsWith("Could not use "));
        assertEquals(0, actions.walks);
    }

    @Test
    public void changingSelectionDuringLocalWalkRequiresNewSelectedArrival()
    {
        FakeActions actions = new FakeActions();
        actions.arrival = actions.selected;
        TravelTask task = new TravelTask(actions);
        task.execute();
        assertEquals(1, actions.walks);
        actions.selected = TravelMethod.MORYTANIA_LEGS_4;
        task.execute();
        assertEquals(List.of(TravelMethod.MORYTANIA_LEGS_4), actions.requests);
        assertEquals(1, actions.walks);
        actions.arrival = actions.selected;
        task.execute();
        assertEquals(2, actions.walks);
    }

    @Test
    public void cryptAndBankTripsDoNotTriggerOutboundTravel()
    {
        FakeActions actions = new FakeActions();
        TravelTask task = new TravelTask(actions);
        actions.inside = true;
        assertFalse(task.isValid());
        task.execute();
        actions.inside = false;
        actions.bank = true;
        assertFalse(task.isValid());
        task.execute();
        assertTrue(actions.requests.isEmpty());
        assertEquals(0, actions.walks);
    }

    @Test
    public void sceneSuspensionPreservesArrivalAttemptBudget()
    {
        FakeActions actions = new FakeActions();
        TravelTask task = new TravelTask(actions);
        task.execute();
        actions.now = 100;
        task.suspend();
        task.execute();
        assertEquals(1, actions.requests.size());
        actions.now = 189;
        task.execute();
        assertEquals(1, actions.requests.size());
        actions.now = 190;
        task.execute();
        assertEquals(2, actions.requests.size());
        assertEquals("", actions.failure);
    }

    @Test
    public void reachingSurfaceResetsAttemptBudgetForNextTrip()
    {
        FakeActions actions = new FakeActions();
        TravelTask task = new TravelTask(actions);
        task.execute();
        actions.now = 90;
        task.execute();
        actions.surface = true;
        assertFalse(task.isValid());
        actions.surface = false;
        for (int tick : new int[] {91, 181, 271})
        {
            actions.now = tick;
            task.execute();
            assertEquals("", actions.failure);
        }
        assertEquals(5, actions.requests.size());
    }

    @Test
    public void anOpenBankAtTheStartIsClosedThenTheTripContinues()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.problem = "Close the bank or busy interface before travelling to Barrows";
        TravelTask task = new TravelTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        assertEquals("Closing the bank", task.getStatus());
        assertEquals("", actions.failure);
        assertTrue(actions.requests.isEmpty());
        actions.now = 1;
        task.execute();
        assertEquals(1, actions.closes, "Waits for the close before asking again");
        actions.bankOpen = false;
        actions.problem = null;
        actions.now = 2;
        task.execute();
        assertEquals("", actions.failure);
        assertEquals(List.of(TravelMethod.BARROWS_TABLET), actions.requests);
    }

    @Test
    public void aClosedBankWaitsForTheGameToAcknowledgeTheClose()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        actions.closeAcknowledged = false;
        actions.problem = "Close the bank or busy interface before travelling to Barrows";
        TravelTask task = new TravelTask(actions);
        task.execute();
        actions.bankOpen = false;
        actions.now = 1;
        task.execute();
        assertEquals("", actions.failure);
        assertEquals("Closing the bank", task.getStatus());
        assertTrue(actions.requests.isEmpty());
        actions.closeAcknowledged = true;
        actions.problem = null;
        actions.now = 2;
        task.execute();
        assertEquals("", actions.failure);
        assertEquals(List.of(TravelMethod.BARROWS_TABLET), actions.requests);
    }

    @Test
    public void aBankThatStaysOpenStopsAfterThreeCloses()
    {
        FakeActions actions = new FakeActions();
        actions.bankOpen = true;
        TravelTask task = new TravelTask(actions);
        for (int tick = 0; tick <= 24; tick += 8)
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.closes);
        assertEquals("The bank did not close; close it, then enable the plugin again", actions.failure);
    }

    private static final class FakeActions extends BarrowsActions
    {
        private final List<TravelMethod> requests = new ArrayList<>();
        private final List<Runnable> callbacks = new ArrayList<>();
        private final BarrowsConfig config = new BarrowsConfig()
        {
            @Override
            public TravelMethod travelMethod()
            {
                return selected;
            }
        };
        private TravelMethod selected = TravelMethod.BARROWS_TABLET;
        private TravelMethod arrival;
        private boolean inside;
        private boolean bank;
        private boolean surface;
        private boolean accept = true;
        private boolean startImmediately = true;
        private int now;
        private int walks;
        private String problem;
        private long cooldown;
        private String failure = "";
        private WorldPoint destination;
        private boolean bankOpen;
        private boolean closeAcknowledged = true;
        private int closes;

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
        public boolean inside()
        {
            return inside;
        }

        @Override
        public boolean bankPending()
        {
            return bank;
        }

        @Override
        public boolean atBarrowsSurface()
        {
            return surface;
        }

        @Override
        public boolean atTravelArrival(TravelMethod method)
        {
            return arrival == method;
        }

        @Override
        public String travelProblem(TravelMethod method)
        {
            return problem;
        }

        @Override
        public long shadesCooldown()
        {
            return cooldown;
        }

        @Override
        public boolean requestTravel(TravelMethod method, Runnable callback)
        {
            requests.add(method);
            callbacks.add(callback);
            if (accept && startImmediately)
            {
                callback.run();
            }
            return accept;
        }

        @Override
        public boolean walk(WorldPoint point, String label)
        {
            walks++;
            destination = point;
            return false;
        }

        @Override
        public int tick()
        {
            return now;
        }

        @Override
        public void status(String text)
        {
        }

        @Override
        public void fail(String reason)
        {
            failure = reason;
        }

        @Override
        public boolean bankingOpen()
        {
            return bankOpen;
        }

        @Override
        public boolean bankCloseObserved()
        {
            return !bankOpen && closeAcknowledged;
        }

        @Override
        public boolean closeBank(Runnable callback)
        {
            closes++;
            callback.run();
            return true;
        }
    }
}
