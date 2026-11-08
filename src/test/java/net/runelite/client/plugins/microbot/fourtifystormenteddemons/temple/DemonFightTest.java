package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DemonFightTest
{
    private static final WorldPoint BOMB = new WorldPoint(4133, 4382, 0);
    private static final WorldPoint OTHER = new WorldPoint(4134, 4382, 0);

    @Test
    public void bindStartsAWindowAndForgetsTheStyleAndOldBombs()
    {
        DemonFight fight = new DemonFight();
        fight.observeAttack(CombatStyle.RANGED);
        fight.observeBomb(BOMB, 10);
        assertEquals(10, fight.getBindTick());
        assertNull(fight.getStyle());
        fight.observeAttack(CombatStyle.MAGIC);
        fight.observeBind(20);
        assertEquals(20, fight.getBindTick());
        assertNull(fight.getStyle());
        assertTrue(fight.getBombs().isEmpty());
    }

    @Test
    public void aSecondBindInsideTheWindowIsTheSameBind()
    {
        DemonFight fight = new DemonFight();
        fight.observeBind(10);
        fight.observeBomb(BOMB, 12);
        fight.observeBomb(OTHER, 14);
        assertEquals(10, fight.getBindTick());
        assertEquals(2, fight.getBombs().size());
        fight.observeBind(15);
        assertEquals(15, fight.getBindTick());
        assertTrue(fight.getBombs().isEmpty());
    }

    @Test
    public void nullBombTargetsAreIgnored()
    {
        DemonFight fight = new DemonFight();
        fight.observeBomb(null, 10);
        assertEquals(Integer.MIN_VALUE / 2, fight.getBindTick());
        assertTrue(fight.getBombs().isEmpty());
    }

    @Test
    public void bombsExpireFourTicksAfterTheBind()
    {
        DemonFight fight = new DemonFight();
        fight.observeBomb(BOMB, 10);
        fight.expire(14);
        assertTrue(fight.onBomb(BOMB, 14));
        assertEquals(1, fight.getBombs().size());
        fight.expire(15);
        assertFalse(fight.onBomb(BOMB, 15));
        assertTrue(fight.getBombs().isEmpty());
    }

    @Test
    public void onlyARecordedBombTileCountsInsideTheWindow()
    {
        DemonFight fight = new DemonFight();
        fight.observeBomb(BOMB, 10);
        assertTrue(fight.onBomb(BOMB, 10));
        assertFalse(fight.onBomb(OTHER, 10));
        assertFalse(fight.onBomb(BOMB, 15));
        assertFalse(new DemonFight().onBomb(BOMB, 0));
    }

    @Test
    public void stepIsDueOnTicksOneToThreeAfterTheBind()
    {
        DemonFight fight = new DemonFight();
        assertFalse(fight.stepDue(0));
        fight.observeBind(10);
        assertFalse(fight.stepDue(9));
        assertFalse(fight.stepDue(10));
        assertTrue(fight.stepDue(11));
        assertTrue(fight.stepDue(12));
        assertTrue(fight.stepDue(13));
        assertFalse(fight.stepDue(14));
    }

    @Test
    public void lockoutCoversTheBindTickThroughTheTickTheBombsLand()
    {
        DemonFight fight = new DemonFight();
        assertFalse(fight.lockedOut(0));
        fight.observeBind(10);
        assertFalse(fight.lockedOut(9));
        for (int tick = 10; tick <= 14; tick++)
        {
            assertTrue(fight.lockedOut(tick), "tick " + tick);
        }
        assertFalse(fight.lockedOut(15));
    }

    @Test
    public void observedAttackSetsTheStyleAndUnknownAnimationsKeepIt()
    {
        DemonFight fight = new DemonFight();
        fight.observeAttack(CombatStyle.MAGIC);
        assertEquals(CombatStyle.MAGIC, fight.getStyle());
        fight.observeAttack(null);
        assertEquals(CombatStyle.MAGIC, fight.getStyle());
        fight.observeAttack(CombatStyle.MELEE);
        assertEquals(CombatStyle.MELEE, fight.getStyle());
    }

    @Test
    public void endingTheFightForgetsOnlyTheStyle()
    {
        DemonFight fight = new DemonFight();
        fight.observeBomb(BOMB, 10);
        fight.observeAttack(CombatStyle.RANGED);
        fight.endFight();
        assertNull(fight.getStyle());
        assertEquals(10, fight.getBindTick());
        assertEquals(1, fight.getBombs().size());
    }

    @Test
    public void resetForgetsEverything()
    {
        DemonFight fight = new DemonFight();
        fight.observeBomb(BOMB, 10);
        fight.observeAttack(CombatStyle.RANGED);
        fight.reset();
        assertNull(fight.getStyle());
        assertTrue(fight.getBombs().isEmpty());
        assertFalse(fight.lockedOut(10));
        assertFalse(fight.stepDue(11));
    }

    @Test
    public void aKnownStyleIsProtectedDirectly()
    {
        DemonFight fight = new DemonFight();
        fight.observeAttack(CombatStyle.RANGED);
        assertEquals(CombatStyle.RANGED, fight.protectFrom(CombatStyle.MELEE, true));
        assertEquals(CombatStyle.RANGED, fight.protectFrom(CombatStyle.MAGIC, false));
    }

    @Test
    public void anUnknownStylePredictsMeleeWhenWeFightInMeleeOrTheDemonIsAdjacent()
    {
        DemonFight fight = new DemonFight();
        assertEquals(CombatStyle.MELEE, fight.protectFrom(CombatStyle.MELEE, false));
        assertEquals(CombatStyle.MELEE, fight.protectFrom(CombatStyle.RANGED, true));
        assertEquals(CombatStyle.MELEE, fight.protectFrom(null, true));
    }

    @Test
    public void anUnknownStyleAtADistanceFallsBackToMagic()
    {
        DemonFight fight = new DemonFight();
        assertEquals(CombatStyle.MAGIC, fight.protectFrom(CombatStyle.RANGED, false));
        assertEquals(CombatStyle.MAGIC, fight.protectFrom(CombatStyle.MAGIC, false));
        assertEquals(CombatStyle.MAGIC, fight.protectFrom(null, false));
    }

    @Test
    public void aBindMakesTheNextAttackUnknownAgain()
    {
        DemonFight fight = new DemonFight();
        fight.observeAttack(CombatStyle.RANGED);
        fight.observeBind(10);
        assertEquals(CombatStyle.MAGIC, fight.protectFrom(CombatStyle.RANGED, false));
    }
}
