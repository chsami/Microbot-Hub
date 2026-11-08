package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** Her three standard attacks, the prayer that blocks each, and when each lands. */
public enum AttackStyle
{
    MAGIC(Rs2PrayerEnum.PROTECT_MAGIC, 4),
    RANGED(Rs2PrayerEnum.PROTECT_RANGE, 4),
    MELEE(Rs2PrayerEnum.PROTECT_MELEE, 2);

    private final Rs2PrayerEnum protection;
    private final int hitTicks;

    AttackStyle(Rs2PrayerEnum protection, int hitTicks)
    {
        this.protection = protection;
        this.hitTicks = hitTicks;
    }

    public Rs2PrayerEnum getProtection()
    {
        return protection;
    }

    /** Ticks from the start of her animation to the hit. */
    public int getHitTicks()
    {
        return hitTicks;
    }

    /**
     * The prayer button that blocks this style. Her curse shifts each button one to the right: Protect from
     * Melee blocks magic, Protect from Magic blocks missiles and Protect from Missiles blocks melee.
     */
    public Rs2PrayerEnum button(boolean cursed)
    {
        if (!cursed)
        {
            return protection;
        }
        if (this == MAGIC)
        {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        if (this == RANGED)
        {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        }
        return Rs2PrayerEnum.PROTECT_RANGE;
    }

    public static AttackStyle fromAnimation(int animation)
    {
        if (animation == DreamData.MAGIC_ATTACK)
        {
            return MAGIC;
        }
        if (animation == DreamData.RANGED_ATTACK)
        {
            return RANGED;
        }
        if (animation == DreamData.MELEE_ATTACK)
        {
            return MELEE;
        }
        return null;
    }
}
