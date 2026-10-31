package net.runelite.client.plugins.microbot.cgprep.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;

/**
 * Where to go next to fill the shopping list: a known node, or a new room to light.
 *
 * Every option is scored as a RATE: expected value / expected game ticks.
 *  - Value is in "needed node-equivalents": one node of something we still need = 1. An
 *    unexplored room is worth the nodes it probably holds of what we still need (RoomPriors, by
 *    ring), minus what known live nodes already cover, plus monsters when a frame/shards are
 *    missing, plus demi-bosses (outer ring only) when a T3 component is missing.
 *  - Cost = walking (room steps through already-lit rooms) + lighting the doorway + the expected
 *    harvesting time of that value, so "explore next door" and "walk to a known node" compete
 *    fairly.
 *
 * The wiki route (The_Gauntlet/Strategies) falls out of this instead of being hard-coded:
 *  - Opening: frame + ~100 shards from weak monsters in rooms next to the start (a C around it,
 *    since the boss room blocks the middle). Encouraged by an opening bonus on start-adjacent rooms.
 *  - Materials: the inner/middle rings are richest in ore, the outer ring is poor in ore. Priors
 *    steer there while ore/bark/linum/grym/fish are still needed.
 *  - Lap: once only components (and shards) are missing, outer-ring rooms are the only ones with
 *    value (demi-bosses), and travel cost makes it sweep them neighbour to neighbour.
 *  - Never wanders off with no time to get back: options that don't fit the remaining budget
 *    (including the return trip: a teleport crystal or the walk) are dropped.
 *
 * Pure logic: fed through {@link MapView}, so it's testable offline against recorded maps.
 */
public final class ExplorePlanner
{
	/** What the planner needs to know about the map. GauntletMaze implements it. */
	public interface MapView
	{
		boolean found(int x, int z);

		int roomX();

		int roomZ();

		int startX();

		int startZ();

		/** Known, not-depleted nodes of this resource anywhere on the map. */
		int liveNodes(Resource r);

		/** Known live nodes of r within this many walking tiles (1.7.0). */
		default int liveNodesWithin(Resource r, int tiles)
		{
			return liveNodes(r);
		}

		/** Tiles from the player to the nearest known live node of r, or -1. Optionally skip outer-ring nodes. */
		int tilesToNearestLiveNode(Resource r, boolean includeOuter);

		/** True if lighting this room failed recently (unreachable doorway): skip it for now. */
		boolean exploreBlocked(int x, int z);
	}

	public enum Kind
	{
		GATHER, EXPLORE
	}

	public static final class Option
	{
		public final Kind kind;
		public final Resource resource;
		public final int x;
		public final int z;
		public final double value;
		public final double costTicks;
		/** GATHER only: walking tiles to the node (-1 otherwise). */
		public int tiles = -1;
		public final String detail;

		Option(Kind kind, Resource resource, int x, int z, double value, double costTicks, String detail)
		{
			this.kind = kind;
			this.resource = resource;
			this.x = x;
			this.z = z;
			this.value = value;
			this.costTicks = costTicks;
			this.detail = detail;
		}

		public double rate()
		{
			return costTicks <= 0 ? value : value / costTicks;
		}

		@Override
		public String toString()
		{
			String what = kind == Kind.GATHER ? "gather " + resource.label : "explore " + x + "," + z;
			return String.format("%s v=%.2f c=%.0ft r=%.3f%s", what, value, costTicks, rate(),
				detail.isEmpty() ? "" : " [" + detail + "]");
		}
	}

	/** What's still wanted beyond node resources. */
	public static final class Wants
	{
		public boolean frame;
		public int shards;
		public boolean bowstring;
		public boolean orb;
		/** Opening phase (no weapon yet): favour the rooms around the start for monsters. */
		public boolean opening;
		/** Ticks we can still spend before we must be heading home. */
		public int budgetTicks = Integer.MAX_VALUE;
		/** Ticks to get home from far away (teleport crystal ~5, else walking). */
		public boolean hasTeleport;
		/** Free inventory slots: fish/grym need one each, so no point going for them with none. */
		public int freeSlots = 28;
		/** Loop 2 demi hunt: the outer ring is allowed straight away (with resources), not last. */
		public boolean lap;
		/** Any demi-boss type is useful (an upgrade is still missing). */
		public boolean anyDemi;
		/** Known nodes further than this aren't worth the walk (loop 1 stays near home). */
		public int maxGatherTiles = Integer.MAX_VALUE;
		/** Fewer than 4 fish (raw + cooked) in the bag: the first fishing spot comes early. */
		public boolean foodLow;
		/** No potion, unfinished potion or grym yet: the first grym comes early. */
		public boolean firstPotion;
	}

