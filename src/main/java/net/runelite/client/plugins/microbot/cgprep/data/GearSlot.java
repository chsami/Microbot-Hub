package net.runelite.client.plugins.microbot.cgprep.data;

import net.runelite.client.plugins.microbot.cgprep.data.Resource;

/**
 * Craftable gear lines. Tier costs are for upgrading INTO that tier (index 0 = T1, 1 = T2, 2 = T3).
 *
 * Costs from the wiki's Singing Bowl recipe table (after the 22 July 2026 update):
 *  - armour piece needs N of EACH of ore, bark and linum plus shards:
 *      helm  T1 50s/1, T2 50s/1,  T3 100s/2
 *      body  T1 50s/1, T2 100s/2, T3 100s/2
 *      legs  T1 50s/1, T2 50s/1,  T3 100s/2      (full T3 set = 650 shards, 13 of each material)
 *  - weapon: T1 = weapon frame only, T2 = +50 shards, T3 = + component (no shards)
 */
public enum GearSlot
{
	HELM(false, null,
		new int[]{50, 50, 100}, new int[]{1, 1, 2}),
	BODY(false, null,
		new int[]{50, 100, 100}, new int[]{1, 2, 2}),
	LEGS(false, null,
		new int[]{50, 50, 100}, new int[]{1, 1, 2}),
	BOW(true, Resource.BOWSTRING,
		new int[]{0, 50, 0}, new int[]{0, 0, 0}),
	STAFF(true, Resource.ORB,
		new int[]{0, 50, 0}, new int[]{0, 0, 0}),
	HALBERD(true, Resource.SPIKE,
		new int[]{0, 50, 0}, new int[]{0, 0, 0});

	public final boolean weapon;
	/** Component needed for T3 (weapons only). */
	public final Resource t3Component;
	/** Shards to craft/upgrade into tier i+1. */
	public final int[] shards;
	/** Ore, bark AND linum each (armour only) to craft/upgrade into tier i+1. */
	public final int[] eachMaterial;

	GearSlot(boolean weapon, Resource t3Component, int[] shards, int[] eachMaterial)
	{
		this.weapon = weapon;
		this.t3Component = t3Component;
		this.shards = shards;
		this.eachMaterial = eachMaterial;
	}

	/** Item ids T1..T3 for this slot in the given mode. */
	public int[] ids(CgMode m)
	{
		switch (this)
		{
			case HELM:
				return m.helm;
			case BODY:
				return m.body;
			case LEGS:
				return m.legs;
			case BOW:
				return m.bow;
			case STAFF:
				return m.staff;
			default:
				return m.halberd;
		}
	}
}
