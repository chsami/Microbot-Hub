package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

/**
 * Her final phase, read each tick: how much of her health bar is gone and how many Hitpoints the player has lost
 * since it began. At that pace, the player loses as much again for each share of her health still left.
 */
public final class LastStand
{
    /** Each sleepwalker that reaches her in this phase blasts the player for this much. */
    static final int SLEEPWALKER_HIT = 15;
    private boolean started;
    private int lastHitpoints;
    private int taken;
    private double health = 1;

    public void reset()
    {
        started = false;
        lastHitpoints = 0;
        taken = 0;
        health = 1;
    }

    /**
     * @param ratio her health bar's filled part, or -1 while the bar is not shown
     * @param scale the health bar's full size
     */
    public void observe(int hitpoints, int ratio, int scale)
    {
        if (started && hitpoints < lastHitpoints)
        {
            taken += lastHitpoints - hitpoints;
        }
        started = true;
        lastHitpoints = hitpoints;
        if (ratio >= 0 && scale > 0)
        {
            health = (double) ratio / scale;
        }
    }

    /** Whether the player can be expected to live until she dies, with one sleepwalker's hit to spare. */
    public boolean outlasts(int hitpoints)
    {
        // Until she has lost health, such as while a parasite takes the attacks, there is no pace to judge by.
        if (taken == 0 || health >= 1)
        {
            return hitpoints > SLEEPWALKER_HIT;
        }
        return hitpoints - taken * health / (1 - health) > SLEEPWALKER_HIT;
    }
}