	static boolean stacks(Resource r)
	{
		return r == Resource.ORE || r == Resource.BARK || r == Resource.LINUM;
	}

	/**
	 * Where we're willing to go, in order. The wiki route (and the user's rule) is: loop the rooms
	 * around the start first, then the middle ring for materials, and the outer ring (stronger
	 * monsters, far from home, little ore) only for the demi-boss lap or when nothing closer is
	 * left. Outer rooms next to where we stand may still be LIT from the doorway ("peeked") to spot
	 * demi-bosses for later, without walking in.
	 */
	enum Area
	{
		/** Rooms next to the start + the inner ring around the boss. */
		HOME,
		/** HOME + the middle ring. */
		NEAR,
		/** Everything. */
		ALL
	}

	/** Known material nodes further than this (walking tiles) don't count against exploring for more. */
	static final int KNOWN_NODE_TILES = 60;
	static final double ROOM_STEP_TICKS = 8;     // 16 tiles at run speed
	static final double LIGHT_TICKS = 4;         // walk to the doorway node + "Light"
	static final double HARVEST_ITEM_TICKS = 3;  // recon: one item per 3 ticks
	static final double HARVEST_START_TICKS = 2;
	static final double TILES_PER_TICK = 2;
	static final double OPENING_BONUS = 0.25;
	static final double FRONTIER_BONUS = 0.05;   // per unexplored neighbour it would reveal
	/**
	 * A demi-boss kill while an upgrade is still missing, in node-equivalents. Any type counts
	 * (user): a Bear's spike is 80 shards, a repeat type never duplicates a drop, so the 2nd/3rd
	 * kill always yields what's missing. Raised from 3: the 0.3.8/0.3.10 runs kept choosing fish
	 * over the outer ring and ran out of time without the bowstring.
	 */
	static final double DEMI_VALUE = 6.0;
	static final double FRAME_VALUE = 1.0;
	/**
	 * Fish while armour materials are still missing (user, 0.3.11: no linum was ever gathered, no
	 * armour, while 15 fish filled the bag). Fish come in later trips; ore/bark/linum come first.
	 */
	public static double FISH_EARLY_VALUE = 0.85;

	static double resourceWeight(Resource r, Needs n)
	{
		return resourceWeight(r, n, null);
	}

	static double resourceWeight(Resource r, Needs n, Wants w)
	{
		if (r == Resource.GRYM && w != null && w.firstPotion)
		{
			return FIRST_GRYM_VALUE;
		}
		if (r != Resource.RAW_FISH)
		{
			return 1.0;
		}
		if (w != null && w.foodLow)
		{
			return FOOD_LOW_VALUE;
		}
		return n.get(Resource.ORE) > 0 || n.get(Resource.BARK) > 0 || n.get(Resource.LINUM) > 0 ? FISH_EARLY_VALUE : 1.0;
	}

	/**
	 * Loop 1 without a frame: nothing goes home until a weak monster drops one (0.3.15: no weak
	 * monster for the first 2:27 while it gathered everything nearby, and loop 1 took 3:03). The
	 * monster rooms come first; gathering on the way still happens, at a discount.
	 */
	public static double FRAME_FIRST_VALUE = 3.0;
	public static double NO_FRAME_GATHER = 0.7;
	/** A needed node this close (walking tiles) is taken on the way, at this value multiplier. */
	public static int ON_THE_WAY_TILES = 8;
	public static double ON_THE_WAY_BONUS = 2.5;
	/** Walking tiles that count as "in this room" for loop-1 gathering without a frame. */
	public static int SAME_ROOM_TILES = 12;

	static double frameFirst(Wants w)
	{
		return w != null && w.opening && w.frame ? NO_FRAME_GATHER : 1.0;
	}

