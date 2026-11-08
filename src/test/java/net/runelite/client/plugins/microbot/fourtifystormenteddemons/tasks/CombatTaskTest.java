package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.MutableConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CombatTaskTest
{
    private final FakeActions actions = new FakeActions();
    private final MutableConfig config = new MutableConfig();
    private final DemonFight fight = new DemonFight();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(20);
    private final CombatTask task = new CombatTask(actions.world.client, actions, config, fight, trip, tick::get);

    public CombatTaskTest()
    {
        trip.setPhase(TripPhase.FIGHTING);
        actions.player();
    }

    private TestWorld.DemonTarget demon(int distance)
    {
        TestWorld.DemonTarget demon = actions.world.demon(distance);
        actions.demonList.add(demon.npc);
        return demon;
    }

    @Test
    public void isValidOnlyWhileFightingInsideTheSelectedRoom()
    {
        assertTrue(task.isValid());
        actions.point = new WorldPoint(4200, 4382, 0);
        assertFalse(task.isValid());
        actions.point = new WorldPoint(4133, 4382, 0);
        trip.setPhase(TripPhase.TRAVELLING);
        assertFalse(task.isValid());
    }

    @Test
    public void keepsFightingAnEngagedDemonOutsideTheRoom()
    {
        actions.point = new WorldPoint(4200, 4382, 0);
        assertFalse(task.isValid());
        trip.engage(demon(3).npc, 20);
        assertTrue(task.isValid());
    }

    @Test
    public void attacksTheNearestFreeDemonInTheRoom()
    {
        demon(9);
        TestWorld.DemonTarget near = demon(4);
        assertSame(near.npc, task.choose());
        task.execute();
        assertEquals("attack", actions.requests.get(0));
        assertEquals("Attacking Tormented Demon", task.getStatus());
    }

    @Test
    public void anEngagedDemonIsKeptEvenWhenAnotherIsNearer()
    {
        TestWorld.DemonTarget engaged = demon(9);
        demon(2);
        trip.engage(engaged.npc, 20);
        assertSame(engaged.npc, task.choose());
    }

    @Test
    public void skipsDemonsOtherPlayersAreFightingButTakesOnesFightingUs()
    {
        TestWorld.DemonTarget busy = demon(2);
        busy.interacting = actions.world.demon(30).npc;
        TestWorld.DemonTarget ours = demon(6);
        ours.interacting = actions.world.player;
        assertSame(ours.npc, task.choose());
        ours.interacting = actions.world.demon(31).npc;
        assertNull(task.choose());
    }

    @Test
    public void includesTwelveTilesAndExcludesThirteen()
    {
        TestWorld.DemonTarget far = demon(13);
        assertNull(task.choose());
        far.location = new WorldPoint(4133 + 12, 4382, 0);
        assertSame(far.npc, task.choose());
    }

    @Test
    public void excludesDemonsOutsideTheRoomEvenWhenClose()
    {
        actions.point = new WorldPoint(4150, 4382, 0);
        actions.world.position = actions.point;
        TestWorld.DemonTarget outside = actions.world.demonAt(new WorldPoint(4160, 4382, 0));
        actions.demonList.add(outside.npc);
        assertNull(task.choose());
    }

    @Test
    public void ignoresDemonsInOtherWorldViewsAndOnOtherPlanes()
    {
        TestWorld.DemonTarget foreign = demon(3);
        foreign.topLevel = false;
        TestWorld.DemonTarget elsewhere = actions.world.demonAt(new WorldPoint(4136, 4382, 1));
        actions.demonList.add(elsewhere.npc);
        assertNull(task.choose());
    }

    @Test
    public void skipsADemonThatIsStillSummoning()
    {
        TestWorld.DemonTarget summoning = demon(2);
        summoning.animation = TempleData.SUMMON_ANIMATION;
        TestWorld.DemonTarget ready = demon(6);
        assertSame(ready.npc, task.choose());
        summoning.animation = -1;
        assertSame(summoning.npc, task.choose());
    }

    @Test
    public void doesNotAttackAnEngagedDemonWhileItSummons()
    {
        TestWorld.DemonTarget target = demon(3);
        target.animation = TempleData.SUMMON_ANIMATION;
        trip.engage(target.npc, 20);
        task.execute();
        assertEquals("Waiting for the demon to finish summoning", task.getStatus());
        assertTrue(actions.requests.isEmpty());
        target.animation = -1;
        task.execute();
        assertEquals(List.of("attack"), actions.requests);
    }

    @Test
    public void waitsForADemonWhenNoneIsAvailable()
    {
        task.execute();
        assertEquals("Waiting for a Tormented Demon", task.getStatus());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void doesNotAttackWhileAlreadyFightingTheTarget()
    {
        TestWorld.DemonTarget target = demon(3);
        trip.engage(target.npc, 20);
        actions.world.interacting = target.npc;
        task.execute();
        assertEquals("Fighting Tormented Demon", task.getStatus());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void keepsFightingAfterAGearSwitchWhenAHitLands()
    {
        TestWorld.DemonTarget target = demon(3);
        trip.engage(target.npc, 20);
        actions.world.interacting = target.npc;
        trip.equipmentChanged(20);
        trip.hitLanded(24);
        tick.set(30);
        task.execute();
        assertEquals("Fighting Tormented Demon", task.getStatus());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void attacksAgainWhenNoHitLandsSoonAfterAGearSwitch()
    {
        TestWorld.DemonTarget target = demon(3);
        trip.engage(target.npc, 20);
        actions.world.interacting = target.npc;
        trip.equipmentChanged(20);
        trip.hitLanded(22);
        tick.set(27);
        task.execute();
        assertEquals("Fighting Tormented Demon", task.getStatus(), "Still within the grace after the switch");
        assertTrue(actions.requests.isEmpty());
        tick.set(28);
        task.execute();
        assertEquals("Attacking Tormented Demon", task.getStatus());
        assertEquals(1, actions.requests.size());
        tick.set(31);
        task.execute();
        assertEquals("Fighting Tormented Demon", task.getStatus());
        assertEquals(1, actions.requests.size());
    }

    @Test
    public void waitsForTheDropAfterAKillBeforeTakingOnTheNextDemon()
    {
        TestWorld.DemonTarget next = demon(3);
        trip.killed(tick.get());
        task.execute();
        assertEquals("Waiting for the drop", task.getStatus());
        assertTrue(actions.requests.isEmpty());
        tick.set(tick.get() + 7);
        task.execute();
        assertTrue(actions.requests.isEmpty());
        tick.set(tick.get() + 1);
        task.execute();
        assertEquals("Attacking Tormented Demon", task.getStatus());
        assertEquals(1, actions.requests.size());
    }

    @Test
    public void doesNotAttackWhileFireBombsAreFalling()
    {
        demon(3);
        fight.observeBind(20);
        task.execute();
        assertEquals("Waiting for the fire bombs to land", task.getStatus());
        assertTrue(actions.requests.isEmpty());
        tick.set(25);
        task.execute();
        assertEquals(1, actions.requests.size());
    }

    @Test
    public void waitsThreeTicksBeforeAskingToAttackAgain()
    {
        demon(3);
        task.execute();
        tick.set(22);
        task.execute();
        assertEquals(1, actions.requests.size());
        tick.set(23);
        task.execute();
        assertEquals(2, actions.requests.size());
    }
}
