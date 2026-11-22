package net.runelite.client.plugins.microbot.droinfernoprayers.prayer;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** The overhead protection the plugin wants up. NONE means no overhead. */
public enum Guard
{
	NONE(null, "None"),
	MELEE(Rs2PrayerEnum.PROTECT_MELEE, "Melee"),
	RANGE(Rs2PrayerEnum.PROTECT_RANGE, "Missiles"),
	MAGIC(Rs2PrayerEnum.PROTECT_MAGIC, "Magic");

	private final Rs2PrayerEnum prayer;
	private final String label;

	Guard(Rs2PrayerEnum prayer, String label)
	{
		this.prayer = prayer;
		this.label = label;
	}

	public Rs2PrayerEnum prayer()
	{
		return prayer;
	}

	public String label()
	{
		return label;
	}
}
