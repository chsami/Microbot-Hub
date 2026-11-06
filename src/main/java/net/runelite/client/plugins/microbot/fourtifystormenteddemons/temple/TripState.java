package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import net.runelite.api.NPC;

/** Shared observations for one session: the trip phase, the engaged demon and the worn equipment style. */
public final class TripState
{
    static final int DROP_TICKS = 7;
    static final int HIT_AFTER_SWITCH_TICKS = 4;
    static final int SWITCH_GRACE_TICKS = 8;
    private TripPhase phase;
    private String leaveReason;
    private NPC target;
    private int lastEngagedTick = Integer.MIN_VALUE / 2;
    private CombatStyle style;
    private boolean attackNeeded;
    private int switchedAt = Integer.MIN_VALUE / 2;
    private int dropDueBy = Integer.MIN_VALUE / 2;
    private int trips;

    public TripPhase getPhase()
    {
        return phase;
    }

    public void setPhase(TripPhase phase)
    {
        this.phase = phase;
    }

    public String getLeaveReason()
    {
        return leaveReason;
    }

    public void leave(String reason)
    {
        leaveReason = reason;
        phase = TripPhase.LEAVING;
    }

    public void arrivedAtBank()
    {
        leaveReason = null;
        phase = TripPhase.BANKING;
    }

    public NPC getTarget()
    {
        return target;
    }

    public void engage(NPC npc, int tick)
    {
        target = npc;
        if (npc != null)
        {
            lastEngagedTick = tick;
        }
    }

    public int ticksSinceEngaged(int tick)
    {
        return tick - lastEngagedTick;
    }

    public CombatStyle getStyle()
    {
        return style;
    }

    public void setStyle(CombatStyle style)
    {
        this.style = style;
    }

    /**
     * A gear switch usually keeps the player attacking with the new weapon. The demon is clicked again
     * only when none of the player's hits lands on it within {@link #SWITCH_GRACE_TICKS} of the switch.
     */
    public void equipmentChanged(int tick)
    {
        attackNeeded = true;
        switchedAt = tick;
    }

    /** One of the player's hits landed on the engaged demon; one from before the switch does not count. */
    public void hitLanded(int tick)
    {
        if (tick - switchedAt >= HIT_AFTER_SWITCH_TICKS)
        {
            attackNeeded = false;
        }
    }

    public boolean isAttackNeeded(int tick)
    {
        return attackNeeded && tick - switchedAt >= SWITCH_GRACE_TICKS;
    }

    public void attackSent()
    {
        attackNeeded = false;
    }

    /** A demon died: its drop appears about 6 ticks after the death starts. */
    public void killed(int tick)
    {
        dropDueBy = tick + DROP_TICKS;
    }

    public boolean isWaitingForDrop(int tick)
    {
        return tick <= dropDueBy;
    }

    public int getTrips()
    {
        return trips;
    }

    public void tripStarted()
    {
        trips++;
    }
}
