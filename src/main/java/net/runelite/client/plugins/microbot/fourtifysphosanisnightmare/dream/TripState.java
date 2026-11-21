package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import net.runelite.api.NPC;

/** Shared observations for one session: the trip phase and why the trip is ending. */
public final class TripState
{
    private TripPhase phase;
    private String leaveReason;
    private String stopMessage;
    private int trips;
    private boolean restored;
    private NPC target;
    private Loadout loadout = Loadout.MELEE;
    private boolean longThrows;

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

    /** Leaves, then stops at the bank with the message, since another trip cannot mend the cause. */
    public void leaveAndStop(String reason, String message)
    {
        leave(reason);
        stopMessage = message;
    }

    public String getStopMessage()
    {
        return stopMessage;
    }

    public void arrivedAtBank()
    {
        leaveReason = null;
        restored = false;
        phase = TripPhase.BANKING;
    }

    /** The Pool of Refreshment was drunk from after this bank visit. */
    public boolean isRestored()
    {
        return restored;
    }

    public void setRestored(boolean restored)
    {
        this.restored = restored;
    }

    /** This tick's target, or null. */
    public NPC getTarget()
    {
        return target;
    }

    public void setTarget(NPC target)
    {
        this.target = target;
    }

    /** The equipment wanted for this tick's target. */
    public Loadout getLoadout()
    {
        return loadout;
    }

    public void setLoadout(Loadout loadout)
    {
        this.loadout = loadout;
    }

    public boolean isLongThrows()
    {
        return longThrows;
    }

    public void setLongThrows(boolean longThrows)
    {
        this.longThrows = longThrows;
    }

    public int getTrips()
    {
        return trips;
    }

    public void tripStarted()
    {
        trips++;
        phase = TripPhase.TRAVELLING;
    }
}