	/** Loop 1: share of the walk back to the start charged to a room, per room step from it. */
	public static double HOME_PULL = 0.5;
	/** First grym leaf (an early potion for the demi-bosses). */
	public static double FIRST_GRYM_VALUE = 1.6;
	/** First fishing spot (food for the demi-bosses). */
	public static double FOOD_LOW_VALUE = 1.5;
	/**
	 * Uncertain value is worth less than a node we can see. Tuned offline (ExploreSim, 5 recorded
	 * maps x 40 fills + 300 prior maps): at 1.0 it over-explores (no better than greedy); at <= 0.6
	 * it harvests what's known first and picks WHICH room to light by value: 98 s vs 108 s on the
	 * recorded maps, equal on random ones. A Monte Carlo rollout planner was also tried and lost.
	 */
	public static double EXPLORE_DISCOUNT = 0.6;

	private static final int N = 7;

	public static int nodesNeeded(Needs n, Resource r)
	{
		int need = n.get(r);
		int y = yieldPerNode(r);
		return need <= 0 ? 0 : (need + y - 1) / y;
	}

	public static int yieldPerNode(Resource r)
	{
		return r == Resource.RAW_FISH ? 4 : r == Resource.GRYM ? 1 : 3;
	}

	private static final Resource[] NODE_RES = {Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.RAW_FISH};

	/** All options with value, best first, from the smallest area that has any. */
	public List<Option> rank(MapView m, Needs n, Wants w)
	{
		if (m.roomX() < 0)
		{
			return new ArrayList<>();
		}
		boolean lap = w.lap || (!anyNodeNeeds(n) && (w.bowstring || w.orb));
		Area[] tiers = lap ? new Area[]{Area.ALL}
			: w.opening ? new Area[]{Area.HOME, Area.NEAR, Area.ALL}
			: new Area[]{Area.NEAR, Area.ALL};
		List<Option> out = new ArrayList<>();
		for (Area a : tiers)
		{
			out = rank(m, n, w, a);
			if (!out.isEmpty())
			{
				return out;
			}
		}
		return out;
	}

	private static boolean anyNodeNeeds(Needs n)
	{
		for (Resource r : NODE_RES)
		{
			if (n.get(r) > 0)
			{
				return true;
			}
		}
		return false;
	}

	private boolean inArea(Area a, MapView m, int x, int z)
	{
		RoomPriors.Ring ring = RoomPriors.ring(x, z);
		switch (a)
		{
			case HOME:
				return ring == RoomPriors.Ring.INNER || Math.abs(x - m.startX()) + Math.abs(z - m.startZ()) == 1;
			case NEAR:
				return ring != RoomPriors.Ring.OUTER || Math.abs(x - m.startX()) + Math.abs(z - m.startZ()) == 1;
			default:
				return true;
		}
	}

