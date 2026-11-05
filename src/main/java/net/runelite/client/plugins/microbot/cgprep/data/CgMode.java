package net.runelite.client.plugins.microbot.cgprep.data;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

/**
 * Everything that differs between the normal (crystal) and Corrupted Gauntlet. Normal mode exists
 * so prep logic can be practised against weaker monsters with a 10-minute timer.
 *
 * All IDs come from the client's gameval tables (the _HM suffix = "hard mode" = Corrupted).
 */
public enum CgMode
{
	CORRUPTED(
		450, // 7:30 prep
		// resources
		ItemID.GAUNTLET_CRYSTAL_SHARD_HM, ItemID.GAUNTLET_CRYSTAL_SHARD_CRUSHED_HM,
		ItemID.GAUNTLET_ORE_HM, ItemID.GAUNTLET_BARK_HM, ItemID.GAUNTLET_FIBRE_HM, ItemID.GAUNTLET_HERB_HM,
		ItemID.GAUNTLET_GENERIC_COMPONENT_HM, ItemID.GAUNTLET_RANGED_COMPONENT_HM, ItemID.GAUNTLET_MAGIC_COMPONENT_HM, ItemID.GAUNTLET_MELEE_COMPONENT_HM,
		// Recon (Corrupted run): the bowl makes the SHARED vial 23879, not the _HM one.
		ItemID.GAUNTLET_VIAL_EMPTY, ItemID.GAUNTLET_TELEPORT_CRYSTAL_HM,
		// tools
		ItemID.GAUNTLET_PICKAXE_HM, ItemID.GAUNTLET_AXE_HM, ItemID.GAUNTLET_HARPOON_HM,
		// gear: helm, body, legs, bow, staff, halberd x T1..T3
		new int[]{ItemID.GAUNTLET_HELMET_T1_HM, ItemID.GAUNTLET_HELMET_T2_HM, ItemID.GAUNTLET_HELMET_T3_HM},
		new int[]{ItemID.GAUNTLET_CHESTPLATE_T1_HM, ItemID.GAUNTLET_CHESTPLATE_T2_HM, ItemID.GAUNTLET_CHESTPLATE_T3_HM},
		new int[]{ItemID.GAUNTLET_PLATELEGS_T1_HM, ItemID.GAUNTLET_PLATELEGS_T2_HM, ItemID.GAUNTLET_PLATELEGS_T3_HM},
		new int[]{ItemID.GAUNTLET_RANGED_T1_HM, ItemID.GAUNTLET_RANGED_T2_HM, ItemID.GAUNTLET_RANGED_T3_HM},
		new int[]{ItemID.GAUNTLET_MAGIC_T1_HM, ItemID.GAUNTLET_MAGIC_T2_HM, ItemID.GAUNTLET_MAGIC_T3_HM},
		new int[]{ItemID.GAUNTLET_MELEE_T1_HM, ItemID.GAUNTLET_MELEE_T2_HM, ItemID.GAUNTLET_MELEE_T3_HM},
		// objects
		ObjectID.GAUNTLET_SINGING_BOWL_HM, ObjectID.GAUNTLET_RANGE_HM, ObjectID.GAUNTLET_SINK_HM, ObjectID.GAUNTLET_TOOLS_HM,
		ObjectID.GAUNTLET_BLOCKADE_HM,
		ObjectID.GAUNTLET_ROCK_HM, ObjectID.GAUNTLET_TREE_HM, ObjectID.GAUNTLET_FIBRE_HM, ObjectID.GAUNTLET_HERB_HM, ObjectID.GAUNTLET_POND_HM,
		ObjectID.GAUNTLET_ROCK_DEPLETED_HM, ObjectID.GAUNTLET_TREE_DEPLETED_HM, ObjectID.GAUNTLET_FIBRE_DEPLETED_HM,
		ObjectID.GAUNTLET_HERB_DEPLETED_HM, ObjectID.GAUNTLET_POND_DEPLETED_HM,
		// npcs
		new int[]{NpcID.CRYSTAL_RAT_HM, NpcID.CRYSTAL_SPIDER_HM, NpcID.CRYSTAL_BAT_HM},
		new int[]{NpcID.CRYSTAL_UNICORN_HM, NpcID.CRYSTAL_SCORPION_HM, NpcID.CRYSTAL_WOLF_HM},
		NpcID.CRYSTAL_BEAR_HM, NpcID.CRYSTAL_DARK_BEAST_HM, NpcID.CRYSTAL_DRAGON_HM),

