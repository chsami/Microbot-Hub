package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutoRetaliateTaskTest
{
    @Test
    public void alreadyOffMakesNoRequest()
    {
        FakeActions actions = new FakeActions();
        actions.disabled = true;
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        assertFalse(task.isValid());
        task.execute();
        assertEquals(0, actions.requests);
    }

    @Test
    public void startedHelperDoesNotReleaseActivityBeforeObservedOff()
    {
        FakeActions actions = new FakeActions();
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        task.execute();
        assertEquals(1, actions.requests);
        assertTrue(task.isValid());
        actions.now = 7;
        task.execute();
        assertEquals(1, actions.requests);
        actions.disabled = true;
        assertFalse(task.isValid());
    }

    @Test
    public void failedAttemptsAreSpacedAndBounded()
    {
        FakeActions actions = new FakeActions();
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        for (int tick : new int[] {0, 8, 16, 24})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.requests);
        assertTrue(actions.failure.contains("Auto-retaliate did not turn off"));
    }

    @Test
    public void unavailableControlsHaveDeadlineEvenWithoutDispatch()
    {
        FakeActions actions = new FakeActions();
        actions.dispatch = false;
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        task.execute();
        actions.now = 24;
        task.execute();
        assertTrue(actions.failure.contains("Auto-retaliate did not turn off"));
    }

    @Test
    public void openBankResupplyCanFinishWithoutConsumingControlDeadline()
    {
        FakeActions actions = new FakeActions();
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        task.execute();
        actions.open = true;
        actions.trip = true;
        assertFalse(task.isValid());
        actions.now = 100;
        task.execute();
        assertEquals(0, actions.closes);
        assertEquals("", actions.failure);
        actions.open = false;
        actions.trip = false;
        task.execute();
        assertEquals(2, actions.requests);
        assertEquals("", actions.failure);
    }

    @Test
    public void unrelatedOpenBankClosesBeforeCombatSetting()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        task.execute();
        assertEquals(1, actions.closes);
        assertEquals(0, actions.requests);
        actions.open = false;
        actions.now = 8;
        task.execute();
        assertEquals(1, actions.requests);
    }

    @Test
    public void observationResetsDeadlineEvenWhenAnotherTaskHasPriority()
    {
        FakeActions actions = new FakeActions();
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        task.execute();
        actions.disabled = true;
        actions.now = 5;
        task.observe();
        actions.disabled = false;
        actions.now = 100;
        task.execute();
        assertEquals(2, actions.requests);
        assertEquals("", actions.failure);
    }

    @Test
    public void loadingSuspendsDeadlineAndNoWorkRunsWhenNotReady()
    {
        FakeActions actions = new FakeActions();
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        task.execute();
        task.suspend();
        actions.ready = false;
        actions.now = 100;
        task.execute();
        assertEquals(1, actions.requests);
        actions.ready = true;
        task.execute();
        assertEquals(2, actions.requests);
        assertEquals("", actions.failure);
    }

    @Test
    public void visibleRewardsCloseBeforeCombatTabRequest()
    {
        FakeActions actions = new FakeActions();
        actions.reward = true;
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        task.execute();
        assertEquals(1, actions.rewardCloses);
        assertEquals(0, actions.requests);
        actions.reward = false;
        actions.now = 8;
        task.execute();
        assertEquals(1, actions.requests);
    }

    @Test
    public void selectedBankTeleportDialogueCompletesBeforeCombatTabRequest()
    {
        FakeActions actions = new FakeActions();
        actions.dialogue = true;
        actions.trip = true;
        AutoRetaliateTask task = new AutoRetaliateTask(actions);
        task.execute();
        assertEquals(1, actions.destinations);
        assertEquals(0, actions.requests);
        actions.dialogue = false;
        actions.now = 8;
        task.execute();
        assertEquals(1, actions.requests);
    }

    private static final class FakeActions extends BarrowsActions
    {
        private boolean disabled;
        private boolean open;
        private boolean trip;
        private boolean ready = true;
        private boolean dispatch = true;
        private int now;
        private int requests;
        private int closes;
        private String failure = "";
        private boolean reward;
        private boolean dialogue;
        private int rewardCloses;
        private int destinations;

        @Override
        public boolean ready()
        {
            return ready;
        }

        @Override
        public boolean autoRetaliateDisabled()
        {
            return disabled;
        }

        @Override
        public boolean bankingOpen()
        {
            return open;
        }

        @Override
        public boolean tripPending()
        {
            return trip;
        }

        @Override
        public int tick()
        {
            return now;
        }

        @Override
        public boolean visible(int id)
        {
            return id == (155 << 16) && reward || id == ((219 << 16) | 1) && dialogue;
        }

        @Override
        public boolean bankTravelDialogueVisible()
        {
            return dialogue;
        }

        @Override
        public boolean closeRewards(Runnable callback)
        {
            assertTrue(reward);
            rewardCloses++;
            callback.run();
            return true;
        }

        @Override
        public boolean selectBankTravelDestination(Runnable callback)
        {
            destinations++;
            callback.run();
            return true;
        }

        @Override
        public boolean disableAutoRetaliate(Runnable callback)
        {
            if (dispatch)
            {
                requests++;
                callback.run();
            }
            return dispatch;
        }

        @Override
        public boolean closeBank(Runnable callback)
        {
            closes++;
            callback.run();
            return true;
        }

        @Override
        public void fail(String reason)
        {
            failure = reason;
            ready = false;
        }
    }
}