	List<Option> rank(MapView m, Needs n, Wants w, Area area)
	{
		List<Option> out = new ArrayList<>();
		int[][] steps = roomSteps(m);
		boolean includeOuterNodes = area == Area.ALL;

		// Known nodes.
		for (Resource r : NODE_RES)
		{
			int need = n.get(r);
			int tiles = m.tilesToNearestLiveNode(r, includeOuterNodes);
			if (need <= 0 || tiles < 0 || tiles > w.maxGatherTiles || (!stacks(r) && w.freeSlots <= 0))
			{
				continue;
			}
			int items = Math.min(need, yieldPerNode(r));
			if (!stacks(r))
			{
				items = Math.min(items, w.freeSlots);
			}
			if (r == Resource.RAW_FISH && items < 3 && tiles > ON_THE_WAY_TILES)
			{
				continue; // never walk anywhere for 1-2 fish (user); only if the spot is right here
			}
			double cost = tiles / TILES_PER_TICK + HARVEST_START_TICKS + items * HARVEST_ITEM_TICKS;
			// Worth what it gives: a fishing spot for 1 fish is a quarter of one for 4 (0.3.15 ran
			// back for a single fish because every node counted as a whole one).
			// Before the frame, what's in the room we're in is taken (user: "just have them, drop the
			// tool, one less thing to worry about"); only detours further out are discounted.
			double value = resourceWeight(r, n, w) * items / yieldPerNode(r) * (tiles <= SAME_ROOM_TILES ? 1.0 : frameFirst(w));
			// Right by us (we're running past it): take it now rather than come back for it (user,
			// 0.3.17 ran past fishing spots on the demi lap and backtracked over a minute for fish).
			// Fish only reach here in a proper batch within the slot budget (fishCap).
			if (tiles <= ON_THE_WAY_TILES)
			{
				value *= ON_THE_WAY_BONUS;
			}
			if (fits(cost, m, w))
			{
				Option o = new Option(Kind.GATHER, r, -1, -1, value, cost, tiles + " tiles, " + items + " items");
				o.tiles = tiles;
				out.add(o);
			}
		}

		// Frontier rooms: not lit, not the boss room, next to a lit room we can walk to.
		for (int z = 0; z < N; z++)
		{
			for (int x = 0; x < N; x++)
			{
				if (m.found(x, z) || RoomPriors.ring(x, z) == RoomPriors.Ring.BOSS || m.exploreBlocked(x, z))
				{
					continue;
				}
				int via = bestNeighbourSteps(steps, x, z);
				if (via < 0)
				{
					continue;
				}
				Option o;
				if (inArea(area, m, x, z))
				{
					o = scoreRoom(m, n, w, x, z, via);
				}
				else if (via == 0 && RoomPriors.ring(x, z) == RoomPriors.Ring.OUTER && (w.bowstring || w.orb || w.anyDemi))
				{
					o = peek(w, x, z);
				}
				else
				{
					continue;
				}
				if (o != null && o.value > 0 && fits(o.costTicks, m, w))
				{
					out.add(o);
				}
			}
		}
		out.sort(Comparator.comparingDouble(Option::rate).reversed());
		return out;
	}

	public Option best(MapView m, Needs n, Wants w)
	{
		List<Option> r = rank(m, n, w);
		return r.isEmpty() ? null : r.get(0);
	}

	private Option scoreRoom(MapView m, Needs n, Wants w, int x, int z, int stepsToNeighbour)
	{
		RoomPriors.Ring ring = RoomPriors.ring(x, z);
		StringBuilder why = new StringBuilder(ring.name().toLowerCase());
		double value = 0;
		double harvestTicks = 0;
		for (Resource r : NODE_RES)
		{
			// A known node a long way off doesn't make the rooms around us worthless for that
			// resource (1.7.0: the only ore, 3 outer-ring nodes 50-116 tiles off, zeroed every
			// room's ore value; no ore all run, no armour, a boss death).
			int eff = Math.max(0, nodesNeeded(n, r) - (stacks(r) ? m.liveNodesWithin(r, KNOWN_NODE_TILES) : m.liveNodes(r)));
			if (!stacks(r))
			{
				eff = Math.min(eff, (w.freeSlots + yieldPerNode(r) - 1) / yieldPerNode(r));
			}
			if (eff == 0)
			{
				continue;
			}
			double exp = Math.min(eff, RoomPriors.expectedNodes(r, ring));
			if (exp > 0)
			{
				value += exp * resourceWeight(r, n, w) * frameFirst(w);
				harvestTicks += exp * (HARVEST_START_TICKS + Math.min(n.get(r), yieldPerNode(r)) * HARVEST_ITEM_TICKS);
				why.append(' ').append(r.name().toLowerCase()).append(String.format("%.2f", exp));
			}
		}
		double weak = RoomPriors.weakMonsterChance(ring);
		if (w.frame)
		{
			value += weak * (w.opening ? FRAME_FIRST_VALUE : FRAME_VALUE);
			why.append(" frame");
		}
		if (w.shards > 0)
		{
			value += weak * 0.5 * Math.min(1.0, w.shards / 100.0);
			why.append(" shards");
		}
		double demi = RoomPriors.demiBossChance(ring);
		if (w.anyDemi && demi > 0)
		{
			value += 3 * demi * DEMI_VALUE; // any of the three types will do
			why.append(" demi-boss?");
		}
		else if (w.bowstring && demi > 0)
		{
			value += demi * DEMI_VALUE;
			why.append(" darkbeast?");
		}
		if (w.orb && demi > 0)
		{
			value += demi * DEMI_VALUE;
			why.append(" dragon?");
		}
		if (value <= 0)
		{
			return null;
		}
		if (w.opening && Math.abs(x - m.startX()) + Math.abs(z - m.startZ()) == 1)
		{
			value += OPENING_BONUS;
			why.append(" next-to-start");
		}
		int fresh = unexploredNeighbours(m, x, z);
		value += FRONTIER_BONUS * fresh;
		double cost = stepsToNeighbour * ROOM_STEP_TICKS + LIGHT_TICKS + ROOM_STEP_TICKS / 2 + harvestTicks;
		if (w.opening && m.startX() >= 0)
		{
			// Loop 1 ends near the start room so the walk home is short and no teleport is spent
			// (user): part of the way back is charged to rooms further from the start.
			cost += (Math.abs(x - m.startX()) + Math.abs(z - m.startZ())) * ROOM_STEP_TICKS * HOME_PULL;
		}
		return new Option(Kind.EXPLORE, null, x, z, value * EXPLORE_DISCOUNT, cost, why.toString());
	}

