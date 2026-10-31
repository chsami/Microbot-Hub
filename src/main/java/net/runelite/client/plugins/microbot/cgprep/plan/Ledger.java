package net.runelite.client.plugins.microbot.cgprep.plan;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.GearSlot;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Snapshot of everything the player has right now (inventory + equipment). Pure observation: it's
 * rebuilt every planning pass instead of being tracked incrementally, so it can never drift from
 * the game. Build it on the script thread, never on the client thread.
 */
public final class Ledger
{
	public final Map<Resource, Integer> have = new EnumMap<>(Resource.class);
	public final Map<GearSlot, Integer> tier = new EnumMap<>(GearSlot.class);
	public int dust;
	public int cookedFish;
	public int emptyVials;
	public int waterVials;
	public int unfinishedPotions;
	public int potions;
	public int potionDoses;
	/** Potion items by dose: doseCounts[d] = how many d-dose Egniols (1..4) are in the bag. */
	public final int[] doseCounts = new int[5];
	/** Doses drunk so far this run (tracked by the script; the ledger can't see them). */
	public int dosesDrunk;
	public boolean pickaxe;
	public boolean axe;
	public boolean harpoon;
	public boolean pestle;
	public int freeSlots;
	/** Any crafted armour piece sitting in the inventory instead of being worn. */
	public boolean armourInInventory;
	public boolean weaponEquipped;
	/** Some of our crafted gear is on the floor nearby (counted in tier, still to pick up). */
	public boolean gearOnGround;

	public static Ledger observe(CgMode m)
	{
		Ledger l = new Ledger();
		for (GearSlot s : GearSlot.values())
		{
			for (int id : s.ids(m))
			{
				if (s.weapon && Rs2Equipment.isWearing(id))
				{
					l.weaponEquipped = true;
				}
				if (!s.weapon && Rs2Inventory.contains(id))
				{
					l.armourInInventory = true;
				}
			}
		}
		for (Resource r : Resource.values())
		{
			int id = m.itemFor(r);
			l.have.put(r, id <= 0 ? 0 : Rs2Inventory.itemQuantity(id));
		}
		for (GearSlot s : GearSlot.values())
		{
			int[] ids = s.ids(m);
			int best = 0;
			for (int t = ids.length - 1; t >= 0; t--)
			{
				if (Rs2Inventory.contains(ids[t]) || Rs2Equipment.isWearing(ids[t]))
				{
					best = t + 1;
					break;
				}
			}
			l.tier.put(s, best);
		}
		l.dust = Rs2Inventory.itemQuantity(m.dust);
		l.cookedFish = Rs2Inventory.count(CgIds.PADDLEFISH);
		l.emptyVials = Rs2Inventory.count(m.vial) + Rs2Inventory.count(CgIds.VIAL_EMPTY_ALT);
		l.waterVials = Rs2Inventory.count(CgIds.VIAL_WATER);
		l.unfinishedPotions = Rs2Inventory.count(CgIds.POTION_UNF);
		int doses = 0;
		int pots = 0;
		int[] egniol = CgIds.EGNIOL_ALL; // 4,3,2,1 doses
		for (int i = 0; i < egniol.length; i++)
		{
			int c = Rs2Inventory.count(egniol[i]);
			l.doseCounts[4 - i] = c;
			pots += c;
			doses += c * (4 - i);
		}
		l.potions = pots;
		l.potionDoses = doses;
		l.pickaxe = Rs2Inventory.contains(m.pickaxe);
		l.axe = Rs2Inventory.contains(m.axe);
		l.harpoon = Rs2Inventory.contains(m.harpoon);
		l.pestle = Rs2Inventory.contains(CgIds.PESTLE);
		l.freeSlots = Rs2Inventory.emptySlotCount();
		return l;
	}

	/** A copy for "what if" planning (the lookahead). */
	public Ledger copy()
	{
		Ledger c = new Ledger();
		c.have.putAll(have);
		c.tier.putAll(tier);
		c.dust = dust;
		c.cookedFish = cookedFish;
		c.emptyVials = emptyVials;
		c.waterVials = waterVials;
		c.unfinishedPotions = unfinishedPotions;
		c.potions = potions;
		c.potionDoses = potionDoses;
		System.arraycopy(doseCounts, 0, c.doseCounts, 0, doseCounts.length);
		c.dosesDrunk = dosesDrunk;
		c.pickaxe = pickaxe;
		c.axe = axe;
		c.harpoon = harpoon;
		c.pestle = pestle;
		c.freeSlots = freeSlots;
		c.armourInInventory = armourInInventory;
		c.weaponEquipped = weaponEquipped;
		c.gearOnGround = gearOnGround;
		return c;
	}

	/** What the planner's choice depends on in the bag: resource counts, vials/potions, free slots. */
	public String signature()
	{
		return have + "|" + dust + "|" + emptyVials + "|" + waterVials + "|" + unfinishedPotions + "|" + potionDoses + "|" + freeSlots;
	}

	public int get(Resource r)
	{
		return have.getOrDefault(r, 0);
	}

	public String compact()
	{
		return "shards=" + get(Resource.SHARDS) + " ore=" + get(Resource.ORE) + " bark=" + get(Resource.BARK)
			+ " linum=" + get(Resource.LINUM) + " grym=" + get(Resource.GRYM) + " frames=" + get(Resource.FRAME)
			+ " fishRaw=" + get(Resource.RAW_FISH) + " fish=" + cookedFish + " pots=" + potions
			+ " gear=" + tier;
	}
}
