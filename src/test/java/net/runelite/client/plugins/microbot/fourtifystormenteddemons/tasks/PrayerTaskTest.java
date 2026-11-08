package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.MutableConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.TestWorld;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrayerTaskTest
{
    private final FakeActions actions = new FakeActions();
    private final MutableConfig config = new MutableConfig();
    private final DemonFight fight = new DemonFight();
    private final TripState trip = new TripState();
    private final AtomicInteger tick = new AtomicInteger(20);
    private final Set<Rs2PrayerEnum> unlocked = new HashSet<>();
    private final PrayerTask task = new PrayerTask(actions, config, fight, trip, tick::get, unlocked::contains);
    private final TestWorld.DemonTarget demon;

    public PrayerTaskTest()
    {
        actions.point = new WorldPoint(4133, 4382, 0);
        trip.setPhase(TripPhase.FIGHTING);
        demon = actions.world.demonAt(new WorldPoint(4140, 4382, 0));
        trip.engage(demon.npc, 20);
        trip.setStyle(CombatStyle.RANGED);
    }

    @Test
    public void protectionFollowsTheObservedStyle()
    {
        fight.observeAttack(CombatStyle.MAGIC);
        assertTrue(task.isValid());
        task.execute();
        assertEquals("prayer:PROTECT_MAGIC:true", actions.requests.get(0));
        assertEquals("Praying " + Rs2PrayerEnum.PROTECT_MAGIC.getName(), task.getStatus());
    }

    @Test
    public void aStyleChangeSwitchesTheProtection()
    {
        fight.observeAttack(CombatStyle.MAGIC);
        actions.prayersOn.add(Rs2PrayerEnum.PROTECT_MAGIC);
        assertFalse(task.isValid());
        fight.observeAttack(CombatStyle.RANGED);
        assertTrue(task.isValid());
        task.execute();
        assertEquals("prayer:PROTECT_RANGE:true", actions.requests.get(0));
    }

    @Test
    public void anUnknownStyleIsPredictedAsMagicAtADistance()
    {
        assertEquals(CombatStyle.MAGIC, task.wantedProtection());
        task.execute();
        assertEquals("prayer:PROTECT_MAGIC:true", actions.requests.get(0));
    }

    @Test
    public void anUnknownStyleIsMeleeWhenWeFightInMelee()
    {
        trip.setStyle(CombatStyle.MELEE);
        assertEquals(CombatStyle.MELEE, task.wantedProtection());
    }

    @Test
    public void anUnknownStyleIsMeleeWhenTheDemonIsAdjacent()
    {
        demon.location = new WorldPoint(4134, 4382, 0);
        assertEquals(CombatStyle.MELEE, task.wantedProtection());
    }

    @Test
    public void thereIsNoWantedProtectionWithoutADemon()
    {
        trip.engage(null, 21);
        assertNull(task.wantedProtection());
    }

    @Test
    public void noRequestIsMadeWhenThePrayerIsAlreadyOn()
    {
        fight.observeAttack(CombatStyle.RANGED);
        actions.prayersOn.add(Rs2PrayerEnum.PROTECT_RANGE);
        assertFalse(task.isValid());
    }

    @Test
    public void onlyTheFightingPhaseIsValid()
    {
        fight.observeAttack(CombatStyle.RANGED);
        trip.setPhase(TripPhase.TRAVELLING);
        assertFalse(task.isValid());
    }

    @Test
    public void nothingHappensWithNoPrayerPoints()
    {
        fight.observeAttack(CombatStyle.RANGED);
        actions.prayer = 0;
        assertFalse(task.isValid());
        task.execute();
        assertTrue(actions.requests.isEmpty());
    }

    @Test
    public void theOffensivePrayerFollowsTheWornStyleOnceProtectionIsOn()
    {
        config.offensive = true;
        unlocked.add(Rs2PrayerEnum.RIGOUR);
        fight.observeAttack(CombatStyle.MAGIC);
        task.execute();
        assertEquals("prayer:PROTECT_MAGIC:true", actions.requests.get(0));
        actions.prayersOn.add(Rs2PrayerEnum.PROTECT_MAGIC);
        tick.set(23);
        assertTrue(task.isValid());
        task.execute();
        assertEquals("prayer:RIGOUR:true", actions.requests.get(1));
    }

    @Test
    public void eachStyleUsesItsOwnOffensivePrayer()
    {
        config.offensive = true;
        unlocked.add(Rs2PrayerEnum.PIETY);
        unlocked.add(Rs2PrayerEnum.RIGOUR);
        unlocked.add(Rs2PrayerEnum.AUGURY);
        assertEquals(Rs2PrayerEnum.RIGOUR, task.offensive());
        trip.setStyle(CombatStyle.MELEE);
        assertEquals(Rs2PrayerEnum.PIETY, task.offensive());
        trip.setStyle(CombatStyle.MAGIC);
        assertEquals(Rs2PrayerEnum.AUGURY, task.offensive());
    }

    @Test
    public void theOffensivePrayerNeedsTheSettingAndTheUnlock()
    {
        unlocked.add(Rs2PrayerEnum.RIGOUR);
        assertNull(task.offensive());
        config.offensive = true;
        assertEquals(Rs2PrayerEnum.RIGOUR, task.offensive());
        unlocked.clear();
        assertNull(task.offensive());
        unlocked.add(Rs2PrayerEnum.RIGOUR);
        trip.setStyle(null);
        assertNull(task.offensive());
    }

    @Test
    public void doesNotResendTheSamePrayerWithinTwoTicks()
    {
        fight.observeAttack(CombatStyle.RANGED);
        task.execute();
        assertEquals(1, actions.requests.size());
        tick.set(21);
        assertFalse(task.isValid());
        tick.set(22);
        assertTrue(task.isValid());
        task.execute();
        assertEquals(2, actions.requests.size());
    }

    @Test
    public void aDifferentPrayerCanBeSentRightAway()
    {
        fight.observeAttack(CombatStyle.RANGED);
        task.execute();
        fight.observeAttack(CombatStyle.MAGIC);
        tick.set(21);
        assertTrue(task.isValid());
        task.execute();
        assertEquals("prayer:PROTECT_MAGIC:true", actions.requests.get(1));
    }

    @Test
    public void prayersStayOnForSevenIdleTicksAndTurnOffOnTheEighth()
    {
        actions.prayersOn.add(Rs2PrayerEnum.PROTECT_RANGE);
        trip.engage(null, 21);
        trip.engage(demon.npc, 21);
        trip.engage(null, 22);
        tick.set(28);
        assertFalse(task.isValid());
        tick.set(29);
        assertTrue(task.isValid());
        task.execute();
        assertEquals("prayer:PROTECT_RANGE:false", actions.requests.get(0));
        assertEquals("Turning off " + Rs2PrayerEnum.PROTECT_RANGE.getName(), task.getStatus());
    }

    @Test
    public void idlePrayersAreTurnedOffOneAtATimeIncludingTheOffensiveOne()
    {
        config.offensive = true;
        unlocked.add(Rs2PrayerEnum.RIGOUR);
        actions.prayersOn.add(Rs2PrayerEnum.PROTECT_MELEE);
        actions.prayersOn.add(Rs2PrayerEnum.RIGOUR);
        trip.engage(null, 21);
        tick.set(40);
        task.execute();
        assertEquals("prayer:PROTECT_MELEE:false", actions.requests.get(0));
        actions.prayersOn.remove(Rs2PrayerEnum.PROTECT_MELEE);
        tick.set(42);
        task.execute();
        assertEquals("prayer:RIGOUR:false", actions.requests.get(1));
        actions.prayersOn.remove(Rs2PrayerEnum.RIGOUR);
        tick.set(44);
        assertFalse(task.isValid());
    }

    @Test
    public void nothingToTurnOffMeansNotValid()
    {
        trip.engage(null, 21);
        tick.set(60);
        assertFalse(task.isValid());
    }
}