	NORMAL(
		600, // 10:00 prep
		ItemID.GAUNTLET_CRYSTAL_SHARD, ItemID.GAUNTLET_CRYSTAL_SHARD_CRUSHED,
		ItemID.GAUNTLET_ORE, ItemID.GAUNTLET_BARK, ItemID.GAUNTLET_FIBRE, ItemID.GAUNTLET_HERB,
		ItemID.GAUNTLET_GENERIC_COMPONENT, ItemID.GAUNTLET_RANGED_COMPONENT, ItemID.GAUNTLET_MAGIC_COMPONENT, ItemID.GAUNTLET_MELEE_COMPONENT,
		ItemID.GAUNTLET_VIAL_EMPTY, ItemID.GAUNTLET_TELEPORT_CRYSTAL,
		ItemID.GAUNTLET_PICKAXE, ItemID.GAUNTLET_AXE, ItemID.GAUNTLET_HARPOON,
		new int[]{ItemID.GAUNTLET_HELMET_T1, ItemID.GAUNTLET_HELMET_T2, ItemID.GAUNTLET_HELMET_T3},
		new int[]{ItemID.GAUNTLET_CHESTPLATE_T1, ItemID.GAUNTLET_CHESTPLATE_T2, ItemID.GAUNTLET_CHESTPLATE_T3},
		new int[]{ItemID.GAUNTLET_PLATELEGS_T1, ItemID.GAUNTLET_PLATELEGS_T2, ItemID.GAUNTLET_PLATELEGS_T3},
		new int[]{ItemID.GAUNTLET_RANGED_T1, ItemID.GAUNTLET_RANGED_T2, ItemID.GAUNTLET_RANGED_T3},
		new int[]{ItemID.GAUNTLET_MAGIC_T1, ItemID.GAUNTLET_MAGIC_T2, ItemID.GAUNTLET_MAGIC_T3},
		new int[]{ItemID.GAUNTLET_MELEE_T1, ItemID.GAUNTLET_MELEE_T2, ItemID.GAUNTLET_MELEE_T3},
		ObjectID.GAUNTLET_SINGING_BOWL, ObjectID.GAUNTLET_RANGE, ObjectID.GAUNTLET_SINK, ObjectID.GAUNTLET_TOOLS,
		ObjectID.GAUNTLET_BLOCKADE,
		ObjectID.GAUNTLET_ROCK, ObjectID.GAUNTLET_TREE, ObjectID.GAUNTLET_FIBRE, ObjectID.GAUNTLET_HERB, ObjectID.GAUNTLET_POND,
		ObjectID.GAUNTLET_ROCK_DEPLETED, ObjectID.GAUNTLET_TREE_DEPLETED, ObjectID.GAUNTLET_FIBRE_DEPLETED,
		ObjectID.GAUNTLET_HERB_DEPLETED, ObjectID.GAUNTLET_POND_DEPLETED,
		new int[]{NpcID.CRYSTAL_RAT, NpcID.CRYSTAL_SPIDER, NpcID.CRYSTAL_BAT},
		new int[]{NpcID.CRYSTAL_UNICORN, NpcID.CRYSTAL_SCORPION, NpcID.CRYSTAL_WOLF},
		NpcID.CRYSTAL_BEAR, NpcID.CRYSTAL_DARK_BEAST, NpcID.CRYSTAL_DRAGON);

	public final int prepSeconds;

	public final int shard;
	public final int dust;
	public final int ore;
	public final int bark;
	public final int linum;
	public final int grym;
	/** Weapon frame. */
	public final int frame;
	/** Bowstring (Dark Beast). */
	public final int bowstring;
	/** Orb (Dragon). */
	public final int orb;
	/** Spike (Bear). */
	public final int spike;
	public final int vial;
	public final int teleportCrystal;

