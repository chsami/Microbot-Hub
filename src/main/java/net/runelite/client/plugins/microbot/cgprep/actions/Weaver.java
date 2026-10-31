package net.runelite.client.plugins.microbot.cgprep.actions;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.cgprep.fight.WorldScan;

/**
 * 1-tick attack weaving while gathering (user, 0.3.30): the tick a resource's XP drop shows, click
 * Attack on the weak monster hitting us; on the very next game tick, click the node again. Any
 * extra tick in between loses gathering time, so this runs off the game events themselves
 * (StatChanged / GameTick, passed in by the plugin), not off the 300 ms script loop. The clicks
 * go out on a small executor thread (never the client thread).
 *
 * The script "arms" it while we're harvesting a node and "disarms" it otherwise.
 */
public final class Weaver
{
	/** Prep weapons attack every 4 ticks: a 2nd swing before that would just queue. */
	private static final int SWING_TICKS = 4;

	private final ExecutorService exec = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "cgprep-weave");
		t.setDaemon(true);
		return t;
	});
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);

	private volatile boolean armed;
	private volatile CgMode mode;
	private volatile int nodeId;
	private volatile WorldPoint nodeRaw;
	private volatile boolean melee;
	private volatile int resumeTick = -1;
	private volatile int lastSwingTick = -100;
	private volatile int lastResumeTick = -100;
	/** What the last weave did, for the log (read and cleared by the script). */
	public volatile String note;

	public void arm(CgMode mode, int nodeId, WorldPoint nodeRaw, boolean melee)
	{
		this.mode = mode;
		this.nodeId = nodeId;
		this.nodeRaw = nodeRaw;
		this.melee = melee;
		this.armed = true;
	}

	public void disarm()
	{
		armed = false;
		resumeTick = -1;
	}

	/** A weave is in flight (attack sent, node click due or just sent): don't click anything else. */
	public boolean busy(int tick)
	{
		return resumeTick >= 0 || tick - lastResumeTick <= 1;
	}

	/** Plugin thread (StatChanged). */
	public void onStat(Skill skill, int xp, int tick)
	{
		Integer before = lastXp.put(skill, xp);
		if (before == null || xp <= before || !armed)
		{
			return;
		}
		if (skill != Skill.MINING && skill != Skill.WOODCUTTING && skill != Skill.FARMING
			&& skill != Skill.FISHING && skill != Skill.HERBLORE)
		{
			return;
		}
		if (tick - lastSwingTick < SWING_TICKS || resumeTick >= 0)
		{
			return;
		}
		int idx = pickTarget();
		if (idx < 0)
		{
			return;
		}
		lastSwingTick = tick;
		resumeTick = tick + 1;
		exec.execute(() ->
		{
			Rs2NpcModel npc = Microbot.getRs2NpcCache().query().where(x -> x.getIndex() == idx && !x.isDead()).first();
			boolean sent = npc != null && Aim.onScreen(npc.getLocalLocation()) && npc.click("Attack");
			if (!sent)
			{
				resumeTick = -1; // nothing went out: leave the node alone
			}
			note = sent ? "weave: attack on the xp-drop tick, node again next tick" : null;
		});
	}

	/** Plugin thread (GameTick): the tick after the attack, back on the node. */
	public void onTick(int tick)
	{
		int due = resumeTick;
		if (due < 0 || tick < due)
		{
			return;
		}
		resumeTick = -1;
		lastResumeTick = tick;
		CgMode m = mode;
		int id = nodeId;
		WorldPoint at = nodeRaw;
		if (m == null || at == null)
		{
			return;
		}
		exec.execute(() ->
		{
			Rs2TileObjectModel obj = Microbot.getRs2TileObjectCache().query().withId(id).at(at).first();
			if (obj != null && Aim.onScreen(obj.getLocalLocation()))
			{
				obj.click();
			}
		});
	}

	/** A weak monster hitting us that we can reach this tick, else -1. */
	private int pickTarget()
	{
		WorldScan scan = WorldScan.latest();
		CgMode m = mode;
		if (scan == null || scan.me == null || m == null)
		{
			return -1;
		}
		int[] weak = m.weakMonsters;
		for (WorldScan.Npc n : scan.npcs)
		{
			if (n.dead || !n.targetingUs || n.raw == null || java.util.Arrays.stream(weak).noneMatch(id -> id == n.id))
			{
				continue;
			}
			// Melee: only the 4 sides (a diagonal swing would step us off the node); staff: in reach.
			boolean reach = melee
				? n.tiles == 1 && (n.raw.getX() == scan.me.getX() || n.raw.getY() == scan.me.getY())
				: n.tiles <= 3;
			if (reach && clearShot(n.index, melee))
			{
				return n.index;
			}
		}
		return -1;
	}

	/**
	 * We can actually hit it from where we stand (1.7.2, user): no wall in between. A monster 2
	 * tiles off behind a wall made the Attack click walk us round to it and back to the node.
	 * Ranged/magic: line of sight from our tile. Melee: it's next to us and no wall on that edge.
	 * Runs on the client thread (StatChanged); unsure = no weave.
	 */
	private static boolean clearShot(int index, boolean melee)
	{
		try
		{
			net.runelite.api.Client c = Microbot.getClient();
			net.runelite.api.Player me = c.getLocalPlayer();
			net.runelite.api.WorldView wv = me == null ? null : me.getWorldView();
			net.runelite.api.NPC npc = wv == null ? null : wv.npcs().byIndex(index);
			if (npc == null)
			{
				return false;
			}
			net.runelite.api.coords.WorldArea mine = me.getWorldArea();
			net.runelite.api.coords.WorldArea its = npc.getWorldArea();
			if (mine == null || its == null)
			{
				return false;
			}
			if (!mine.hasLineOfSightTo(wv, its))
			{
				return false;
			}
			if (!melee)
			{
				return true;
			}
			if (!mine.isInMeleeDistance(its))
			{
				return false;
			}
			// the edge between us: can we step that way (a wall on the tile edge blocks melee too)
			int dx = Integer.signum(its.getX() - mine.getX());
			int dy = Integer.signum(its.getY() - mine.getY());
			if (its.getX() <= mine.getX() && mine.getX() <= its.getX() + its.getWidth() - 1)
			{
				dx = 0;
			}
			if (its.getY() <= mine.getY() && mine.getY() <= its.getY() + its.getHeight() - 1)
			{
				dy = 0;
			}
			return mine.canTravelInDirection(wv, dx, dy);
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	public void shutdown()
	{
		exec.shutdownNow();
	}
}
