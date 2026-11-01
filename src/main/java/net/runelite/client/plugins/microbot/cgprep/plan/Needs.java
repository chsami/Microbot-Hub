package net.runelite.client.plugins.microbot.cgprep.plan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.GearSlot;
import net.runelite.client.plugins.microbot.cgprep.data.PrepProfile;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;

/**
 * The shopping list: what's still missing between a {@link Ledger} and a {@link PrepProfile}, plus
 * what could be crafted right now. Pure function of its inputs, so it's unit-testable offline.
 */
public final class Needs
{
	/** Units still to gather per resource (0 = have enough). */
	public final Map<Resource, Integer> deficit = new EnumMap<>(Resource.class);
	/** Gear slots whose next tier is affordable right now, in priority order. */
	public final List<GearSlot> craftableNow = new ArrayList<>();
	/** Total raw demand per resource (have + deficit), for the overlay. */
	public final Map<Resource, Integer> demand = new EnumMap<>(Resource.class);
	/** Egniol brews still to do (each brew = one 3-dose potion). */
	public int potionsToMake;
	/** Vials still to make at the bowl. */
	public int vialsToMake;
	/** Doses the boss gets (dose model: 6), 0 = old "N potions" model. */
	public int bossDoses;
	/** Doses that may be sipped during prep without shorting the boss (dose model: 3). */
	public int prepDoseAllowance;
	/** Potion containers wanted at the end (decant method: 2, a 4-dose and a 3-dose). */
	public int containersTarget;
	/** Empty Egniol vials vanish when drunk (learned in game): every brew needs its own vial. */
	public boolean vialsSmash;
	/**
	 * Shards still needed for the REQUIRED potions (vials + dust); kept back from armour upgrades
	 * past T1. Since 0.3.34 only the first brews (the boss's 6 doses with nothing sipped) count:
	 * T2 armour and both T3 weapons are required, potions are loose (user), so the extra brew
	 * that replaces sipped doses gets what's left after the gear.
	 */
	public int potionShards;
	/** Brews still to make that are required (the rest of potionsToMake is the loose extra). */
	public int requiredPotionsToMake;

	/** Priority for crafting and for the late-timer fallback: weapons first, then body, legs, helm. */
	private static final GearSlot[] PRIORITY = {
		GearSlot.STAFF, GearSlot.HALBERD, GearSlot.BODY, GearSlot.LEGS, GearSlot.HELM, GearSlot.BOW};

	/**
	 * Who gets the shards when they're short (each slot's NEXT tier): staff, then T1 armour, then
	 * the bow, then armour upgrades past T1. With T1 armour this is the user's staff -> armour -> bow;
	 * with a T2-armour profile the T3 bow still beats the T2 upgrades.
	 */
	static java.util.List<GearSlot> craftOrder(Ledger l)
	{
		java.util.List<GearSlot> order = new java.util.ArrayList<>(java.util.Arrays.asList(PRIORITY));
		order.sort(java.util.Comparator.comparingInt(s -> rank(s, l.tier.getOrDefault(s, 0))));
		return order;
	}

	/** Every armour piece the profile wants at {@code tier}+ is at least that tier. */
	static boolean armourAtLeast(Ledger l, PrepProfile p, int tier)
	{
		for (GearSlot s : new GearSlot[]{GearSlot.HELM, GearSlot.BODY, GearSlot.LEGS})
		{
			if (p.targetTier.getOrDefault(s, 0) >= tier && l.tier.getOrDefault(s, 0) < tier)
			{
				return false;
			}
		}
		return true;
	}

	private static int rank(GearSlot s, int cur)
	{
		if (s == GearSlot.STAFF || s == GearSlot.HALBERD)
		{
			return 0;
		}
		if (s == GearSlot.BOW)
		{
			return 2;
		}
		return cur == 0 ? 1 : cur == 1 ? 3 : 4; // armour: into T1 before the bow, T2 after it, T3 last
	}

	public static Needs compute(Ledger l, PrepProfile p, int fishTarget, int shardBuffer)
	{
		return compute(l, p, fishTarget, shardBuffer, p.potions);
	}

	/** @param potionTarget number of Egniol potions wanted (each crafted potion is a 3-dose) */
	public static Needs compute(Ledger l, PrepProfile p, int fishTarget, int shardBuffer, int potionTarget)
	{
		return compute(l, p, fishTarget, shardBuffer, potionTarget, false);
	}

	/**
	 * @param decant the players' 2-vial method (user): brew two 3-doses, decant into a 4 + a 2, sip
	 *               the 2-dose during prep, brew the 3rd potion in that emptied vial. The boss gets
	 *               a 4-dose and a 3-dose; prep gets 2 doses; one vial (10 shards, a slot) is saved.
	 */
	public static Needs compute(Ledger l, PrepProfile p, int fishTarget, int shardBuffer, int potionTarget, boolean decant)
	{
		return compute(l, p, fishTarget, shardBuffer, potionTarget, decant, false);
	}

