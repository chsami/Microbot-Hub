package net.runelite.client.plugins.microbot.cgprep.actions;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.GearSlot;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze;
import net.runelite.client.plugins.microbot.cgprep.nav.InstanceNav;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Fight one maze monster or demi-boss. One action per call, in priority order:
 * eat if low -> best weapon wielded -> attack if not already fighting (prayers: fight/PrayerGuard).
 *
 * Prep demi-bosses don't switch styles, so a single protection prayer per monster is enough.
 */
public final class CombatAction
{
	private static final int SEARCH_RADIUS = 15; // NPCs only exist ~15 tiles out anyway

	private final InstanceNav nav;
	private final int eatBelowPct;
	private final int bailBelowHp;

	public CombatAction(InstanceNav nav, int eatBelowPct, int bailBelowHp)
	{
		this.nav = nav;
		this.eatBelowPct = eatBelowPct;
		this.bailBelowHp = bailBelowHp;
	}

	public enum Outcome
	{
		FIGHTING, NO_TARGET, WALKING, KILLED, BAIL,
		/** Ran through the demi-boss so it blocks the melee monsters chasing us (user). */
		REPOSITION
	}

	private Rs2NpcModel target;

	public Outcome step(CgMode mode, GauntletMaze maze, Monster m)
	{
		int hp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
		int hpPct = hp * 100 / Math.max(1, Rs2Player.getRealSkillLevel(Skill.HITPOINTS));

		// Survival first.
		if (hpPct < eatBelowPct)
		{
			if (Rs2Inventory.contains(CgIds.PADDLEFISH))
			{
				Rs2Inventory.interact(CgIds.PADDLEFISH, "Eat");
				sleepUntil(() -> Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) > hp, 1800);
				return Outcome.FIGHTING;
			}
			if (hp < bailBelowHp)
			{
				return Outcome.BAIL; // no food and low: give up on this one (TODO(recon): can we flee?)
			}
		}

		// A target left over from a previous fight (another monster type) must not count: 0.3.7
		// reported the Dragon "KILLED" on the first tick because the last weak monster was dead.
		if (target != null && java.util.Arrays.stream(m.ids(mode)).noneMatch(id -> id == target.getId()))
		{
			target = null;
		}
		if (target != null)
		{
			net.runelite.api.coords.WorldPoint at = target.getWorldLocation();
			if (at != null)
			{
				lastTargetTile = at;
			}
		}
		if (target != null && (target.isDead() || target.getHealthRatio() == 0))
		{
			target = null;
			return Outcome.KILLED;
		}

		Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
			.withIds(m.ids(mode))
			.where(n -> !n.isDead())
			.nearest(SEARCH_RADIUS);
		if (npc == null)
		{
			if (target != null)
			{
				// It was there last tick and now isn't in the cache: treat as killed.
				target = null;
				return Outcome.KILLED;
			}
			GauntletMaze.Sighting seen = maze.lastSeen(m);
			if (seen != null)
			{
				nav.step(seen.raw, 5);
				return Outcome.WALKING;
			}
			return Outcome.NO_TARGET;
		}

		// Protection prayers are PrayerGuard's job (only when actually in danger), not every fight's.

		// Demi-boss + melee monsters on us: run through the demi to its far side. Players walk
		// through NPCs but NPCs block each other, so the demi becomes a wall between us and them.
		if (m != Monster.WEAK && m != Monster.STRONG && trapBehindDemi(mode, npc))
		{
			return Outcome.REPOSITION;
		}
		// A second demi-boss shooting at us (user, 0.3.30): step out of ITS line of sight, keeping
		// ours to the one we fight, and take them one at a time.
		if (m != Monster.WEAK && m != Monster.STRONG && hideFromSecondDemi(mode, npc, m))
		{
			return Outcome.REPOSITION;
		}

		if (equipBestWeapon(mode))
		{
			return Outcome.FIGHTING;
		}

