package net.runelite.client.plugins.microbot.cgprep.actions;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze;
import net.runelite.client.plugins.microbot.cgprep.nav.InstanceNav;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Gathers from ONE committed node until it's empty or the need is met.
 *
 * Live test (0.2.5) showed two time-wasters this class now prevents:
 *  - re-clicking a node every ~1.5 s while still walking to it (8 clicks for one ore rock), and
 *  - a monster's hit replacing the gathering animation with the block animation (403), which made
 *    the player look idle, so the bot clicked elsewhere mid-harvest.
 *
 * "Busy" is therefore not "is animating". The player is busy when they're walking, a gathering
 * animation or a resource gain happened in the last few ticks (yields come every 3 ticks; hits
 * don't stop them), or we clicked moments ago. Only after ~5 quiet ticks next to a live node do
 * we click it again, and always the SAME node.
 */
public final class GatherAction
{
	/** The node we're standing by that won't come on screen, and since when (1.6.2). */
	private Object offScreenNode;
	private long offScreenSinceMs;

	/** The global stuck watchdog gave up on what we were doing: skip the current node for a while. */
	public void skipCurrentNode(long ms)
	{
		if (node != null)
		{
			node.avoidUntilMs = System.currentTimeMillis() + ms;
			node = null;
		}
	}

	/** Click the node directly once this close; the server pathfinder walks the last bit. */
	private static final int CLICK_RANGE = 10;
	/** Recon: mine/chop/fish/pick, normal and Corrupted. 403/424 (block) deliberately absent. */
	private static final Set<Integer> GATHER_ANIMS = Set.of(8347, 8348, 8324, 8325, 8336, 8337, 2282);
	/** Yields arrive every 3 ticks (1.8 s); quieter than this means we really stopped. */
	private static final long QUIET_MS = 3000;
	/** Water vials last seen, and when one was last filled here (the fill repeats by itself). */
	private int lastWater = -1;
	private long lastWaterMs;
	/** After a click, give the walk + first swing this long before judging. */
	private static final long CLICK_GRACE_MS = 2400;

	/** Items per full node (wiki + recon). Fish spots give 4, grym roots 1, the rest 3. */
	public static int yieldPerNode(Resource r)
	{
		switch (r)
		{
			case RAW_FISH:
				return 4;
			case GRYM:
				return 1;
			default:
				return 3;
		}
	}


	private final InstanceNav nav;

	/** Set by the script: the tick-exact attack weaver (null = no weaving). */
	public volatile Weaver weaver;
	/** Set by the script: the node the planner picked (a detour on the way), or null = nearest. */
	public volatile WorldPoint prefer;
	/** Set by the script: weave attacks on weak monsters while gathering (setting "weaveAttacks"). */
	public volatile boolean weave = true;
	/** Set by the script: true when the wielded weapon hits with melee (sceptre / halberd). */
	public volatile boolean meleeWeapon = true;
	/** What the last weave did, for the log (read and cleared by the script). */
	public volatile String weaveNote;

	private Resource res;
	private GauntletMaze.Node node;
	private int lastCount = -1;
	private long lastSignalMs;
	private long clickedMs;
	/** Something actually came out of the committed node (not just "walking towards it"). */
	private boolean harvesting;
	/** Items that came out of the committed node so far (for the lookahead: how much is left in it). */
	private int fromNode;

	public GatherAction(InstanceNav nav)
	{
		this.nav = nav;
	}

	public void reset()
	{
		Weaver wv = weaver;
		if (wv != null)
		{
			wv.disarm();
		}
		res = null;
		node = null;
		lastCount = -1;
		lastWater = -1;
		lastWaterMs = 0;
		lastSignalMs = 0;
		clickedMs = 0;
		harvesting = false;
		fromNode = 0;
	}

	/** The committed node while we're harvesting it, else null (see committedResource). */
	public GauntletMaze.Node harvestingNode(GauntletMaze maze)
	{
		return committedResource(maze) == null ? null : node;
	}

	/** Items still expected from the committed node (at least 1). */
	public int leftOnNode()
	{
		Resource r = res;
		return r == null ? 1 : Math.max(1, yieldPerNode(r) - fromNode);
	}

	/**
	 * The resource we're mid-way through harvesting from a still-live node we're standing at, else
	 * null. 0.3.17: "committed" survived the node running out and re-attached to whatever spot was
	 * nearest next, 56-68 tiles away, for one fish; and it pulled the bot out of the start room
	 * after crafting. A commitment now needs a yield from THIS node and us within 12 tiles of it.
	 * 0.3.26: also from the click on it (8 s to get there and start). 0.3.25 clicked a fishing
	 * spot, switched to a grym leaf 0.2 s later while the character went on fishing, and then
	 * waited 3 s of "quiet" after the spot ran out before moving on (the delay the user saw).
	 */
	public Resource committedResource(GauntletMaze maze)
	{
		GauntletMaze.Node n = node;
		WorldPoint me = maze.playerRaw();
		boolean onIt = harvesting || (clickedMs > 0 && System.currentTimeMillis() - clickedMs < 8000);
		if (n == null || n.depleted || !onIt || me == null || InstanceNav.chebyshev(me, n.raw) > 12)
		{
			return null;
		}
		return res;
	}

	/** Raw tile of the committed node, for logging. */
	public WorldPoint committedNode()
	{
		GauntletMaze.Node n = node;
		return n == null ? null : n.raw;
	}

	public ActionResult step(CgMode mode, GauntletMaze maze, Resource r)
	{
		long now = System.currentTimeMillis();
		if ((r == Resource.RAW_FISH || r == Resource.GRYM) && Rs2Inventory.isFull())
		{
			reset();
			return ActionResult.NOT_AVAILABLE; // nowhere to put it: the planner frees space or goes home
		}
		if (r != res)
		{
			reset();
			res = r;
		}
		int count = Rs2Inventory.itemQuantity(mode.itemFor(r));
		boolean yielded = lastCount >= 0 && count > lastCount;
		if (yielded)
		{
			lastSignalMs = now;
			harvesting = node != null;
			fromNode += count - lastCount;
		}
		lastCount = count;
		boolean gatherAnim = GATHER_ANIMS.contains(Rs2Player.getAnimation());

		// Keep the committed node while it's alive; only then pick a new one.
		if (node != null && node.depleted)
		{
			node = null;
			clickedMs = 0;
			harvesting = false;
		}
		// The planner picked a particular node (beside our route): take that one, unless we've
		// already started harvesting another.
		WorldPoint want = prefer;
		if (want != null && (node == null || (!harvesting && !node.raw.equals(want))))
		{
			GauntletMaze.Node p = maze.nodeAt(want);
			if (p != null && !p.depleted && p.type == r)
			{
				node = p;
				clickedMs = 0;
				harvesting = false;
				fromNode = 0;
				lastSignalMs = 0; // the old node's last yield says nothing about this one (0.4.6: 3-5 s idle)
			}
		}
		if (node == null)
		{
			node = maze.nearestLiveNode(r);
			clickedMs = 0;
			harvesting = false;
			fromNode = 0;
			lastSignalMs = 0;
			if (node == null)
			{
				return ActionResult.NOT_AVAILABLE;
			}
		}

		WorldPoint me = maze.playerRaw();
		// A gathering animation only counts for THIS node (next to it). Fishing on at the old spot
		// after the goal moved on used to read as "busy" with the new one.
		if (gatherAnim && me != null && InstanceNav.chebyshev(me, node.raw) <= 2)
		{
			lastSignalMs = now;
			if (clickedMs > 0)
			{
				harvesting = true;
			}
		}
		// Click the node itself when it's on screen within ~a room (a player clicks the tree, not
		// the minimap 2 tiles short of it); the server walks there. Farther: minimap hops.
		int far = me == null ? 0 : InstanceNav.chebyshev(me, node.raw);
		boolean clickFromHere = far <= CLICK_RANGE;
		if (!clickFromHere && far <= 18 && clickedMs == 0)
		{
			Rs2TileObjectModel o = Microbot.getRs2TileObjectCache().query().withId(mode.nodeFor(r)).at(node.raw).first();
			clickFromHere = o != null && Aim.onScreen(o.getLocalLocation());
		}
		if (!clickFromHere && clickedMs > 0 && now - clickedMs < 6000 && far <= 18)
		{
			return ActionResult.IN_PROGRESS; // clicked it from range: the server is walking us there
		}
		if (me != null && !clickFromHere)
		{
			InstanceNav.Result walk = nav.step(node.raw, CLICK_RANGE);
			if (walk == InstanceNav.Result.NO_PATH)
			{
				// 0.3.5: kept retrying an unreachable spot from a corner. Skip it for 30 s, re-plan.
				node.avoidUntilMs = System.currentTimeMillis() + 30_000;
				node = null;
				return ActionResult.NO_PATH;
			}
			return ActionResult.WALKING;
		}

		Rs2TileObjectModel obj = Microbot.getRs2TileObjectCache().query()
			.withId(mode.nodeFor(r))
			.at(node.raw)
			.first();
		if (obj == null)
		{
			// Live id gone from that tile = harvested out. The maze rescan confirms it next tick.
			node.depleted = true;
			node = null;
			clickedMs = 0;
			return ActionResult.DONE;
		}

		// Attack weaving (user, 0.3.23 / 0.3.30): done tick-exact by the Weaver off the game events
		// (attack on the XP-drop tick, node again the next tick). Armed while we work this node.
		Weaver wv = weaver;
		if (wv != null)
		{
			boolean atNode = harvesting && me != null && InstanceNav.chebyshev(me, node.raw) <= 2;
			if (weave && atNode)
			{
				wv.arm(mode, mode.nodeFor(r), node.raw, meleeWeapon);
			}
			else
			{
				wv.disarm();
			}
			if (wv.busy(Microbot.getClient().getTickCount()))
			{
				lastSignalMs = now;
				return ActionResult.IN_PROGRESS; // a weave is mid-way: its node click comes next tick
			}
		}

		boolean busy = Rs2Player.isMoving()
			|| now - lastSignalMs < QUIET_MS
			|| (clickedMs > 0 && now - clickedMs < CLICK_GRACE_MS);
		if (busy)
		{
			return ActionResult.IN_PROGRESS;
		}

		// Wiki: fill potion vials at the fishing spot (no pump trip). One use fills them all, a vial
		// per tick (user, 0.4.7), so while the water count keeps rising we wait; then the normal
		// click below starts fishing again.
		if (r == Resource.RAW_FISH)
		{
			int vial = Rs2Inventory.contains(mode.vial) ? mode.vial
				: Rs2Inventory.contains(CgIds.VIAL_EMPTY_ALT) ? CgIds.VIAL_EMPTY_ALT : -1;
			int waterNow = Rs2Inventory.count(CgIds.VIAL_WATER);
			if (waterNow != lastWater)
			{
				if (lastWater >= 0 && waterNow > lastWater)
				{
					lastWaterMs = now; // another vial filled: the batch is running
				}
				lastWater = waterNow;
			}
			if (vial != -1 && now - lastWaterMs < 1500)
			{
				return ActionResult.IN_PROGRESS;
			}
			if (vial != -1)
			{
				int water = waterNow;
				if (!Aim.useItemOn(vial, obj))
				{
					return ActionResult.NO_REACTION;
				}
				clickedMs = System.currentTimeMillis();
				boolean filled = sleepUntil(() -> Rs2Inventory.count(CgIds.VIAL_WATER) > water, 2400);
				if (filled)
				{
					lastWater = Rs2Inventory.count(CgIds.VIAL_WATER);
					lastWaterMs = System.currentTimeMillis();
				}
				return filled ? ActionResult.CLICKED : ActionResult.NO_REACTION;
			}
		}

		int before = count;
		if (!Aim.onScreen(obj.getLocalLocation()))
		{
			// Off screen even after the camera turn: get closer, but not forever (1.6.2: 100 s of
			// "WALKING" on one tile; a minimap click on a tile we're already by doesn't move us).
			if (offScreenNode != node)
			{
				offScreenNode = node;
				offScreenSinceMs = System.currentTimeMillis();
			}
			if (System.currentTimeMillis() - offScreenSinceMs > 8000)
			{
				node.avoidUntilMs = System.currentTimeMillis() + 30_000;
				node = null;
				offScreenNode = null;
				return ActionResult.NO_PATH;
			}
			nav.step(node.raw, 1);
			return ActionResult.WALKING;
		}
		offScreenNode = null;
		if (!obj.click())
		{
			return ActionResult.NO_REACTION;
		}
		clickedMs = System.currentTimeMillis();
		boolean started = sleepUntil(() -> Rs2Player.isMoving() || GATHER_ANIMS.contains(Rs2Player.getAnimation())
			|| Rs2Inventory.itemQuantity(mode.itemFor(r)) > before, 2400);
		return started ? ActionResult.CLICKED : ActionResult.NO_REACTION;
	}
}
