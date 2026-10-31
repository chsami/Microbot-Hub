package net.runelite.client.plugins.microbot.cgprep.fight;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;

/**
 * Which monsters are "on us" for protection prayers (user, 0.3.13). Pure logic, fed once per tick.
 *
 * A monster becomes ENGAGED when it attacks us: it targets us and is in reach (melee monsters
 * within 2 tiles; demi-bosses at any range). It stays engaged while we kite it, and is let go
 * once it has been more than 10 tiles away for 5 ticks in a row, or died/despawned, or stopped
 * targeting us for 4 ticks. A monster that merely shows up never counts until it attacks; when
 * one does, it's added and the kiting count of everything still close starts over.
 *
 * Demi-bosses (user, 0.3.25): only a THREAT while they're attacking or about to. A Dragon kept
 * "targeting" us from its room while we walked the red route, and Protect from Magic stayed on
 * for a minute. A Dragon / Dark Beast counts from the tick its attack animation starts until 8
 * ticks pass without another attack, and whenever it targets us from within 10 tiles (0.4.7).
 * The Bear hits in melee with no warning, so it also counts whenever it's targeting us from next
 * to us.
 */
public final class Engagement
{
	public static final int KITE_RADIUS = 10;
	public static final int KITE_TICKS = 5;
	public static final int IDLE_TICKS = 4;
	/**
	 * Ticks a demi-boss stays a threat after its last attack. 5 (3 s) let the prayer drop between a
	 * Dragon's attacks and its next hit landed unprayed for 30 (0.4.7, at 5:23).
	 */
	public static final int ATTACK_GRACE = 8;
	/**
	 * A Dragon / Dark Beast targeting us this close is about to shoot: pray before its first attack
	 * (0.4.7: the reactive prayer, now clicked through the prayer tab, was too late for a 49 hit).
	 * Further out it doesn't count until it attacks (0.3.25: a Dragon "targeting" us from its room
	 * kept the prayer on for a minute).
	 */
	public static final int RANGED_DEMI_REACH = 10;
	/** Footprint distance (WorldScan): next to any size of monster = 1; 2 allows a tick of lag. */
	public static final int MELEE_RANGE = 2;

	/** One monster as seen this tick. */
	public static final class Seen
	{
		public final int index;
		public final Monster type;
		public final int distance;
		public final boolean targetingUs;
		/** Its attack animation started this tick (while targeting us). */
		public final boolean attacked;

		public Seen(int index, Monster type, int distance, boolean targetingUs)
		{
			this(index, type, distance, targetingUs, false);
		}

		public Seen(int index, Monster type, int distance, boolean targetingUs, boolean attacked)
		{
			this.index = index;
			this.type = type;
			this.distance = distance;
			this.targetingUs = targetingUs;
			this.attacked = attacked;
		}
	}

	private static final class State
	{
		Monster type;
		int farTicks;
		int idleTicks;
		int distance;
		boolean targetingNow;
		/** Ticks since its last attack on us (demi-bosses). */
		int sinceAttack = 999;

		boolean threat()
		{
			if (!isDemi(type))
			{
				// 1.2.1 (user): a weak/medium monster is only a threat while it can actually hit us:
				// next to us (or a step away) and still after us, or it just swung. One chasing from
				// 4-10 tiles or behind a wall kept Protect from Melee on for a minute (1.2.0 log:
				// t399-t506, and a medium 7 tiles away in the next room) and prayer ran dry.
				return distance <= MELEE_THREAT && (targetingNow || sinceAttack <= 2);
			}
			return sinceAttack <= ATTACK_GRACE || (targetingNow && distance <= (type == Monster.BEAR ? BEAR_REACH : RANGED_DEMI_REACH));
		}
	}

	/**
	 * Footprint distance at which a chasing weak/medium monster counts for Protect from Melee: it
	 * hits from 1; at 3 it needs 2 ticks to swing, enough for the prayer click (~1 tick).
	 */
	public static final int MELEE_THREAT = 3;

	/** A Bear (melee) only hits from next to us (footprint distance 1; 2 allows a tick of lag). */
	public static final int BEAR_REACH = 2;