		if (!Rs2Player.isInteracting() || !npc.isInteractingWithPlayer())
		{
			if (!Aim.onScreen(npc.getLocalLocation()))
			{
				net.runelite.api.coords.WorldPoint at = npc.getWorldLocation();
				if (at != null)
				{
					nav.step(at, 1); // off screen even after the camera turn: close in first
				}
				return Outcome.WALKING;
			}
			if (npc.click("Attack"))
			{
				target = npc;
				sleepUntil(Rs2Player::isInteracting, 2400);
			}
			return Outcome.FIGHTING;
		}
		target = npc;
		return Outcome.FIGHTING;
	}

	private long lastTrapMs;
	private int trapsThisFight;
	private int trapDemiIndex = -1;

	private boolean trapBehindDemi(CgMode mode, Rs2NpcModel demi)
	{
		long now = System.currentTimeMillis();
		if (demi.getIndex() != trapDemiIndex)
		{
			trapDemiIndex = demi.getIndex();
			trapsThisFight = 0;
		}
		if (now - lastTrapMs < 6000 || trapsThisFight >= 3)
		{
			return false;
		}
		int[] meleeIds = java.util.stream.IntStream.concat(java.util.Arrays.stream(Monster.WEAK.ids(mode)),
			java.util.Arrays.stream(Monster.STRONG.ids(mode))).toArray();
		// True tile distances from the per-tick read (isWithinDistanceFromPlayer is in local units:
		// 128 per tile, so "within 3" never matched and this never ran before 0.3.17).
		net.runelite.client.plugins.microbot.cgprep.fight.WorldScan scan = net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.latest();
		if (scan == null)
		{
			return false;
		}
		java.util.List<net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.Npc> melee = new java.util.ArrayList<>();
		int size = 3;
		for (net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.Npc n : scan.npcs)
		{
			if (n.index == demi.getIndex())
			{
				size = n.size;
			}
			if (!n.dead && n.targetingUs && n.tiles <= 2 && n.raw != null
				&& java.util.Arrays.stream(meleeIds).anyMatch(id -> id == n.id))
			{
				melee.add(n);
			}
		}
		net.runelite.api.coords.WorldPoint d = demi.getWorldLocation();
		if (melee.isEmpty() || d == null)
		{
			return false;
		}
		double cx = d.getX() + (size - 1) / 2.0;
		double cy = d.getY() + (size - 1) / 2.0;
		double mx = 0;
		double my = 0;
		int counted = 0;
		for (net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.Npc n : melee)
		{
			net.runelite.api.coords.WorldPoint p = n.raw;
			if (p != null)
			{
				mx += p.getX();
				my += p.getY();
				counted++;
			}
		}
		if (counted == 0)
		{
			return false;
		}
		mx /= counted;
		my /= counted;
		// From the mobs, through the demi, out the other side (half its size + 3 tiles beyond it).
		double vx = cx - mx;
		double vy = cy - my;
		double len = Math.sqrt(vx * vx + vy * vy);
		if (len < 0.5)
		{
			return false;
		}
		double reach = size / 2.0 + 3;
		net.runelite.api.coords.WorldPoint spot = new net.runelite.api.coords.WorldPoint(
			(int) Math.round(cx + vx / len * reach), (int) Math.round(cy + vy / len * reach), d.getPlane());
		nav.reset();
		InstanceNav.Result r = nav.step(spot, 0);
		if (r != InstanceNav.Result.CLICKED)
		{
			return false;
		}
		lastTrapMs = now;
		trapsThisFight++;
		lastTrapSpot = spot;
		// Let the run play out (a few ticks), then the next step re-attacks from the new side.
		sleepUntil(Rs2Player::isMoving, 1200);
		sleepUntil(() -> !Rs2Player.isMoving(), 3600);
		return true;
	}

	private volatile net.runelite.api.coords.WorldPoint lastTrapSpot;
	private long lastHideMs;
	/** For the log: where we stepped to hide from the second demi-boss. */
	public volatile String hideNote;

	private boolean hideFromSecondDemi(CgMode mode, Rs2NpcModel target, Monster targetType)
	{
		long now = System.currentTimeMillis();
		if (now - lastHideMs < 2500)
		{
			return false;
		}
		net.runelite.client.plugins.microbot.cgprep.fight.WorldScan scan = net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.latest();
		if (scan == null)
		{
			return false;
		}
		int otherIdx = -1;
		for (net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.Npc n : scan.npcs)
		{
			// Ranged / magic demis only (the Bear needs to be next to us; it just follows).
			boolean shooter = n.id == mode.darkBeast || n.id == mode.dragon;
			if (shooter && !n.dead && n.targetingUs && n.index != target.getIndex() && n.tiles <= 14)
			{
				otherIdx = n.index;
				break;
			}
		}
		if (otherIdx < 0)
		{
			return false;
		}
		int oi = otherIdx;
		int ti = target.getIndex();
		boolean bear = targetType == Monster.BEAR;
		net.runelite.api.coords.WorldPoint spot = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			net.runelite.api.Client c = Microbot.getClient();
			net.runelite.api.WorldView wv = c.getTopLevelWorldView();
			net.runelite.api.Player p = c.getLocalPlayer();
			if (wv == null || p == null)
			{
				return null;
			}
			net.runelite.api.NPC other = null;
			net.runelite.api.NPC tgt = null;
			for (net.runelite.api.NPC n : wv.npcs())
			{
				if (n != null && n.getIndex() == oi)
				{
					other = n;
				}
				if (n != null && n.getIndex() == ti)
				{
					tgt = n;
				}
			}
			if (other == null || tgt == null)
			{
				return null;
			}
			net.runelite.api.CollisionData[] maps = wv.getCollisionMaps();
			if (maps == null || maps[wv.getPlane()] == null)
			{
				return null;
			}
			int[][] flags = maps[wv.getPlane()].getFlags();
			net.runelite.api.coords.WorldArea oa = other.getWorldArea();
			net.runelite.api.coords.WorldArea ta = tgt.getWorldArea();
			net.runelite.api.coords.WorldPoint me = p.getWorldLocation();
			net.runelite.api.coords.WorldPoint best = null;
			int bestD = Integer.MAX_VALUE;
			for (int dx = -6; dx <= 6; dx++)
			{
				for (int dy = -6; dy <= 6; dy++)
				{
					net.runelite.api.coords.WorldPoint t = new net.runelite.api.coords.WorldPoint(me.getX() + dx, me.getY() + dy, me.getPlane());
					net.runelite.api.coords.LocalPoint lp = net.runelite.api.coords.LocalPoint.fromWorld(wv, t);
					if (lp == null || (flags[lp.getSceneX()][lp.getSceneY()] & net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
					{
						continue;
					}
					if (oa.hasLineOfSightTo(wv, t))
					{
						continue; // the other one would still see us
					}
					// Our own target: in reach and in sight (the Bear comes to us anyway).
					if (!bear && (!ta.hasLineOfSightTo(wv, t) || ta.distanceTo(t) > 7))
					{
						continue;
					}
					int d = Math.max(Math.abs(dx), Math.abs(dy));
					if (d < bestD)
					{
						bestD = d;
						best = t;
					}
				}
			}
			return best;
		}).orElse(null);
		lastHideMs = now;
		if (spot == null)
		{
			return false;
		}
		nav.reset();
		InstanceNav.Result r = nav.step(spot, 0);
		if (r != InstanceNav.Result.CLICKED)
		{
			return false;
		}
		hideNote = "second demi-boss on us: out of its sight to " + spot.getX() + "," + spot.getY();
		sleepUntil(Rs2Player::isMoving, 1200);
		sleepUntil(() -> !Rs2Player.isMoving(), 3000);
		return true;
	}

	public net.runelite.api.coords.WorldPoint lastTrapSpot()
	{
		return lastTrapSpot;
	}

	public void reset()
	{
		target = null;
		lastTargetTile = null;
	}

	/** Where the current/last target stood (its drops land there; with the staff that's ~7 tiles away). */
	private volatile net.runelite.api.coords.WorldPoint lastTargetTile;

	public net.runelite.api.coords.WorldPoint lastTargetTile()
	{
		return lastTargetTile;
	}

	/** Wield the highest-tier weapon we own if a better one is in the inventory. Returns true if it acted. */
	/**
	 * The weapon we should be holding: the best STAFF we own (user: the staff with Mystic Vigour
	 * out-damages the bow with Deadeye, and the bow is only made at the very end), else the best
	 * bow, else the halberd. Returns its id, or -1 if it's already wielded or we own none.
	 */
	static int bestUnwornWeapon(CgMode mode)
	{
		for (GearSlot s : new GearSlot[]{GearSlot.STAFF, GearSlot.BOW, GearSlot.HALBERD})
		{
			int[] ids = s.ids(mode);
			for (int t = ids.length - 1; t >= 0; t--)
			{
				if (Rs2Equipment.isWearing(ids[t]))
				{
					return -1;
				}
				if (Rs2Inventory.contains(ids[t]))
				{
					return ids[t];
				}
			}
		}
		return -1;
	}

	static boolean equipBestWeapon(CgMode mode)
	{
		int id = bestUnwornWeapon(mode);
		if (id == -1)
		{
			return false;
		}
		Rs2Inventory.interact(id, "Wield");
		sleepUntil(() -> Rs2Equipment.isWearing(id), 1800);
		return true;
	}
}
