package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * The observed state of one fight: the demon's attack style, the last bind and its fire bomb tiles.
 * Melee hits land on the attack animation tick, so an unknown style must be predicted before the attack.
 * Ranged and magic hits land four ticks after their animation, which leaves time to correct the prayer.
 */
public final class DemonFight
{
    /** Bombs land four ticks after the bind; the player can first move two ticks after it. */
    public static final int BOMB_LANDS = 4;
    public static final int FIRST_STEP = 1;
    public static final int LAST_STEP = 3;
    private static final int NO_BIND = Integer.MIN_VALUE / 2;
    private final Set<WorldPoint> bombs = new LinkedHashSet<>();
    private CombatStyle style;
    private int bindTick = NO_BIND;

    public void reset()
    {
        style = null;
        bindTick = NO_BIND;
        bombs.clear();
    }

    public void observeAttack(CombatStyle observed)
    {
        if (observed != null)
        {
            style = observed;
        }
    }

    /** A new fight starts with an unknown style. */
    public void endFight()
    {
        style = null;
    }

    /** Every demon changes its style after a bind, so the next attack is predicted again. */
    public void observeBind(int tick)
    {
        if (tick - bindTick > BOMB_LANDS)
        {
            bindTick = tick;
            style = null;
            bombs.clear();
        }
    }

    public void observeBomb(WorldPoint target, int tick)
    {
        if (target == null)
        {
            return;
        }
        observeBind(tick);
        bombs.add(target);
    }

    public void expire(int tick)
    {
        if (tick - bindTick > BOMB_LANDS)
        {
            bombs.clear();
        }
    }

    public CombatStyle getStyle()
    {
        return style;
    }

    public Set<WorldPoint> getBombs()
    {
        return Collections.unmodifiableSet(bombs);
    }

    public int getBindTick()
    {
        return bindTick;
    }

    public boolean onBomb(WorldPoint point, int tick)
    {
        return tick - bindTick <= BOMB_LANDS && bombs.contains(point);
    }

    /** True on the ticks a step request can still reach the game before the bombs land. */
    public boolean stepDue(int tick)
    {
        int age = tick - bindTick;
        return age >= FIRST_STEP && age <= LAST_STEP;
    }

    /** No eating, drinking, looting or gear changes between the bind and the bomb hit. */
    public boolean lockedOut(int tick)
    {
        int age = tick - bindTick;
        return age >= 0 && age <= BOMB_LANDS;
    }

    /**
     * The protection prayer to hold. A known style is protected directly. An unknown style is melee when
     * the player fights in melee or the demon is next to the player; otherwise Protect from Magic is held,
     * which still cuts a first ranged attack to about a quarter.
     */
    public CombatStyle protectFrom(CombatStyle ours, boolean demonAdjacent)
    {
        if (style != null)
        {
            return style;
        }
        if (ours == CombatStyle.MELEE || demonAdjacent)
        {
            return CombatStyle.MELEE;
        }
        return CombatStyle.MAGIC;
    }
}