	private final Map<Integer, State> engaged = new HashMap<>();
	private int attackingNow;

	public void clear()
	{
		engaged.clear();
		attackingNow = 0;
	}

	/** Feed one tick: every live monster seen (any distance), and the indexes still alive. */
	public void tick(Collection<Seen> seen, Set<Integer> alive)
	{
		int now = 0;
		for (Seen s : seen)
		{
			boolean demi = isDemi(s.type);
			State st = engaged.get(s.index);
			if (st != null)
			{
				st.farTicks = s.distance > KITE_RADIUS ? st.farTicks + 1 : 0;
				st.idleTicks = s.targetingUs ? 0 : st.idleTicks + 1;
				st.distance = s.distance;
				st.targetingNow = s.targetingUs;
				st.sinceAttack = s.attacked && s.targetingUs ? 0 : st.sinceAttack + 1;
			}
			if (s.targetingUs && (demi || s.distance <= MELEE_RANGE))
			{
				if (st == null)
				{
					st = new State();
					engaged.put(s.index, st);
				}
				st.type = s.type;
				st.farTicks = 0;
				st.idleTicks = 0;
				st.distance = s.distance;
				st.targetingNow = true;
				if (s.attacked)
				{
					st.sinceAttack = 0;
				}
				if (st.threat())
				{
					now++;
				}
			}
		}
		// Engaged but not in this tick's list at all = out of the scene: counts as far away.
		for (Map.Entry<Integer, State> e : engaged.entrySet())
		{
			boolean listed = false;
			for (Seen s : seen)
			{
				if (s.index == e.getKey())
				{
					listed = true;
					break;
				}
			}
			if (!listed)
			{
				e.getValue().farTicks++;
				e.getValue().targetingNow = false;
				e.getValue().distance = Integer.MAX_VALUE;
				e.getValue().sinceAttack++;
			}
		}
		engaged.entrySet().removeIf(e -> !alive.contains(e.getKey())
			|| e.getValue().farTicks >= KITE_TICKS || e.getValue().idleTicks >= IDLE_TICKS);
		attackingNow = now;
	}

	/** Monsters attacking us this tick. */
	public int attackingNow()
	{
		return attackingNow;
	}

	/** Engaged melee monsters plus demi-bosses that are a threat right now. */
	public int engagedCount()
	{
		int n = 0;
		for (State s : engaged.values())
		{
			if (s.threat())
			{
				n++;
			}
		}
		return n;
	}

	/** The engaged demi-boss to pray against (its style wins over everything), or null. */
	public Monster engagedDemi()
	{
		return engagedDemi(null);
	}

	/**
	 * With several demi-bosses engaged, the one that can hit us THIS tick wins: a Dark Beast or
	 * Dragon targeting us (they hit from range), or a Bear targeting us from next to us. 0.3.14
	 * kept Protect from Melee for a Bear still chasing us from behind while the Dark Beast shot us
	 * dead. Ties: the one we're fighting ({@code prefer}), then the closest. If none can hit right
	 * now, the closest engaged one.
	 */
	public Monster engagedDemi(Monster prefer)
	{
		State best = null;
		int bestScore = Integer.MAX_VALUE;
		for (State s : engaged.values())
		{
			if (!isDemi(s.type) || !s.threat())
			{
				continue;
			}
			boolean canHit = s.targetingNow && (s.type != Monster.BEAR || s.distance <= BEAR_REACH);
			int score = (canHit ? 0 : 1_000_000) + (s.type == prefer ? 0 : 1000) + Math.min(s.distance, 999);
			if (score < bestScore)
			{
				bestScore = score;
				best = s;
			}
		}
		return best == null ? null : best.type;
	}

	/** Engaged melee monsters (weak/strong) close enough to hit us (see {@link State#threat}). */
	public int engagedMelee()
	{
		int n = 0;
		for (State s : engaged.values())
		{
			if (!isDemi(s.type) && s.threat())
			{
				n++;
			}
		}
		return n;
	}

	public static boolean isDemi(Monster m)
	{
		return m != Monster.WEAK && m != Monster.STRONG;
	}
}
