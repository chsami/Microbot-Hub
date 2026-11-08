package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.MutableConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.LootRule;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.scene.GroundDrop;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LootTaskTest
{
    private static final WorldPoint IN_ROOM = new WorldPoint(4135, 4382, 0);
    private final FakeActions actions = new FakeActions();
    private final MutableConfig config = new MutableConfig();
    private final DemonFight fight = new DemonFight();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(20);
    private final LootTask task = new LootTask(actions, config, fight, trip, tick::get, new LootRule("Tormented synapse", 5000));

    public LootTaskTest()
    {
        trip.setPhase(TripPhase.FIGHTING);
    }

    private GroundDrop drop(WorldPoint where, int id, int quantity, String name, long price)
    {
        TestWorld.DropTile tile = actions.world.drop(where, id, quantity, TileItem.OWNERSHIP_SELF);
        actions.names.put(id, name);
        actions.prices.put(id, price);
        GroundDrop drop = new GroundDrop(tile.tile, tile.item);
        actions.dropList.add(drop);
        return drop;
    }

    @Test
    public void takesAValuableDropAndReportsIt()
    {
        drop(IN_ROOM, 1000, 1, "Rune bar", 12_000);
        assertTrue(task.isValid());
        task.execute();
        assertEquals("pickup:1000", actions.requests.get(0));
        assertEquals("Looting Rune bar", task.getStatus());
    }

    @Test
    public void takesTheFirstQualifyingDropInTheOrderGiven()
    {
        drop(IN_ROOM, 1000, 1, "Bones", 10);
        GroundDrop wanted = drop(IN_ROOM, 1001, 1, "Rune bar", 12_000);
        drop(IN_ROOM, 1002, 1, "Rune bar", 99_000);
        assertSame(wanted, task.next());
    }

    @Test
    public void leavesCheapDropsAndUnpricedDropsUnlessListed()
    {
        drop(IN_ROOM, 1000, 1, "Bones", 10);
        drop(IN_ROOM, 1001, 1, "Mystery", 0);
        assertFalse(task.isValid());
        GroundDrop listed = drop(IN_ROOM, 29580, 1, "Tormented synapse", 0);
        assertSame(listed, task.next());
    }

    @Test
    public void ignoresDropsOutsideTheSelectedRoom()
    {
        drop(new WorldPoint(4200, 4382, 0), 1000, 1, "Rune bar", 12_000);
        drop(new WorldPoint(4135, 4382, 1), 1001, 1, "Rune bar", 12_000);
        assertFalse(task.isValid());
    }

    @Test
    public void aFullInventoryOnlyTakesAStackItAlreadyCarries()
    {
        for (int id = 1; id <= 28; id++)
        {
            actions.have(id, 1);
        }
        drop(IN_ROOM, 1000, 1, "Rune bar", 12_000);
        assertFalse(task.isValid());
        actions.stackables.add(1000);
        assertFalse(task.isValid());
        actions.inventory.put(1000, 5);
        actions.inventory.remove(28);
        assertTrue(task.isValid());
        actions.inventory.put(28, 1);
        assertTrue(task.isValid());
        assertEquals(0, actions.freeSlots());
    }

    @Test
    public void lootsWhileTheNextDemonAttacksButNotWhileFireBombsAreFalling()
    {
        drop(IN_ROOM, 1000, 1, "Rune bar", 12_000);
        trip.engage(actions.world.demon(3).npc, 20);
        assertTrue(task.isValid());
        trip.engage(null, 21);
        assertTrue(task.isValid());
        fight.observeBind(20);
        assertFalse(task.isValid());
        tick.set(25);
        assertTrue(task.isValid());
    }

    @Test
    public void onlyLootsWhileFighting()
    {
        drop(IN_ROOM, 1000, 1, "Rune bar", 12_000);
        trip.setPhase(TripPhase.LEAVING);
        assertFalse(task.isValid());
    }

    @Test
    public void doesNotAskAgainWhileWalkingToTheDrop()
    {
        actions.point = new WorldPoint(4130, 4382, 0);
        drop(IN_ROOM, 1000, 1, "Rune bar", 12_000);
        assertTrue(task.isValid());
        task.execute();
        for (int step = 1; step <= 4; step++)
        {
            tick.set(20 + step);
            actions.point = new WorldPoint(4130 + step, 4382, 0);
            assertFalse(task.isValid(), "Walking, tick " + step);
        }
        tick.set(25);
        assertFalse(task.isValid(), "Arrived this tick");
        tick.set(26);
        assertTrue(task.isValid(), "Still there and the drop is still on the ground");
    }

    @Test
    public void waitsThreeTicksAfterAPickupRequest()
    {
        drop(IN_ROOM, 1000, 1, "Rune bar", 12_000);
        task.execute();
        tick.set(22);
        assertFalse(task.isValid());
        tick.set(23);
        assertTrue(task.isValid());
    }

    @Test
    public void doesNothingWhenThereIsNoDrop()
    {
        task.execute();
        assertTrue(actions.requests.isEmpty());
    }
}
