package net.runelite.client.plugins.microbot.drozulrah;

/** Jad predicts the next attack; ordinary blue phases retain magic protection. */
final class ZulrahPrayerSequence
{
    private boolean magic;
    private int lastAttackTick = -1;
    private int lastProjectileCycle = -1;

    synchronized void reset(boolean magicFirst)
    {
        magic = magicFirst;
        lastAttackTick = -1;
        lastProjectileCycle = -1;
    }

    synchronized boolean attack(int tick)
    {
        if (tick > lastAttackTick)
        {
            magic = !magic;
            lastAttackTick = tick;
        }
        return magic;
    }

    synchronized boolean projectile(int cycle, boolean ranged)
    {
        if (cycle > lastProjectileCycle)
        {
            magic = ranged;
            lastProjectileCycle = cycle;
        }
        return magic;
    }
}
