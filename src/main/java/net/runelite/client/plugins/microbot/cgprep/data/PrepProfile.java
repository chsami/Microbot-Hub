package net.runelite.client.plugins.microbot.cgprep.data;

import java.util.EnumMap;
import java.util.Map;

/**
 * Target loadouts. The planner shops for whatever is missing between the current ledger and the
 * profile, and degrades gracefully (see PrepPlanner) when the timer runs short.
 */
public enum PrepProfile
{
	/** The project's goal: T1 armour, perfected bow + perfected staff. Needs Dark Beast + Dragon. */
	T1_ARMOUR_T3_BOW_STAFF("T1 armour + T3 bow + T3 staff", 1, 1, 1, 3, 3, 0, 3),
	/**
	 * T2 armour with both T3 weapons: +200 shards and +4 each of ore/bark/linum over the T1 set
	 * (7 of each in total, 3 nodes per type). When short, the T3 bow is crafted before the T2
	 * upgrades (Needs.craftOrder), and the upgrades go body -> legs -> helm.
	 */
	T2_ARMOUR_T3_BOW_STAFF("T2 armour + T3 bow + T3 staff", 2, 2, 2, 3, 3, 0, 3),
	/**
	 * Full T3 (perfected) armour with both T3 weapons: 650 shards and 13 each of ore/bark/linum for
	 * the armour (+300 shards, +6 of each over T2). For accounts with low Defence (see the README's
	 * loadout table).
	 */
	T3_ARMOUR_T3_BOW_STAFF("T3 armour + T3 bow + T3 staff", 3, 3, 3, 3, 3, 0, 3),
	/** Speed variant: perfected bow, basic staff. Only needs the Dark Beast. */
	T1_ARMOUR_T3_BOW_T1_STAFF("T1 armour + T3 bow + T1 staff", 1, 1, 1, 3, 1, 0, 3),
	/**
	 * Dev profile for testing the crafting/brewing/cooking chain quickly (M2): everything T1 and
	 * exactly one potion, regardless of the Potions setting. Not meant for an actual kill.
	 */
	DEV_ALL_T1("DEV: all T1, 1 potion", 1, 1, 1, 1, 1, 0, 1);

	public final String label;
	public final Map<GearSlot, Integer> targetTier = new EnumMap<>(GearSlot.class);
	public final int potions;

	PrepProfile(String label, int helm, int body, int legs, int bow, int staff, int halberd, int potions)
	{
		this.label = label;
		targetTier.put(GearSlot.HELM, helm);
		targetTier.put(GearSlot.BODY, body);
		targetTier.put(GearSlot.LEGS, legs);
		targetTier.put(GearSlot.BOW, bow);
		targetTier.put(GearSlot.STAFF, staff);
		targetTier.put(GearSlot.HALBERD, halberd);
		this.potions = potions;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
