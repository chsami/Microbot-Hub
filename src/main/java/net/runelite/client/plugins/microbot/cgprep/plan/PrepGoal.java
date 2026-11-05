package net.runelite.client.plugins.microbot.cgprep.plan;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.cgprep.data.GearSlot;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;

/** One decision from the planner: what to do next, and why (shown in overlay + logs). */
public final class PrepGoal
{
	public enum Type
	{
		/** Grab tools from the start-room storage (if the run doesn't start with them). */
		GET_TOOLS,
		GATHER,
		FIGHT,
		LOOT,
		EXPLORE,
		RETURN_TO_START,
		CRAFT_GEAR,
		/** Make empty vials at the bowl early, to fill them at a fishing spot later (wiki tip). */
		MAKE_VIALS,
		/** Drop a tool we're done with to free a slot (resource = what it gathers; SHARDS = the pestle and mortar). */
		DROP_TOOL,
		/** Drop leftover material nothing still to be made needs (user, 0.3.26: 9 linum of 7). */
		DROP_SURPLUS,
		/** Pestle on a corrupted spike: 80 shards, one slot freed. */
		CRUSH_SPIKE,
		BREW,
		/** Two part-full Egniols: pour the smaller into the bigger (decant method). */
		DECANT,
		/** Drink the smallest Egniol (to empty its vial for the last brew). */
		DRINK,
		/** Use empty vials on a fishing spot (live or fished out) to fill them; no pump trips (user). */
		FILL_VIALS,
		/** Sing a teleport crystal at the bowl (50 shards) for a quick trip home later. */
		MAKE_CRYSTAL,
		COOK,
		EQUIP,
		HANDOVER
	}

	public final Type type;
	public final Resource resource;
	public final Monster monster;
	public final GearSlot slot;
	public final int[] room;
	public final String why;
	/** GATHER: this particular node (a detour picked on the way), or null = the nearest one. */
	public WorldPoint at;
	/** GATHER: pick the item up off the floor at {@link #at} (not a node). */
	public boolean groundItem;
	/** RETURN_TO_START only: the last trip home (loop 2 done / time up), the one the teleport crystal is for. */
	public boolean finalTrip;
	/** RETURN_TO_START only: use the teleport crystal now, whatever the distance. */
	public boolean teleportNow;
	/**
	 * An inventory chore (crush the spike, drop a finished tool, decant, a brew step) to do right
	 * after this goal's next walking click, while running (user, 0.3.15). Null = none.
	 */
	public PrepGoal chore;

	private PrepGoal(Type type, Resource resource, Monster monster, GearSlot slot, int[] room, String why)
	{
		this.type = type;
		this.resource = resource;
		this.monster = monster;
		this.slot = slot;
		this.room = room;
		this.why = why;
	}

	public static PrepGoal of(Type t, String why)
	{
		return new PrepGoal(t, null, null, null, null, why);
	}

	/** The final trip home: walking or, from 2+ rooms away, the teleport crystal (user). */
	public static PrepGoal finalTripHome(String why)
	{
		PrepGoal g = new PrepGoal(Type.RETURN_TO_START, null, null, null, null, why);
		g.finalTrip = true;
		return g;
	}

	/** Teleport home now (full bag on the ring, fish still short, a spot known near home). */
	public static PrepGoal teleportHome(String why)
	{
		PrepGoal g = new PrepGoal(Type.RETURN_TO_START, null, null, null, null, why);
		g.teleportNow = true;
		return g;
	}

	public static PrepGoal gather(Resource r, String why)
	{
		return new PrepGoal(Type.GATHER, r, null, null, null, why);
	}

	public static PrepGoal gatherAt(Resource r, WorldPoint at, String why)
	{
		PrepGoal g = new PrepGoal(Type.GATHER, r, null, null, null, why);
		g.at = at;
		return g;
	}

	/** Pick up raw paddlefish lying on the floor at {@code at} (1.6.0 top-up). */
	public static PrepGoal pickUpFish(WorldPoint at, String why)
	{
		PrepGoal g = new PrepGoal(Type.GATHER, Resource.RAW_FISH, null, null, null, why);
		g.at = at;
		g.groundItem = true;
		return g;
	}

	public static PrepGoal fight(Monster m, String why)
	{
		return new PrepGoal(Type.FIGHT, null, m, null, null, why);
	}

	public static PrepGoal craft(GearSlot s, String why)
	{
		return new PrepGoal(Type.CRAFT_GEAR, null, null, s, null, why);
	}

	public static PrepGoal dropSurplus(Resource r, String why)
	{
		return new PrepGoal(Type.DROP_SURPLUS, r, null, null, null, why);
	}

	public static PrepGoal dropTool(Resource gathers, String why)
	{
		return new PrepGoal(Type.DROP_TOOL, gathers, null, null, null, why);
	}

	public static PrepGoal explore(int[] room, String why)
	{
		return new PrepGoal(Type.EXPLORE, null, null, null, room, why);
	}

	/** The goal in a few words for the overlay ("Gather Ore", "Fight the Dragon", "Head home"). */
	public String headline()
	{
		switch (type)
		{
			case GATHER:
				return "Gather " + (resource == null ? "resources" : resource.label);
			case FIGHT:
				return monster == null ? "Fight" : monster == Monster.WEAK ? "Kill a weak monster (shards)"
					: monster == Monster.STRONG ? "Kill a medium monster (shards)" : "Fight the " + pretty(monster.name());
			case LOOT:
				return "Loot";
			case EXPLORE:
				return "Explore";
			case RETURN_TO_START:
				return finalTrip ? "Head home to finish" : "Head home";
			case CRAFT_GEAR:
				return "Craft " + (slot == null ? "gear" : pretty(slot.name()));
			case MAKE_VIALS:
				return "Make vials";
			case FILL_VIALS:
				return "Fill vials";
			case BREW:
			case DECANT:
			case DRINK:
				return "Potions";
			case COOK:
				return "Cook fish";
			case EQUIP:
				return "Equip gear";
			case DROP_TOOL:
			case DROP_SURPLUS:
				return "Make room";
			case CRUSH_SPIKE:
				return "Crush the spike";
			case MAKE_CRYSTAL:
				return "Make a teleport crystal";
			case GET_TOOLS:
				return "Get tools";
			case HANDOVER:
				return "Prep done";
			default:
				return type.name();
		}
	}

	private static String pretty(String enumName)
	{
		String s = enumName.toLowerCase().replace('_', ' ');
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	@Override
	public String toString()
	{
		String what = resource != null ? " " + resource.label
			: monster != null ? " " + monster
			: slot != null ? " " + slot
			: room != null ? " room " + room[0] + "," + room[1] : "";
		return type + what + " (" + why + ")" + (chore == null ? "" : " + on the move: " + chore.type + (chore.resource != null ? " " + chore.resource.label : ""));
	}
}
