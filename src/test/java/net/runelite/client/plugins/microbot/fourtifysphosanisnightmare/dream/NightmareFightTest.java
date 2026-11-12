package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.Set;
import org.junit.jupiter.api.Test;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NightmareFightTest
{
    @Test
    public void herFormsMapToStages()
    {
        assertEquals(Stage.WAKING, Stage.fromNpcId(9423));
        for (int id : new int[] {9416, 9417, 9418, 11153})
        {
            assertEquals(Stage.SHIELD, Stage.fromNpcId(id));
        }
        for (int id : new int[] {9419, 9420, 9421, 11155})
        {
            assertEquals(Stage.TOTEMS, Stage.fromNpcId(id));
        }
        assertEquals(Stage.BLAST, Stage.fromNpcId(9422));
        assertEquals(Stage.DESPERATION, Stage.fromNpcId(11154));
        assertEquals(Stage.DYING, Stage.fromNpcId(9424));
        assertEquals(Stage.ASLEEP, Stage.fromNpcId(377));
        assertNull(Stage.fromNpcId(9470));
    }

    @Test
    public void eachBlastEndsAShieldPhaseAndTheFinalPhaseIsFour()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.WAKING);
        fight.observeBoss(Stage.SHIELD);
        assertEquals(1, fight.getPhase());
        fight.observeBoss(Stage.TOTEMS);
        fight.observeBoss(Stage.BLAST);
        assertEquals(1, fight.getPhase(), "the blast still belongs to its phase");
        fight.observeBoss(Stage.SHIELD);
        assertEquals(2, fight.getPhase());
        fight.observeBoss(Stage.TOTEMS);
        fight.observeBoss(Stage.BLAST);
        fight.observeBoss(Stage.SHIELD);
        assertEquals(3, fight.getPhase());
        fight.observeBoss(Stage.DESPERATION);
        assertEquals(4, fight.getPhase());
        assertTrue(fight.isFighting());
        fight.observeBoss(Stage.DYING);
        assertFalse(fight.isFighting());
        fight.observeBoss(Stage.WAKING);
        fight.observeBoss(Stage.SHIELD);
        assertEquals(1, fight.getPhase(), "a new kill counts from the start");
    }

    @Test
    public void anAttackIsPendingUntilItLandsAndMeleeLandsSooner()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.SHIELD);
        fight.observeAttack(AttackStyle.MAGIC, 10);
        assertEquals(AttackStyle.MAGIC, fight.pendingAttack(14));
        assertNull(fight.pendingAttack(15));
        fight.observeAttack(AttackStyle.MELEE, 20);
        assertEquals(AttackStyle.MELEE, fight.pendingAttack(22));
        assertNull(fight.pendingAttack(23));
        assertEquals(AttackStyle.MELEE, fight.getLastAttack());
    }

    @Test
    public void herBlastHitsForTheShareOfSleepwalkersThatReachedHer()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.SHIELD);
        assertEquals(0, fight.blastDamage(), "no blast is coming");
        for (int index = 1; index <= 4; index++)
        {
            fight.observeSleepwalker(index, false, false);
        }
        assertEquals(5, fight.blastDamage());
        fight.observeSleepwalker(3, true, false);
        assertEquals(41, fight.blastDamage(), "one reached her before she took her blast form");
        fight.observeBoss(Stage.BLAST);
        fight.observeSleepwalker(4, true, false);
        fight.observeSleepwalker(4, true, false);
        assertEquals(77, fight.blastDamage(), "two of four");
        fight.observeBoss(Stage.SHIELD);
        assertEquals(0, fight.blastDamage(), "her blast has ended");
        fight.observeSleepwalker(7, false, false);
        fight.observeSleepwalker(8, true, false);
        assertEquals(77, fight.blastDamage(), "one of two, counted afresh for the next blast");
    }

    @Test
    public void aSleepwalkerNeitherSeenDyingNorReachingHerIsOutOfViewOnlyWhileUnseen()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.BLAST);
        fight.observeSleepwalker(1, true, false);
        fight.observeSleepwalker(2, false, true);
        fight.observeSleepwalker(3, false, false);
        assertTrue(fight.sleepwalkerOutOfView(Set.of()), "the third has not been seen dying");
        assertFalse(fight.sleepwalkerOutOfView(Set.of(3)));
        fight.observeSleepwalker(3, false, true);
        assertFalse(fight.sleepwalkerOutOfView(Set.of()), "all three are done");
        fight.observeSleepwalker(4, false, false);
        fight.observeBoss(Stage.SHIELD);
        assertFalse(fight.sleepwalkerOutOfView(Set.of()), "her blast has ended");
    }

    @Test
    public void herFinalPhaseSleepwalkersBringNoBlast()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.DESPERATION);
        fight.observeSleepwalker(1, false, false);
        fight.observeSleepwalker(2, true, false);
        assertEquals(0, fight.blastDamage(), "each hits for 15 on its own");
        assertFalse(fight.sleepwalkersUp());
    }

    @Test
    public void herCurseShiftsEachProtectionButtonOneToTheRight()
    {
        assertEquals(Rs2PrayerEnum.PROTECT_MAGIC, AttackStyle.MAGIC.button(false));
        assertEquals(Rs2PrayerEnum.PROTECT_MELEE, AttackStyle.MAGIC.button(true));
        assertEquals(Rs2PrayerEnum.PROTECT_MAGIC, AttackStyle.RANGED.button(true));
        assertEquals(Rs2PrayerEnum.PROTECT_RANGE, AttackStyle.MELEE.button(true));
        assertEquals(AttackStyle.RANGED, AttackStyle.fromAnimation(8596));
        assertEquals(AttackStyle.MAGIC, AttackStyle.fromAnimation(8595));
        assertEquals(AttackStyle.MELEE, AttackStyle.fromAnimation(8594));
        assertNull(AttackStyle.fromAnimation(8598));
    }

    @Test
    public void wakingClearsTheCurseButKeepsTheParasiteState()
    {
        NightmareFight fight = new NightmareFight();
        fight.setCursed(true);
        fight.setWeakened(true);
        fight.observeBoss(Stage.WAKING);
        assertFalse(fight.isCursed());
        assertTrue(fight.isWeakened());
        fight.reset();
        assertFalse(fight.isWeakened());
    }

    @Test
    public void herHusksOrParasiteCountFromWhenHerTotemsWake()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.SHIELD);
        fight.observeSpecial(DreamData.HUSKS);
        fight.observeBoss(Stage.TOTEMS);
        assertFalse(fight.spawnedSinceTotems(), "husks before the totems woke do not count");
        fight.observeSpecial(DreamData.HUSKS);
        assertTrue(fight.spawnedSinceTotems());
        fight.observeBoss(Stage.BLAST);
        fight.observeBoss(Stage.SHIELD);
        fight.observeBoss(Stage.TOTEMS);
        assertFalse(fight.spawnedSinceTotems(), "each phase's totems start again");
    }

    @Test
    public void herSpecialsAreCountedInRoundsOfThreeFromThePhasesStart()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.WAKING);
        fight.observeBoss(Stage.SHIELD);
        assertTrue(fight.roundKnown());
        fight.observeSpecial(DreamData.CURSE);
        assertTrue(fight.spawnCanComeNext(), "the round has not had its husks");
        fight.observeSpecial(DreamData.CLAWS);
        fight.observeSpecial(DreamData.HUSKS);
        assertFalse(fight.spawnCanComeNext(), "the round has had its husks");
        fight.observeBoss(Stage.TOTEMS);
        assertFalse(fight.spawnCanComeNext(), "the round goes on through her totems");
        fight.observeSpecial(DreamData.SPORES);
        assertTrue(fight.spawnCanComeNext(), "the round is over: the next may be husks");
        fight.observeSpecial(DreamData.HUSKS);
        assertFalse(fight.spawnCanComeNext(), "the next round opened with husks");
        assertTrue(fight.roundKnown());
    }

    @Test
    public void eachPhaseStartsANewRound()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.WAKING);
        fight.observeBoss(Stage.SHIELD);
        fight.observeSpecial(DreamData.PARASITE);
        fight.observeBoss(Stage.TOTEMS);
        fight.observeBoss(Stage.BLAST);
        fight.observeBoss(Stage.SHIELD);
        assertTrue(fight.roundKnown());
        assertTrue(fight.spawnCanComeNext(), "the new phase's round has not had its spawn");
        fight.observeSpecial(DreamData.SURGE_CHARGE);
        fight.observeSpecial(DreamData.PARASITE);
        assertFalse(fight.spawnCanComeNext());
    }

    @Test
    public void theRoundIsUnknownWithoutThePhasesStartOrAfterARepeat()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.SHIELD);
        assertFalse(fight.roundKnown(), "started in the middle of a phase");
        fight.observeBoss(Stage.TOTEMS);
        fight.observeBoss(Stage.BLAST);
        fight.observeBoss(Stage.SHIELD);
        assertTrue(fight.roundKnown());
        fight.observeSpecial(DreamData.HUSKS);
        fight.observeSpecial(DreamData.HUSKS);
        assertFalse(fight.roundKnown(), "a special twice in one round");
    }

    @Test
    public void joinedAfterSheWokeHerBlastsSleepwalkersNameThePhase()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.SHIELD);
        fight.observeBoss(Stage.TOTEMS);
        for (int index = 1; index <= 3; index++)
        {
            fight.observeSleepwalker(index, false, false);
        }
        fight.observeBoss(Stage.BLAST);
        assertEquals(2, fight.getPhase(), "three sleepwalkers: her second blast");
        fight.observeBoss(Stage.SHIELD);
        assertEquals(3, fight.getPhase());
        fight.observeBoss(Stage.TOTEMS);
        for (int index = 4; index <= 7; index++)
        {
            fight.observeSleepwalker(index, false, false);
        }
        fight.observeBoss(Stage.BLAST);
        assertEquals(3, fight.getPhase());
    }

    @Test
    public void sinceSheWokeHerBlastsAreCounted()
    {
        NightmareFight fight = new NightmareFight();
        fight.observeBoss(Stage.WAKING);
        fight.observeBoss(Stage.SHIELD);
        fight.observeBoss(Stage.TOTEMS);
        // A sleepwalker left from another phase does not change the count.
        for (int index = 1; index <= 3; index++)
        {
            fight.observeSleepwalker(index, false, false);
        }
        fight.observeBoss(Stage.BLAST);
        assertEquals(1, fight.getPhase());
        fight.observeBoss(Stage.SHIELD);
        assertEquals(2, fight.getPhase());
    }

    @Test
    public void aParasitesAttacksAlternateStyles()
    {
        NightmareFight fight = new NightmareFight();
        assertEquals(AttackStyle.RANGED, fight.parasiteNext());
        fight.observeParasiteAttack(DreamData.PARASITE_RANGED);
        assertEquals(AttackStyle.MAGIC, fight.parasiteNext());
        fight.observeParasiteAttack(DreamData.PARASITE_MAGIC);
        assertEquals(AttackStyle.RANGED, fight.parasiteNext());
        fight.observeParasiteAttack(DreamData.PARASITE_RANGED);
        fight.parasiteRose();
        assertEquals(AttackStyle.RANGED, fight.parasiteNext(), "a new parasite starts with missiles");
    }
}
