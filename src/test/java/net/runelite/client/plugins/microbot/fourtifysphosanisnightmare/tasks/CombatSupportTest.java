package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CombatSupportTest
{
    private static final int LONG_AGO = Integer.MAX_VALUE / 2;

    @Test
    public void whereTheGameShowsItsThrallTimersTheyDecide()
    {
        assertTrue(CombatTask.thrallUp(1, 0, false, LONG_AGO), "a thrall is out");
        assertTrue(CombatTask.thrallUp(0, 1, false, LONG_AGO), "the spell is cooling down");
        assertFalse(CombatTask.thrallUp(0, 0, false, LONG_AGO), "neither: summon one");
    }

    @Test
    public void withoutThrallTimersAThrallInSightOrARecentSummonCounts()
    {
        assertTrue(CombatTask.thrallUp(0, 0, true, LONG_AGO), "a thrall in sight");
        assertTrue(CombatTask.thrallUp(0, 0, false, CombatTask.THRALL_COOLDOWN_TICKS - 1), "summoned within the cooldown");
        assertFalse(CombatTask.thrallUp(0, 0, false, CombatTask.THRALL_COOLDOWN_TICKS),
            "the cooldown is over and no thrall is out");
    }

    @Test
    public void whereTheGameShowsDeathChargeItsTimersDecide()
    {
        assertTrue(CombatTask.deathChargeUp(1, 0, LONG_AGO), "waiting for a kill");
        assertTrue(CombatTask.deathChargeUp(2, 0, LONG_AGO), "the upgraded spell waiting for a kill");
        assertTrue(CombatTask.deathChargeUp(0, 1, LONG_AGO), "cooling down");
        assertFalse(CombatTask.deathChargeUp(0, 0, LONG_AGO), "neither: cast it");
    }

    @Test
    public void withoutDeathChargeTimersARecentCastCounts()
    {
        assertTrue(CombatTask.deathChargeUp(0, 0, CombatTask.DEATH_CHARGE_COOLDOWN_TICKS - 1), "cast within the cooldown");
        assertFalse(CombatTask.deathChargeUp(0, 0, CombatTask.DEATH_CHARGE_COOLDOWN_TICKS), "the cooldown is over");
    }

    @Test
    public void aThrallIsSummonedOnlyWithItsPrayerCost()
    {
        assertEquals(List.of(Rs2Thrall.GREATER_GHOST, Rs2Thrall.SUPERIOR_GHOST, Rs2Thrall.LESSER_GHOST), spells(6));
        assertEquals(List.of(Rs2Thrall.SUPERIOR_GHOST, Rs2Thrall.LESSER_GHOST), spells(5), "5 points: not the greater");
        assertEquals(List.of(Rs2Thrall.LESSER_GHOST), spells(2));
        assertEquals(List.of(), spells(1), "too few points for any");
    }

    private static List<Rs2Thrall> spells(int prayerPoints)
    {
        return CombatTask.affordableThralls(prayerPoints).stream().map(thrall -> thrall.spell).collect(Collectors.toList());
    }
}
