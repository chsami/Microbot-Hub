package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** A combat style with its protection prayer and the demon overhead sprite that blocks it. */
public enum CombatStyle
{
    MELEE("Melee", Rs2PrayerEnum.PROTECT_MELEE, 0),
    RANGED("Ranged", Rs2PrayerEnum.PROTECT_RANGE, 1),
    MAGIC("Magic", Rs2PrayerEnum.PROTECT_MAGIC, 2);

    private final String displayName;
    private final Rs2PrayerEnum protection;
    private final int overheadSprite;

    CombatStyle(String displayName, Rs2PrayerEnum protection, int overheadSprite)
    {
        this.displayName = displayName;
        this.protection = protection;
        this.overheadSprite = overheadSprite;
    }

    public Rs2PrayerEnum getProtection()
    {
        return protection;
    }

    public int getOverheadSprite()
    {
        return overheadSprite;
    }

    /** Returns the style a demon protects from, or null for no recognised overhead sprite. */
    public static CombatStyle fromOverhead(short[] sprites)
    {
        if (sprites == null)
        {
            return null;
        }
        for (short sprite : sprites)
        {
            for (CombatStyle style : values())
            {
                if (sprite == style.overheadSprite)
                {
                    return style;
                }
            }
        }
        return null;
    }

    public static CombatStyle fromAttackAnimation(int animation)
    {
        if (animation == TempleData.MELEE_ANIMATION)
        {
            return MELEE;
        }
        if (animation == TempleData.RANGED_ANIMATION)
        {
            return RANGED;
        }
        return animation == TempleData.MAGIC_ANIMATION ? MAGIC : null;
    }

    public static CombatStyle fromProjectile(int projectile)
    {
        if (projectile == TempleData.RANGED_PROJECTILE)
        {
            return RANGED;
        }
        return projectile == TempleData.MAGIC_PROJECTILE ? MAGIC : null;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
