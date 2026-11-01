package net.runelite.client.plugins.microbot.cgprep.data;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Monster classes inside the maze and the protection prayer to use against each.
 * TODO(recon): confirm attack styles from ANIM/PROJ lines (weak/strong monsters are melee per wiki;
 * Dark Beast = ranged, Dragon = magic, Bear = melee).
 */
public enum Monster
{
	WEAK(Rs2PrayerEnum.PROTECT_MELEE),
	STRONG(Rs2PrayerEnum.PROTECT_MELEE),
	BEAR(Rs2PrayerEnum.PROTECT_MELEE),
	DARK_BEAST(Rs2PrayerEnum.PROTECT_RANGE),
	DRAGON(Rs2PrayerEnum.PROTECT_MAGIC);

	public final Rs2PrayerEnum protection;

	Monster(Rs2PrayerEnum protection)
	{
		this.protection = protection;
	}

	public int[] ids(CgMode m)
	{
		switch (this)
		{
			case WEAK:
				return m.weakMonsters;
			case STRONG:
				return m.strongMonsters;
			case BEAR:
				return new int[]{m.bear};
			case DARK_BEAST:
				return new int[]{m.darkBeast};
			default:
				return new int[]{m.dragon};
		}
	}

	/** Demi-boss that guarantees a given T3 component (wiki mapping). */
	public static Monster forComponent(Resource r)
	{
		switch (r)
		{
			case BOWSTRING:
				return DARK_BEAST;
			case ORB:
				return DRAGON;
			case SPIKE:
				return BEAR;
			default:
				return null;
		}
	}
}
