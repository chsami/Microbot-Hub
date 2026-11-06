package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GearTaskTest
{
    private final FakeActions actions = new FakeActions();
    private final DemonFight fight = new DemonFight();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(20);
    private final Map<CombatStyle, Set<Integer>> sets = new EnumMap<>(CombatStyle.class);
    private final GearTask task = new GearTask(actions, fight, trip, tick::get, sets);
    private final TestWorld.DemonTarget demon = actions.world.demon(5);

    public GearTaskTest()
    {
        sets.put(CombatStyle.MAGIC, Set.of(100, 101));
        sets.put(CombatStyle.RANGED, Set.of(200));
        sets.put(CombatStyle.MELEE, Set.of(300));
        trip.setPhase(TripPhase.FIGHTING);
        trip.setStyle(CombatStyle.MAGIC);
        trip.engage(demon.npc, 20);
        actions.worn.add(100);
        actions.worn.add(101);
    }

    @Test
    public void staysInTheCurrentStyleWhileTheDemonDoesNotProtectAgainstIt()
    {
        demon.overhead = new short[] {-1, (short) CombatStyle.RANGED.getOverheadSprite()};
        task.observe();
        assertEquals(CombatStyle.MAGIC, trip.getStyle());
        assertFalse(task.isValid());
    }

    @Test
    public void switchesWhenTheOverheadProtectsTheWornStyle()
    {
        actions.have(200, 1);
        demon.overhead = new short[] {-1, (short) CombatStyle.MAGIC.getOverheadSprite()};
        task.observe();
        assertEquals(CombatStyle.RANGED, trip.getStyle());
        assertEquals(200, task.missing());
        assertTrue(task.isValid());
        task.execute();
        assertEquals("inventory:200:Wield", actions.requests.get(0));
        assertEquals("Switching to Ranged gear", task.getStatus());
        assertFalse(trip.isAttackNeeded(tick.get() + 7), "a hit may still land");
        assertTrue(trip.isAttackNeeded(tick.get() + 8), "no hit landed after the switch");
    }

    @Test
    public void wearsWhenTheItemHasNoWieldAction()
    {
        actions.have(200, 1);
        actions.refused.add("inventory:200:Wield");
        demon.overhead = new short[] {(short) CombatStyle.MAGIC.getOverheadSprite()};
        task.observe();
        task.execute();
        assertEquals("inventory:200:Wear", actions.requests.get(0));
    }

    @Test
    public void switchesOneItemPerRequestAndFindsTheFirstUnwornItem()
    {
        sets.put(CombatStyle.RANGED, Set.of(200, 201));
        actions.have(200, 1);
        actions.have(201, 1);
        actions.worn.add(200);
        trip.setStyle(CombatStyle.RANGED);
        assertEquals(201, task.missing());
        actions.worn.add(201);
        assertEquals(-1, task.missing());
        assertFalse(task.isValid());
    }

    @Test
    public void failsWhenTheMissingItemIsNotCarried()
    {
        demon.overhead = new short[] {(short) CombatStyle.MAGIC.getOverheadSprite()};
        task.observe();
        assertTrue(task.isValid());
        actions.names.put(200, "Ancient staff");
        task.execute();
        assertEquals("Missing Ancient staff (200): carry it or remove it from your equipment IDs, then restart the plugin",
            actions.getFailure());
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void withoutATargetTheStyleDoesNotChange()
    {
        trip.engage(null, 21);
        task.observe();
        assertEquals(CombatStyle.MAGIC, trip.getStyle());
    }

    @Test
    public void nothingIsMissingWithoutAConfiguredStyle()
    {
        trip.setStyle(null);
        assertEquals(-1, task.missing());
        sets.clear();
        trip.setStyle(CombatStyle.MAGIC);
        assertEquals(-1, task.missing());
        task.observe();
        assertEquals(CombatStyle.MAGIC, trip.getStyle());
        assertNull(sets.get(CombatStyle.RANGED));
    }

    @Test
    public void waitsOutTheFireBombWindowAndTheObservationTicks()
    {
        actions.have(200, 1);
        trip.setStyle(CombatStyle.RANGED);
        fight.observeBind(20);
        tick.set(24);
        assertFalse(task.isValid());
        tick.set(25);
        assertTrue(task.isValid());
        task.execute();
        tick.set(26);
        assertFalse(task.isValid());
        tick.set(27);
        assertTrue(task.isValid());
    }

    @Test
    public void onlyTheFightingPhaseSwitchesGear()
    {
        trip.setStyle(CombatStyle.RANGED);
        actions.have(200, 1);
        assertTrue(task.isValid());
        trip.setPhase(TripPhase.BANKING);
        assertFalse(task.isValid());
    }
}