	/**
	 * @param vialsSmash the account's empty vials disappear when the last dose is drunk (0.3.18 and
	 *                   0.3.19 logs: after "empty the small potion" a new vial still had to be made).
	 *                   Then the decant method's "brew the last potion in the emptied vial" can't
	 *                   work: one vial per brew, and nothing is drunk empty to free one.
	 */
	public static Needs compute(Ledger l, PrepProfile p, int fishTarget, int shardBuffer, int potionTarget, boolean decant,
		boolean vialsSmash)
	{
		Needs n = new Needs();
		Map<Resource, Integer> want = new EnumMap<>(Resource.class);
		for (Resource r : Resource.values())
		{
			want.put(r, 0);
		}

		for (GearSlot s : GearSlot.values())
		{
			int cur = l.tier.getOrDefault(s, 0);
			int target = p.targetTier.getOrDefault(s, 0);
			for (int t = cur + 1; t <= target; t++)
			{
				int i = t - 1;
				add(want, Resource.SHARDS, s.shards[i]);
				add(want, Resource.ORE, s.eachMaterial[i]);
				add(want, Resource.BARK, s.eachMaterial[i]);
				add(want, Resource.LINUM, s.eachMaterial[i]);
				if (s.weapon && t == 1)
				{
					add(want, Resource.FRAME, 1);
				}
				if (s.weapon && t == 3 && s.t3Component != null)
				{
					add(want, s.t3Component, 1);
				}
			}
		}

		// Potions: each brew = 1 grym + 10 dust (10 shards) and gives a 3-dose. Brews done are
		// counted in doses (held + the planned prep sips), so decanting and sipping don't confuse it;
		// an emergency sip beyond the allowance makes room for an extra brew.
		if (decant && potionTarget >= 2)
		{
			// Dose model (user, 0.3.32): the boss needs 6 doses (= 3 x (brews - 1)), and no more than
			// `potionTarget` brews (3 herbs) in a run. Two brews do it if nothing is sipped; every
			// sip (up to 3) calls for the 3rd. Brews made = doses brewed / 3 (in the bag + drunk).
			int bossDoses = 3 * (potionTarget - 1);
			n.bossDoses = bossDoses;
			n.prepDoseAllowance = 3 * potionTarget - bossDoses;
			int brewsMade = (l.potionDoses + l.dosesDrunk) / 3;
			int wanted = Math.min(potionTarget, (bossDoses + l.dosesDrunk + 2) / 3);
			n.potionsToMake = Math.max(0, wanted - brewsMade);
			n.requiredPotionsToMake = Math.max(0, Math.min(n.potionsToMake, (bossDoses + 2) / 3 - brewsMade));
		}
		else
		{
			n.prepDoseAllowance = 0;
			int brewsDone = l.potionDoses / 3;
			n.potionsToMake = Math.max(0, potionTarget - brewsDone);
			n.requiredPotionsToMake = n.potionsToMake;
		}
		add(want, Resource.GRYM, Math.max(0, n.potionsToMake - l.unfinishedPotions));
		int dustNeeded = Math.max(0, n.potionsToMake * CgIds.DUST_PER_POTION - l.dust);
		add(want, Resource.SHARDS, dustNeeded);
		// Containers: every potion item, vial, water vial or unfinished potion is one. The decant
		// method ends with 2 (the 3rd brew reuses the vial the sipped 2-dose leaves behind).
		n.vialsSmash = vialsSmash;
		n.containersTarget = decant && potionTarget >= 2 && !vialsSmash ? potionTarget - 1 : potionTarget;
		int containers = l.emptyVials + l.waterVials + l.unfinishedPotions + l.potions;
		// Vanishing vials: one per brew, but at most 2 in progress (user: 2 vials on the first trip
		// home; the 3rd is made on a later visit once those are brewed).
		n.vialsToMake = vialsSmash
			? Math.max(0, Math.min(n.potionsToMake, 2) - (l.emptyVials + l.waterVials + l.unfinishedPotions))
			: Math.max(0, Math.min(n.potionsToMake, n.containersTarget - containers));
		int reqDust = Math.max(0, n.requiredPotionsToMake * CgIds.DUST_PER_POTION - l.dust);
		int reqVials = Math.max(0, Math.min(n.vialsToMake, n.requiredPotionsToMake - (l.emptyVials + l.waterVials + l.unfinishedPotions)));
		n.potionShards = reqDust + reqVials * CgIds.VIAL_SHARD_COST;
		add(want, Resource.SHARDS, n.vialsToMake * CgIds.VIAL_SHARD_COST);

		add(want, Resource.RAW_FISH, Math.max(0, fishTarget - l.cookedFish));
		if (want.get(Resource.SHARDS) > 0)
		{
			add(want, Resource.SHARDS, shardBuffer);
		}

		for (Resource r : Resource.values())
		{
			int have = l.get(r);
			// T3 components can't be "had" in excess of what's needed; frames likewise.
			n.deficit.put(r, Math.max(0, want.get(r) - have));
			n.demand.put(r, want.get(r));
		}

		// What can be crafted right now (next tier only, greedy by priority, sharing the stock).
		int shards = l.get(Resource.SHARDS);
		int ore = l.get(Resource.ORE);
		int bark = l.get(Resource.BARK);
		int linum = l.get(Resource.LINUM);
		int frames = l.get(Resource.FRAME);
		for (GearSlot s : craftOrder(l))
		{
			int cur = l.tier.getOrDefault(s, 0);
			int target = p.targetTier.getOrDefault(s, 0);
			if (cur >= target)
			{
				continue;
			}
			// A piece goes to T3 only once every piece is T2 (0.4.9: the sim crafted T1 helm / T3 body
			// / T1 legs, worse than a T2 set for the same shards).
			if (!s.weapon && cur >= 2 && !armourAtLeast(l, p, 2))
			{
				continue;
			}
			int i = cur; // upgrading into tier cur+1
			int mat = s.eachMaterial[i];
			boolean frameOk = !(s.weapon && cur == 0) || frames > 0;
			boolean compOk = !(s.weapon && cur == 2) || l.get(s.t3Component) > 0;
			// Armour past T1 only with the potions' shards kept back (0.3.19: T2 legs + helm took the
			// shards of the 3rd potion; the user had to make it by hand).
			int usable = rank(s, cur) >= 3 ? shards - n.potionShards : shards;
			if (usable >= s.shards[i] && ore >= mat && bark >= mat && linum >= mat && frameOk && compOk)
			{
				n.craftableNow.add(s);
				shards -= s.shards[i];
				ore -= mat;
				bark -= mat;
				linum -= mat;
				if (s.weapon && cur == 0)
				{
					frames--;
				}
			}
		}
		return n;
	}

