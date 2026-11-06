package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DodgeTaskTest
{
    private static final WorldPoint HERE = new WorldPoint(4133, 4382, 0);
    private final FakeActions actions = new FakeActions();
    private final DemonFight fight = new DemonFight();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(10);
    private final Map<WorldPoint, Integer> reachable = new HashMap<>();
    private final AtomicReference<Object[]> asked = new AtomicReference<>();
    private WorldPoint center;
    private final DodgeTask task = new DodgeTask(actions, fight, trip, tick::get, (position, steps) ->
    {
        asked.set(new Object[] {position, steps});
        return reachable;
    }, () -> center);

    public DodgeTaskTest()
    {
        actions.point = HERE;
        fight.observeBomb(HERE, 10);
    }

    @Test
    public void isValidOnlyOnABombTileInsideTheStepWindow()
    {
        tick.set(10);
        assertFalse(task.isValid());
        tick.set(11);
        assertTrue(task.isValid());
        tick.set(13);
        assertTrue(task.isValid());
        tick.set(14);
        assertFalse(task.isValid());
    }

    @Test
    public void isNotValidOffTheBombTilesOrWithoutAPosition()
    {
        tick.set(11);
        actions.point = new WorldPoint(4134, 4382, 0);
        assertFalse(task.isValid());
        actions.point = null;
        assertFalse(task.isValid());
    }

    @Test
    public void stepsTowardTheRoomCentreWhenTilesAreOtherwiseEqual()
    {
        tick.set(11);
        center = new WorldPoint(4140, 4382, 0);
        reachable.put(new WorldPoint(4132, 4382, 0), 1);
        reachable.put(new WorldPoint(4134, 4382, 0), 1);
        task.execute();
        assertEquals("step:4134,4382", actions.requests.get(0));
    }

    @Test
    public void stepsToASafeTileAndAsksForTilesTwoStepsAway()
    {
        tick.set(11);
        reachable.put(new WorldPoint(4132, 4382, 0), 1);
        reachable.put(HERE, 0);
        task.execute();
        assertEquals("step:4132,4382", actions.requests.get(0));
        assertEquals(HERE, asked.get()[0]);
        assertEquals(2, asked.get()[1]);
        assertEquals("Dodging fire bomb", task.getStatus());
    }

    @Test
    public void neverStepsOntoAnotherBombTile()
    {
        tick.set(11);
        WorldPoint bombed = new WorldPoint(4132, 4382, 0);
        fight.observeBomb(bombed, 10);
        reachable.put(bombed, 1);
        reachable.put(new WorldPoint(4134, 4382, 0), 1);
        task.execute();
        assertEquals("step:4134,4382", actions.requests.get(0));
    }

    @Test
    public void prefersATileNearTheDemonWhenFightingInRangedOrMagic()
    {
        tick.set(11);
        TestWorld.DemonTarget demon = new TestWorld().demonAt(new WorldPoint(4140, 4381, 0));
        trip.engage(demon.npc, 10);
        trip.setStyle(CombatStyle.RANGED);
        reachable.put(new WorldPoint(4132, 4382, 0), 1);
        reachable.put(new WorldPoint(4134, 4382, 0), 1);
        task.execute();
        assertEquals("step:4134,4382", actions.requests.get(0));
    }

    @Test
    public void inMeleeItStaysAdjacentToTheDemon()
    {
        tick.set(11);
        TestWorld.DemonTarget demon = new TestWorld().demonAt(new WorldPoint(4135, 4381, 0));
        trip.engage(demon.npc, 10);
        trip.setStyle(CombatStyle.MELEE);
        reachable.put(new WorldPoint(4132, 4382, 0), 1);
        reachable.put(new WorldPoint(4134, 4383, 0), 1);
        task.execute();
        assertEquals("step:4134,4383", actions.requests.get(0));
    }

    @Test
    public void reportsWhenNoTileIsSafe()
    {
        tick.set(11);
        task.execute();
        assertTrue(actions.requests.isEmpty());
        assertEquals("No safe tile from the fire bomb", task.getStatus());
    }

    @Test
    public void doesNotResendOnTheTickItAlreadySentAStep()
    {
        tick.set(11);
        reachable.put(new WorldPoint(4132, 4382, 0), 1);
        assertTrue(task.isValid());
        task.execute();
        assertFalse(task.isValid());
        tick.set(12);
        assertTrue(task.isValid());
    }

    @Test
    public void aRefusedStepCanBeRetriedOnTheSameTick()
    {
        tick.set(11);
        reachable.put(new WorldPoint(4132, 4382, 0), 1);
        actions.refused.add("step");
        task.execute();
        assertTrue(task.isValid());
    }
}
