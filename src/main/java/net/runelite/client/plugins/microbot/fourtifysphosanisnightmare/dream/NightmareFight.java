package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.HashSet;
import java.util.Set;

/**
 * One kill as the player sees it: her stage, which shield phase this is, her last standard attack and
 * the curse. Phosani's has three shield phases and then her final phase; her NPC ID names her special
 * set, not the phase, so the phase is counted from her blasts.
 */
public class NightmareFight
{
    private Stage stage;
    private int blasts;
    private AttackStyle attack;
    private int attackTick = -1;
    private boolean cursed;
    private boolean drowsy;
    private boolean weakened;
    /** She has summoned husks or a parasite since her totems woke. */
    private boolean spawned;
    /** Her specials in the current round of the phase's three, counted from the phase's start. */
    private final Set<Integer> round = new HashSet<>();
    /** The round was counted from the phase's start and has not repeated a special. */
    private boolean roundKnown;
    /** A risen parasite's next attack on the player: missiles first, then the other style each time. */
    private AttackStyle parasiteNext = AttackStyle.RANGED;
    /** The tick the missile husk last attacked, or the tick after it rose. */
    private int missileHuskAt = Integer.MIN_VALUE / 2;
    /** Her blast's sleepwalkers, by NPC index, and those of them that reached her. */
    private final Set<Integer> risen = new HashSet<>();
    private final Set<Integer> absorbed = new HashSet<>();

    public void reset()
    {
        stage = null;
        blasts = 0;
        attack = null;
        attackTick = -1;
        cursed = false;
        drowsy = false;
        weakened = false;
        spawned = false;
        round.clear();
        roundKnown = false;
        parasiteNext = AttackStyle.RANGED;
        missileHuskAt = Integer.MIN_VALUE / 2;
        risen.clear();
        absorbed.clear();
    }

    /** Reads her form each tick; a new kill starts when she wakes. */
    public void observeBoss(Stage next)
    {
        if (next == null || next == stage)
        {
            return;
        }
        if (next == Stage.WAKING || next == Stage.ASLEEP)
        {
            blasts = 0;
            attack = null;
            attackTick = -1;
            cursed = false;
        }
        else if (next == Stage.BLAST)
        {
            blasts++;
        }
        else if (next == Stage.TOTEMS)
        {
            spawned = false;
        }
        if (next == Stage.SHIELD && (stage == Stage.WAKING || stage == Stage.BLAST))
        {
            round.clear();
            roundKnown = true;
        }
        if (stage == Stage.BLAST)
        {
            // Her sleepwalkers rise and reach her before she takes her blast form, so they are counted until it ends.
            risen.clear();
            absorbed.clear();
        }
        stage = next;
    }

    /** A sleepwalker seen, and whether she is absorbing it. */
    public void observeSleepwalker(int index, boolean absorbing)
    {
        if (stage == Stage.DESPERATION)
        {
            // Her final phase has no blast: each sleepwalker that reaches her hits for 15 on its own.
            return;
        }
        risen.add(index);
        if (absorbing)
        {
            absorbed.add(index);
        }
    }

    /** Her blast's sleepwalkers have risen, and her blast has not ended. */
    public boolean sleepwalkersUp()
    {
        return !risen.isEmpty();
    }

    /**
     * What her coming blast hits for, from the sleepwalkers that reached her: 5, and 144 shared over those
     * that rose for each one that got through. 0 when no sleepwalkers rose.
     */
    public int blastDamage()
    {
        return risen.isEmpty() ? 0 : 5 + 144 * absorbed.size() / risen.size();
    }

    public void observeAttack(AttackStyle style, int tick)
    {
        if (style != null)
        {
            attack = style;
            attackTick = tick;
        }
    }

    /** She started a special with this animation; others are ignored. */
    public void observeSpecial(int animation)
    {
        if (!DreamData.SPECIALS.contains(animation))
        {
            return;
        }
        if (animation == DreamData.HUSKS || animation == DreamData.PARASITE)
        {
            spawned = true;
        }
        if (round.size() == 3)
        {
            round.clear();
        }
        if (!round.add(animation))
        {
            // A repeat inside one round: the rounds did not start where they were counted from.
            roundKnown = false;
        }
    }

    /** A risen parasite attacked the player with this animation; it alternates missiles and magic. */
    public void observeParasiteAttack(int animation)
    {
        if (animation == DreamData.PARASITE_RANGED)
        {
            parasiteNext = AttackStyle.MAGIC;
        }
        else if (animation == DreamData.PARASITE_MAGIC)
        {
            parasiteNext = AttackStyle.RANGED;
        }
    }

    /** The missile husk rose or attacked on this tick. */
    public void observeMissileHusk(int animation, int tick)
    {
        if (animation == DreamData.HUSK_RISE)
        {
            missileHuskAt = tick + DreamData.RANGED_HUSK_FIRST_TICKS - DreamData.HUSK_ATTACK_TICKS;
        }
        else if (animation == DreamData.RANGED_HUSK_ATTACK)
        {
            missileHuskAt = tick;
        }
    }

    /** The missile husk attacks on this tick. */
    public boolean missileHuskAttacks(int tick)
    {
        return tick > missileHuskAt && (tick - missileHuskAt) % DreamData.HUSK_ATTACK_TICKS == 0;
    }

    /** A new parasite has risen: its first attack is with missiles. */
    public void parasiteRose()
    {
        parasiteNext = AttackStyle.RANGED;
    }

    public AttackStyle parasiteNext()
    {
        return parasiteNext;
    }

    /** She has summoned husks or a parasite since her totems woke. */
    public boolean spawnedSinceTotems()
    {
        return spawned;
    }

    /** Her specials have been counted in rounds from the phase's start. */
    public boolean roundKnown()
    {
        return roundKnown;
    }

    /** Her next special may be her husks or her parasite: this round has not had it yet, or has ended. */
    public boolean spawnCanComeNext()
    {
        return round.size() == 3 || !round.contains(DreamData.HUSKS) && !round.contains(DreamData.PARASITE);
    }

    public void setCursed(boolean cursed)
    {
        this.cursed = cursed;
    }

    public void setDrowsy(boolean drowsy)
    {
        this.drowsy = drowsy;
    }

    /** The parasite inside the player has been weakened; cleared once it is gone. */
    public void setWeakened(boolean weakened)
    {
        this.weakened = weakened;
    }

    public boolean isWeakened()
    {
        return weakened;
    }

    public Stage getStage()
    {
        return stage;
    }

    /** The shield phase, 1 to 3; 4 once she lashes out in her final phase. */
    public int getPhase()
    {
        if (stage == Stage.DESPERATION)
        {
            return 4;
        }
        // Her blast ends a phase; the blast itself still belongs to it.
        return stage == Stage.BLAST ? blasts : blasts + 1;
    }

    public boolean isFighting()
    {
        return stage == Stage.SHIELD || stage == Stage.TOTEMS || stage == Stage.BLAST || stage == Stage.DESPERATION;
    }

    public boolean isCursed()
    {
        return cursed;
    }

    public boolean isDrowsy()
    {
        return drowsy;
    }

    /** Her attack that has not landed yet on this tick, or none. */
    public AttackStyle pendingAttack(int tick)
    {
        if (attack == null || tick > attackTick + attack.getHitTicks())
        {
            return null;
        }
        return attack;
    }

    /** Her last standard attack this kill, or null. */
    public AttackStyle getLastAttack()
    {
        return attack;
    }

    public int getAttackTick()
    {
        return attackTick;
    }
}
