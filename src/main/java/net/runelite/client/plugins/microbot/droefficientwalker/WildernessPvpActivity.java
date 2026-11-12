package net.runelite.client.plugins.microbot.droefficientwalker;

/** Confirmed player attack activity; following/trading does not renew the game-tick window. */
final class WildernessPvpActivity
{
    static final int QUIET_TICKS = 10;
    private boolean active;
    private int lastAttackTick;

    static boolean isAttackAction(boolean targetsLocal, int animation)
    {
        return targetsLocal && animation != -1;
    }

    boolean observe(boolean inWilderness, boolean attacker, int tick)
    {
        if (!inWilderness || (active && tick < lastAttackTick)) reset();
        if (!inWilderness) return false;
        if (attacker) { active = true; lastAttackTick = tick; }
        else if (active && tick - lastAttackTick >= QUIET_TICKS) reset();
        return active;
    }

    void reset() { active = false; lastAttackTick = 0; }
}
