package net.runelite.client.plugins.microbot.cgprep.fight;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * The offensive prayers the player picked (settings "Melee / Ranged / Magic prayer", 1.3.0), used
 * in prep and in the boss fight. A pick the account hasn't unlocked falls back to the best one it
 * has, so the defaults (Piety / Rigour / Augury) still work on accounts without them. Call where
 * client reads are allowed (varbits and levels); the script copies the settings in every tick.
 */
public final class OffensePrayers
{
	private OffensePrayers()
	{
	}

	public enum Melee
	{
		PIETY("Piety"), CHIVALRY("Chivalry"), NONE("None");

		private final String label;

		Melee(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum Ranged
	{
		EAGLE_EYE("Eagle Eye"), DEADEYE("Deadeye"), RIGOUR("Rigour"), NONE("None");

		private final String label;

		Ranged(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum Magic
	{
		MYSTIC_MIGHT("Mystic Might"), MYSTIC_VIGOUR("Mystic Vigour"), AUGURY("Augury"), NONE("None");

		private final String label;

		Magic(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public static volatile Melee melee = Melee.PIETY;
	public static volatile Ranged ranged = Ranged.RIGOUR;
	public static volatile Magic magic = Magic.AUGURY;

	private static int prayerLevel()
	{
		return Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
	}

	/** The magic prayer to use, or null (None picked, or none available). */
	public static Rs2PrayerEnum mage()
	{
		Magic m = magic;
		if (m == Magic.NONE)
		{
			return null;
		}
		boolean ok = m == Magic.AUGURY ? Rs2Prayer.isAuguryUnlocked() && prayerLevel() >= Rs2PrayerEnum.AUGURY.getLevel()
			: m == Magic.MYSTIC_VIGOUR ? Rs2Prayer.isMysticVigourUnlocked()
			: prayerLevel() >= Rs2PrayerEnum.MYSTIC_MIGHT.getLevel();
		if (ok)
		{
			return m == Magic.AUGURY ? Rs2PrayerEnum.AUGURY : m == Magic.MYSTIC_VIGOUR ? Rs2PrayerEnum.MYSTIC_VIGOUR : Rs2PrayerEnum.MYSTIC_MIGHT;
		}
		return Rs2Prayer.getBestMagePrayer();
	}

	/** The ranged prayer to use, or null (None picked, or none available). */
	public static Rs2PrayerEnum range()
	{
		Ranged r = ranged;
		if (r == Ranged.NONE)
		{
			return null;
		}
		boolean ok = r == Ranged.RIGOUR ? Rs2Prayer.isRigourUnlocked()
			: r == Ranged.DEADEYE ? Rs2Prayer.isDeadeyeUnlocked()
			: prayerLevel() >= Rs2PrayerEnum.EAGLE_EYE.getLevel();
		if (ok)
		{
			return r == Ranged.RIGOUR ? Rs2PrayerEnum.RIGOUR : r == Ranged.DEADEYE ? Rs2PrayerEnum.DEAD_EYE : Rs2PrayerEnum.EAGLE_EYE;
		}
		return Rs2Prayer.getBestRangePrayer();
	}

	/** The melee prayer to use, or null (None picked, or none available). */
	public static Rs2PrayerEnum melee()
	{
		Melee m = melee;
		if (m == Melee.NONE)
		{
			return null;
		}
		boolean ok = m == Melee.PIETY ? Rs2Prayer.isPietyUnlocked() : Rs2Prayer.isChivalryUnlocked();
		if (ok)
		{
			return m == Melee.PIETY ? Rs2PrayerEnum.PIETY : Rs2PrayerEnum.CHIVALRY;
		}
		return Rs2Prayer.getBestMeleePrayer();
	}
}
