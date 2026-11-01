package net.runelite.client.plugins.microbot.cgprep.plan;

import net.runelite.client.plugins.microbot.cgprep.data.Resource;

/**
 * What an UNEXPLORED room is likely to contain, by ring (distance from the boss room).
 *
 * Sources:
 *  - Our recon logs (6 runs, 56 explored rooms, 2026-10-26). Average resource nodes per room:
 *      inner  (8 rooms around the boss, 18 seen):  ore .78  bark .56  linum .72  grym .56  fish .72
 *      middle (16 rooms, 25 seen):                 ore .84  bark .44  linum .48  grym .64  fish 1.04
 *      outer  (24 rooms, 13 seen):                 ore .15  bark .46  linum .62  grym .54  fish .85
 *    Rooms are "themed": most hold a cluster of 3 nodes of one type plus a few singles.
 *  - Wiki (The_Gauntlet/Strategies): the inner 3x3 has at least 3 of each material; one room next
 *    to the Hunllef always has 3+ fishing spots; demi-bosses spawn ONLY on the outer edge; weak
 *    monsters are common everywhere and drop a guaranteed frame on the first kill.
 *
 * Values are rounded and lightly smoothed towards the wiki claims. Recalibrate as logs grow.
 */
public final class RoomPriors
{
	public enum Ring
	{
		BOSS, INNER, MIDDLE, OUTER
	}

	private RoomPriors()
	{
	}

	public static Ring ring(int x, int z)
	{
		int d = Math.max(Math.abs(x - 3), Math.abs(z - 3));
		return d == 0 ? Ring.BOSS : d == 1 ? Ring.INNER : d == 2 ? Ring.MIDDLE : Ring.OUTER;
	}

	/** Expected number of nodes of this resource in an unexplored room of this ring. */
	public static double expectedNodes(Resource r, Ring ring)
	{
		switch (ring)
		{
			case INNER:
				switch (r)
				{
					case ORE: return 0.80;
					case BARK: return 0.60;
					case LINUM: return 0.70;
					case GRYM: return 0.55;
					case RAW_FISH: return 0.70;
					default: return 0;
				}
			case MIDDLE:
				switch (r)
				{
					case ORE: return 0.80;
					case BARK: return 0.45;
					case LINUM: return 0.50;
					case GRYM: return 0.60;
					case RAW_FISH: return 1.00;
					default: return 0;
				}
			case OUTER:
				switch (r)
				{
					case ORE: return 0.20;
					case BARK: return 0.45;
					case LINUM: return 0.60;
					case GRYM: return 0.55;
					case RAW_FISH: return 0.85;
					default: return 0;
				}
			default:
				return 0;
		}
	}

	/** Chance a room has weak monsters (rats, spiders, bats): seen in roughly 6 of 10 rooms. */
	public static double weakMonsterChance(Ring ring)
	{
		return ring == Ring.BOSS ? 0 : 0.6;
	}

	/** Chance a room holds one specific demi-boss type. Outer ring only (wiki); ~2 of each per map. */
	public static double demiBossChance(Ring ring)
	{
		return ring == Ring.OUTER ? 0.08 : 0;
	}
}