	/** Light an outer room from our doorway just to see if a demi-boss is there (no walking in). */
	private Option peek(Wants w, int x, int z)
	{
		double p = RoomPriors.demiBossChance(RoomPriors.Ring.OUTER);
		double value = w.anyDemi ? 3 * p * DEMI_VALUE : (w.bowstring ? p * DEMI_VALUE : 0) + (w.orb ? p * DEMI_VALUE : 0);
		return value <= 0 ? null : new Option(Kind.EXPLORE, null, x, z, value, LIGHT_TICKS, "peek for demi-bosses");
	}

	private boolean fits(double costTicks, MapView m, Wants w)
	{
		if (w.budgetTicks == Integer.MAX_VALUE)
		{
			return true;
		}
		double home = w.hasTeleport ? 5 : (Math.abs(m.roomX() - m.startX()) + Math.abs(m.roomZ() - m.startZ()) + 1) * ROOM_STEP_TICKS;
		return costTicks + home <= w.budgetTicks;
	}

	/** Room steps from the player's room to (x,z) through lit rooms; -1 if unreachable. */
	public static int roomDistance(MapView m, int x, int z)
	{
		if (m.roomX() < 0 || x < 0 || z < 0 || x >= N || z >= N)
		{
			return -1;
		}
		return roomSteps(m)[x][z];
	}

	/** BFS over lit rooms from the player's room: room steps to each, -1 if unreachable. */
	static int[][] roomSteps(MapView m)
	{
		int[][] d = new int[N][N];
		for (int[] row : d)
		{
			java.util.Arrays.fill(row, -1);
		}
		ArrayDeque<int[]> q = new ArrayDeque<>();
		d[m.roomX()][m.roomZ()] = 0;
		q.add(new int[]{m.roomX(), m.roomZ()});
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!q.isEmpty())
		{
			int[] c = q.poll();
			for (int[] dd : dirs)
			{
				int nx = c[0] + dd[0];
				int nz = c[1] + dd[1];
				if (nx < 0 || nz < 0 || nx >= N || nz >= N || d[nx][nz] >= 0 || !m.found(nx, nz)
					|| RoomPriors.ring(nx, nz) == RoomPriors.Ring.BOSS)
				{
					continue;
				}
				d[nx][nz] = d[c[0]][c[1]] + 1;
				q.add(new int[]{nx, nz});
			}
		}
		return d;
	}

	private static int bestNeighbourSteps(int[][] steps, int x, int z)
	{
		int best = -1;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] dd : dirs)
		{
			int nx = x + dd[0];
			int nz = z + dd[1];
			if (nx < 0 || nz < 0 || nx >= N || nz >= N)
			{
				continue;
			}
			int s = steps[nx][nz];
			if (s >= 0 && (best < 0 || s < best))
			{
				best = s;
			}
		}
		return best;
	}

	private static int unexploredNeighbours(MapView m, int x, int z)
	{
		int c = 0;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] dd : dirs)
		{
			int nx = x + dd[0];
			int nz = z + dd[1];
			if (nx >= 0 && nz >= 0 && nx < N && nz < N && !m.found(nx, nz) && RoomPriors.ring(nx, nz) != RoomPriors.Ring.BOSS)
			{
				c++;
			}
		}
		return c;
	}
}
