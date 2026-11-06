package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.MutableConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SuppliesTaskTest
{
    private final FakeActions actions = new FakeActions();
    private final MutableConfig config = new MutableConfig();
    private final DemonFight fight = new DemonFight();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(20);
    private final SuppliesTask task = new SuppliesTask(actions, config, fight, trip, tick::get);

    public SuppliesTaskTest()
    {
        trip.setPhase(TripPhase.FIGHTING);
        actions.maxHp = 100;
        actions.hp = 100;
        actions.maxPrayer = 100;
        actions.prayer = 100;
        actions.have(385, 5);
        actions.have(2434, 1);
    }

    @Test
    public void eatsAtOrBelowThePercentageOfMaximumHitpoints()
    {
        actions.hp = 56;
        assertFalse(task.needsFood());
        assertFalse(task.isValid());
        actions.hp = 55;
        assertTrue(task.needsFood());
        assertTrue(task.isValid());
        task.execute();
        assertEquals("inventory:385:Eat", actions.requests.get(0));
        assertEquals("Eating", task.getStatus());
    }

    @Test
    public void fullHitpointsNeverNeedFoodEvenAtOneHundredPercent()
    {
        config.eat = 100;
        assertFalse(task.needsFood());
        actions.hp = 99;
        assertTrue(task.needsFood());
    }

    @Test
    public void drinksAtOrBelowThePercentageOfMaximumPrayer()
    {
        actions.prayer = 21;
        assertFalse(task.needsPrayer());
        actions.prayer = 20;
        assertTrue(task.needsPrayer());
        assertTrue(task.isValid());
        task.execute();
        assertEquals("inventory:2434:Drink", actions.requests.get(0));
        assertEquals("Drinking a prayer restore", task.getStatus());
    }

    @Test
    public void drinksTheLowestDoseFirst()
    {
        actions.inventory.clear();
        actions.have(2434, 1);
        assertEquals(2434, task.restore());
        actions.have(139, 1);
        assertEquals(139, task.restore());
        actions.have(141, 1);
        assertEquals(141, task.restore());
        actions.have(143, 1);
        assertEquals(143, task.restore());
        actions.inventory.clear();
        actions.have(3024, 1);
        actions.have(3030, 1);
        assertEquals(3030, task.restore());
        actions.inventory.clear();
        assertEquals(-1, task.restore());
    }

    @Test
    public void foodComesBeforePrayerWhenBothAreLow()
    {
        actions.hp = 10;
        actions.prayer = 5;
        task.execute();
        assertEquals("inventory:385:Eat", actions.requests.get(0));
    }

    @Test
    public void withoutFoodItStillDrinks()
    {
        actions.hp = 10;
        actions.prayer = 5;
        actions.inventory.remove(385);
        assertTrue(task.isValid());
        task.execute();
        assertEquals("inventory:2434:Drink", actions.requests.get(0));
    }

    @Test
    public void isNotValidWhenLowButNothingIsCarried()
    {
        actions.hp = 10;
        actions.prayer = 5;
        actions.inventory.clear();
        assertFalse(task.isValid());
    }

    @Test
    public void waitsOutTheFireBombWindow()
    {
        actions.hp = 10;
        fight.observeBind(20);
        for (int now = 20; now <= 24; now++)
        {
            tick.set(now);
            assertFalse(task.isValid(), "tick " + now);
        }
        tick.set(25);
        assertTrue(task.isValid());
    }

    @Test
    public void isNotValidWhileBanking()
    {
        actions.hp = 10;
        trip.setPhase(TripPhase.BANKING);
        assertFalse(task.isValid());
    }

    @Test
    public void waitsThreeTicksAfterASentRequest()
    {
        actions.hp = 10;
        task.execute();
        tick.set(22);
        assertFalse(task.isValid());
        tick.set(23);
        assertTrue(task.isValid());
    }

    @Test
    public void aRefusedRequestIsNotRememberedAsSent()
    {
        actions.hp = 10;
        actions.refused.add("inventory");
        task.execute();
        assertTrue(actions.requests.isEmpty());
        assertTrue(task.isValid());
    }
}
