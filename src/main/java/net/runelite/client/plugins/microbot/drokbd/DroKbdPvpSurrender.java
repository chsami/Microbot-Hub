package net.runelite.client.plugins.microbot.drokbd;

/** Shared by the trip loop and fast threat watcher; timing uses game ticks. */
final class DroKbdPvpSurrender
{
    static final int QUIET_TICKS = 10;
    private boolean active;
    private int lastAttackerTick;

    static boolean isAttackAction(boolean targetsLocal, int animation)
    {
        return targetsLocal && animation != -1;
    }

    synchronized boolean observe(boolean inWilderness, boolean attacker, int gameTick)
    {
        if (!inWilderness || (active && gameTick < lastAttackerTick)) reset();
        if (!inWilderness) return false;
        if (attacker)
        {
            active = true;
            lastAttackerTick = gameTick;
        }
        else if (active && gameTick - lastAttackerTick >= QUIET_TICKS)
        {
            reset();
        }
        return active;
    }

    synchronized boolean isActive()
    {
        return active;
    }

    synchronized void reset()
    {
        active = false;
        lastAttackerTick = 0;
    }
}