	public final int pickaxe;
	public final int axe;
	public final int harpoon;

	public final int[] helm;
	public final int[] body;
	public final int[] legs;
	public final int[] bow;
	public final int[] staff;
	public final int[] halberd;

	public final int objBowl;
	public final int objRange;
	public final int objSink;
	public final int objTools;
	public final int objBossBarrier;

	public final int objRock;
	public final int objTree;
	public final int objLinum;
	public final int objHerb;
	public final int objPond;
	public final int objRockDepleted;
	public final int objTreeDepleted;
	public final int objLinumDepleted;
	public final int objHerbDepleted;
	public final int objPondDepleted;

	public final int[] weakMonsters;
	public final int[] strongMonsters;
	public final int bear;
	public final int darkBeast;
	public final int dragon;

	CgMode(int prepSeconds,
		int shard, int dust, int ore, int bark, int linum, int grym,
		int frame, int bowstring, int orb, int spike, int vial, int teleportCrystal,
		int pickaxe, int axe, int harpoon,
		int[] helm, int[] body, int[] legs, int[] bow, int[] staff, int[] halberd,
		int objBowl, int objRange, int objSink, int objTools, int objBossBarrier,
		int objRock, int objTree, int objLinum, int objHerb, int objPond,
		int objRockDepleted, int objTreeDepleted, int objLinumDepleted, int objHerbDepleted, int objPondDepleted,
		int[] weakMonsters, int[] strongMonsters, int bear, int darkBeast, int dragon)
	{
		this.prepSeconds = prepSeconds;
		this.shard = shard;
		this.dust = dust;
		this.ore = ore;
		this.bark = bark;
		this.linum = linum;
		this.grym = grym;
		this.frame = frame;
		this.bowstring = bowstring;
		this.orb = orb;
		this.spike = spike;
		this.vial = vial;
		this.teleportCrystal = teleportCrystal;
		this.pickaxe = pickaxe;
		this.axe = axe;
		this.harpoon = harpoon;
		this.helm = helm;
		this.body = body;
		this.legs = legs;
		this.bow = bow;
		this.staff = staff;
		this.halberd = halberd;
		this.objBowl = objBowl;
		this.objRange = objRange;
		this.objSink = objSink;
		this.objTools = objTools;
		this.objBossBarrier = objBossBarrier;
		this.objRock = objRock;
		this.objTree = objTree;
		this.objLinum = objLinum;
		this.objHerb = objHerb;
		this.objPond = objPond;
		this.objRockDepleted = objRockDepleted;
		this.objTreeDepleted = objTreeDepleted;
		this.objLinumDepleted = objLinumDepleted;
		this.objHerbDepleted = objHerbDepleted;
		this.objPondDepleted = objPondDepleted;
		this.weakMonsters = weakMonsters;
		this.strongMonsters = strongMonsters;
		this.bear = bear;
		this.darkBeast = darkBeast;
		this.dragon = dragon;
	}

	/** Option on the lobby entrance (ObjectID.GAUNTLET_ENTRANCE 37340), recon-confirmed for both modes. */
	public String entranceOption()
	{
		return this == CORRUPTED ? "Enter-corrupted" : "Enter";
	}

	/** Gather node object id for a resource, or -1 if the resource doesn't come from a node. */
	public int nodeFor(Resource r)
	{
		switch (r)
		{
			case ORE:
				return objRock;
			case BARK:
				return objTree;
			case LINUM:
				return objLinum;
			case GRYM:
				return objHerb;
			case RAW_FISH:
				return objPond;
			default:
				return -1;
		}
	}

	/** Inventory item id for a resource, or -1 for the ones that aren't a single item. */
	public int itemFor(Resource r)
	{
		switch (r)
		{
			case SHARDS:
				return shard;
			case ORE:
				return ore;
			case BARK:
				return bark;
			case LINUM:
				return linum;
			case GRYM:
				return grym;
			case RAW_FISH:
				return CgIds.RAW_PADDLEFISH;
			case FRAME:
				return frame;
			case BOWSTRING:
				return bowstring;
			case ORB:
				return orb;
			case SPIKE:
				return spike;
			default:
				return -1;
		}
	}
}