	/** A copy with one resource's deficit capped (e.g. fish limited by the slot budget). */
	public Needs withCap(Resource r, int cap)
	{
		Needs c = new Needs();
		c.deficit.putAll(deficit);
		c.demand.putAll(demand);
		c.craftableNow.addAll(craftableNow);
		c.potionsToMake = potionsToMake;
		c.vialsToMake = vialsToMake;
		c.prepDoseAllowance = prepDoseAllowance;
		c.bossDoses = bossDoses;
		c.containersTarget = containersTarget;
		c.vialsSmash = vialsSmash;
		c.potionShards = potionShards;
		c.requiredPotionsToMake = requiredPotionsToMake;
		c.deficit.put(r, Math.max(0, Math.min(get(r), cap)));
		return c;
	}

	/** A copy that only asks for these resources (everything else counted as done). */
	public Needs only(Resource... keep)
	{
		Needs c = withCap(Resource.SHARDS, get(Resource.SHARDS));
		java.util.Set<Resource> k = java.util.EnumSet.noneOf(Resource.class);
		k.addAll(java.util.Arrays.asList(keep));
		for (Resource r : Resource.values())
		{
			if (!k.contains(r))
			{
				c.deficit.put(r, 0);
			}
		}
		return c;
	}

	public boolean gatheringDone()
	{
		return deficit.values().stream().allMatch(v -> v == 0);
	}

	/** A copy with r's deficit set to v (e.g. the full fish need back on top of other caps). */
	public Needs withDeficit(Resource r, int v)
	{
		Needs c = withCap(r, Integer.MAX_VALUE);
		c.deficit.put(r, Math.max(0, v));
		return c;
	}

	/** Done apart from {@code r} (a nice-to-have that's not worth a search). */
	public boolean gatheringDoneExcept(Resource r)
	{
		return deficit.entrySet().stream().allMatch(e -> e.getKey() == r || e.getValue() == 0);
	}

	public int get(Resource r)
	{
		return deficit.getOrDefault(r, 0);
	}

	private static void add(Map<Resource, Integer> m, Resource r, int v)
	{
		m.merge(r, v, Integer::sum);
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		deficit.forEach((r, v) ->
		{
			if (v > 0)
			{
				sb.append(r.label).append(' ').append(v).append(", ");
			}
		});
		return sb.length() == 0 ? "nothing missing" : sb.substring(0, sb.length() - 2);
	}
}
