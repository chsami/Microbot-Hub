package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import java.lang.reflect.Proxy;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SuppliesTaskTest
{
    @Test
    public void safeFeroxReturnDefersFoodAndPrayerToThePool()
    {
        for (WorldPoint position : new WorldPoint[] {new WorldPoint(3151, 3636, 0),
            new WorldPoint(3130, 3634, 0), new WorldPoint(3130, 3632, 0)})
        {
            FakeActions actions = feroxReturn(position);
            actions.prayer = 1;
            actions.potions = 4;
            SuppliesTask task = new SuppliesTask(actions);
            for (int tick : new int[] {0, 8, 24, 90})
            {
                actions.now = tick;
                assertFalse(task.isValid());
                task.execute();
            }
            assertEquals(0, actions.requests);
            assertEquals(10, actions.food);
            assertEquals(4, actions.potions);
        }
    }

    @Test
    public void feroxSelectionStillHealsInsideCryptsAndOutsideConfirmedSafeGround()
    {
        for (WorldPoint position : new WorldPoint[] {new WorldPoint(3565, 3290, 0),
            new WorldPoint(3159, 3636, 0), new WorldPoint(3120, 3632, 0),
            new WorldPoint(3130, 3627, 0), new WorldPoint(3130, 3640, 0), new WorldPoint(3130, 3634, 1)})
        {
            FakeActions actions = feroxReturn(position);
            SuppliesTask task = new SuppliesTask(actions);
            assertTrue(task.isValid());
            task.execute();
            assertEquals(1, actions.requests);
        }
        FakeActions inside = feroxReturn(new WorldPoint(3130, 3634, 0));
        inside.inside = true;
        new SuppliesTask(inside).execute();
        assertEquals(1, inside.requests);
    }

    @Test
    public void safeLocationAloneDoesNotSuppressNeededSupplies()
    {
        FakeActions actions = feroxReturn(new WorldPoint(3130, 3634, 0));
        actions.trip = false;
        new SuppliesTask(actions).execute();
        assertEquals(1, actions.requests);
    }

    @Test
    public void pendingFoodConsumptionIsObservedBeforeFeroxDeferral()
    {
        FakeActions actions = feroxReturn(new WorldPoint(3565, 3290, 0));
        SuppliesTask task = new SuppliesTask(actions);
        task.execute();
        actions.position = new WorldPoint(3151, 3636, 0);
        assertTrue(task.isValid());
        task.execute();
        assertEquals(1, actions.requests);
        actions.food--;
        task.execute();
        assertFalse(task.isValid());
        task.execute();
        assertEquals(1, actions.requests);
    }

    @Test
    public void unobservedSupplyAttemptAtFeroxExpiresWithoutRetryingOrBlockingPool()
    {
        FakeActions actions = feroxReturn(new WorldPoint(3565, 3290, 0));
        SuppliesTask task = new SuppliesTask(actions);
        task.execute();
        actions.position = new WorldPoint(3130, 3634, 0);
        actions.now = 8;
        task.execute();
        assertFalse(task.isValid());
        assertEquals(1, actions.requests);
        assertTrue(actions.trip);
    }

    @Test
    public void prayerRecoveryStillRunsOutsideFerox()
    {
        FakeActions actions = feroxReturn(new WorldPoint(3565, 3290, 0));
        actions.hp = 99;
        actions.prayer = 1;
        actions.potions = 4;
        new SuppliesTask(actions).execute();
        assertEquals(1, actions.requests);
        assertEquals(2434, actions.requestedItem);
    }

    private static FakeActions feroxReturn(WorldPoint position)
    {
        FakeActions actions = new FakeActions();
        actions.trip = true;
        actions.inside = false;
        actions.position = position;
        return actions;
    }

    @Test
    public void consumesFoodDuringBankTravelAndWaitsForObservedConsumption()
    {
        FakeActions actions = new FakeActions();
        actions.trip = true;
        actions.inside = false;
        SuppliesTask task = new SuppliesTask(actions);
        assertTrue(task.isValid());
        task.execute();
        task.execute();
        assertEquals(1, actions.requests);
        actions.food--;
        task.execute();
        task.execute();
        assertEquals(2, actions.requests);
    }

    @Test
    public void exhaustedFoodRequestsBankOnceAndDoesNotStarveEscape()
    {
        FakeActions actions = new FakeActions();
        actions.food = 0;
        SuppliesTask task = new SuppliesTask(actions);
        task.execute();
        assertTrue(actions.trip);
        assertFalse(task.isValid());
        assertEquals(0, actions.requests);
    }

    @Test
    public void doesNotConsumeAtFullStatsBelowConfiguredThreshold()
    {
        FakeActions actions = new FakeActions();
        actions.maxHp = actions.hp;
        actions.maxPrayer = actions.prayer;
        SuppliesTask task = new SuppliesTask(actions);
        assertFalse(task.isValid());
    }

    @Test
    public void unavailableControlsReleaseEscapeAfterBoundedWait()
    {
        FakeActions actions = new FakeActions();
        actions.dispatch = false;
        SuppliesTask task = new SuppliesTask(actions);
        task.execute();
        actions.now = 24;
        task.execute();
        assertTrue(actions.trip);
        assertFalse(task.isValid());
    }

    @Test
    public void failedConsumptionRetriesThreeTimesThenReleasesEscape()
    {
        FakeActions actions = new FakeActions();
        SuppliesTask task = new SuppliesTask(actions);
        for (int tick : new int[] {0, 8, 16, 24})
        {
            actions.now = tick;
            task.execute();
        }
        assertEquals(3, actions.requests);
        assertTrue(actions.trip);
        assertFalse(task.isValid());
    }

    @Test
    public void bankInterfaceKeepsSupplyInteractionsOutOfBanking()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        SuppliesTask task = new SuppliesTask(actions);
        assertFalse(task.isValid());
        task.execute();
        assertEquals(0, actions.requests);
    }

    @Test
    public void eatAndDrinkThresholdsArePercentagesOfMaximumPoints()
    {
        FakeActions actions = new FakeActions();
        actions.maxHp = 60;
        actions.hp = 34;
        assertFalse(new SuppliesTask(actions).isValid(), "34 of 60 is above 55%");
        actions.hp = 33;
        assertTrue(new SuppliesTask(actions).isValid(), "33 of 60 is 55%");

        actions.hp = 60;
        actions.potions = 4;
        actions.maxPrayer = 70;
        actions.prayer = 15;
        assertFalse(new SuppliesTask(actions).isValid(), "15 of 70 is above 20%");
        actions.prayer = 14;
        assertTrue(new SuppliesTask(actions).isValid(), "14 of 70 is 20%");
    }

    private static final class FakeActions extends BarrowsActions
    {
        private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, arguments) -> null);
        private final BarrowsConfig config = new BarrowsConfig() {};
        private WorldPoint position = new WorldPoint(3565, 3290, 0);
        private int now;
        private int hp = 30;
        private int prayer = 50;
        private int maxHp = 99;
        private int maxPrayer = 99;
        private int food = 10;
        private int potions;
        private int requests;
        private int requestedItem;
        private boolean trip;
        private boolean inside = true;
        private boolean open;
        private boolean dispatch = true;

        @Override
        public Player player()
        {
            return player;
        }
        @Override
        public BarrowsConfig config()
        {
            return config;
        }
        @Override
        public boolean ready()
        {
            return true;
        }
        @Override
        public boolean inside()
        {
            return inside;
        }
        @Override
        public WorldPoint position()
        {
            return position;
        }
        @Override
        public boolean bankPending()
        {
            return trip;
        }
        @Override
        public boolean bankingOpen()
        {
            return open;
        }
        @Override
        public void requestBank()
        {
            trip = true;
        }
        @Override
        public int tick()
        {
            return now;
        }
        @Override
        public int hp()
        {
            return hp;
        }
        @Override
        public int prayerPoints()
        {
            return prayer;
        }
        @Override
        public int maxHp()
        {
            return maxHp;
        }
        @Override
        public int maxPrayer()
        {
            return maxPrayer;
        }
        @Override
        public int foodCount()
        {
            return food;
        }
        @Override
        public int count(int id)
        {
            return id == 385 ? food : id == 2434 ? potions : 0;
        }
        @Override
        public int prayerRestoreId()
        {
            return potions > 0 ? 2434 : -1;
        }
        @Override
        public boolean inventory(int id, String option, Runnable callback)
        {
            if (dispatch)
            {
                requests++;
                requestedItem = id;
                callback.run();
            }
            return dispatch;
        }
    }
}
