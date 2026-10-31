package net.runelite.client.plugins.microbot.cgprep.boss;

/** Combat style: what the Hunllef attacks with / protects against, and what our weapon deals. */
public enum Style
{
	RANGED, MAGIC, MELEE;

	/** The other one of ranged/magic (the Hunllef only alternates between these two). */
	public Style flip()
	{
		return this == RANGED ? MAGIC : RANGED;
	}
}
