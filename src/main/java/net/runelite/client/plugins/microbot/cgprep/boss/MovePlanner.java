package net.runelite.client.plugins.microbot.cgprep.boss;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where to stand next tick. A beam search over our own moves for the next few ticks (stand and
 * attack, or step to any tile within 2: one tick of running), with the tornadoes moved by
 * {@link TornadoStep} toward each simulated position (ambiguous steps keep both tiles), the floor
 * from {@link FightModel#floorHit} and stomps on boss attack ticks. Cost is expected damage in HP
 * plus a price for ticks where our swing is ready but we're not standing to take it.
 */
public final class MovePlanner
{
	public static final class Result
	{
		/** Tile to step to, or -1 = stand (attack). */
		public int x = -1;
		public int y = -1;
		public double cost;
		public int nodes;
		/** The whole planned route: our tile at tick t+1 .. t+horizon (1.8.1, for long run clicks). */
		public int[] path = new int[0];
	}

	private static final class Node
	{
		final int p;
		final int timer;
		final int[][] tor;
		final double cost;
		final int first;
		final int[] path;

		Node(int p, int timer, int[][] tor, double cost, int first, int[] path)
		{
			this.p = p;
			this.timer = timer;
			this.tor = tor;
			this.cost = cost;
			this.first = first;
			this.path = path;
		}
	}

	private final BrainConfig c;
	private final java.util.Random rnd;
	/** 1.8.1: ticks early to leave the current floor pattern's tiles (see BrainConfig.floorLeadMax). */
	private int floorLead;
	private int leadForWarn = Integer.MIN_VALUE;
	/** 1.8.1: tiles to keep from this tornado phase's tornadoes (see BrainConfig.pClear1). */
	private int torClear = 1;
	private boolean torPhase;

	public MovePlanner(BrainConfig c)
	{
		this(c, new java.util.Random());
	}

	public MovePlanner(BrainConfig c, java.util.Random rnd)
	{
		this.c = c;
		this.rnd = rnd;
	}

	/** This floor pattern's lead and this tornado phase's distance: drawn once each, when they start. */
	private void drawHabits(FightModel m)
	{
		if (m.warnStart != leadForWarn)
		{
			leadForWarn = m.warnStart;
			int max = (int) Math.round(c.floorLeadMax);
			floorLead = max <= 0 ? 0 : rnd.nextInt(max + 1);
		}
		boolean tor = m.o.torN > 0;
		if (tor && !torPhase)
		{
			double r = rnd.nextDouble();
			torClear = r < c.pClear1 ? 1 : r < c.pClear1 + c.pClear2 ? 2 : 3;
		}
		torPhase = tor;
	}

	public int floorLead()
	{
		return floorLead;
	}

	public int torClear()
	{
		return torClear;
	}

	public Result plan(FightModel m)
	{
		drawHabits(m);
		FightObs o = m.o;
		int t = m.tick;
		int n = o.torN;
		int[] seen = new int[n];
		int[][] tor0 = new int[n][];
		for (int i = 0; i < n; i++)
		{
			seen[i] = t - m.tornadoAge(o.torIdx[i]);
			tor0[i] = new int[]{Arena.t(o.torX[i], o.torY[i])};
		}
		int timer0 = m.readyTick < 0 ? 0 : Math.max(0, m.readyTick - (t + 1));
		int speed = m.attackSpeed(m.weaponToUse());
		List<Node> layer = new ArrayList<>();
		layer.add(new Node(Arena.t(o.px, o.py), timer0, tor0, 0, -2, new int[0]));
		Result res = new Result();
		double w = 1;
		for (int k = 1; k <= c.horizon; k++)
		{
			int T = t + k;
			Map<Long, Node> next = new HashMap<>();
			for (Node nd : layer)
			{
				int[][] torT = advance(nd.tor, nd.p, T, seen);
				long torHash = Arrays.deepHashCode(torT);
				int px = Arena.x(nd.p);
				int py = Arena.y(nd.p);
				for (int dx = -2; dx <= 2; dx++)
				{
					for (int dy = -2; dy <= 2; dy++)
					{
						int qx = px + dx;
						int qy = py + dy;
						if (!Arena.in(qx, qy))
						{
							continue;
						}
						boolean stay = dx == 0 && dy == 0;
						// 1.8.1 (user): never end a tick under the boss (a stomp hits up to ~50 through
						// any prayer) unless we're already there and have to stay or move within it.
						if (c.neverUnderBoss && Arena.underBoss(qx, qy, o.bx, o.by) && !Arena.underBoss(px, py, o.bx, o.by))
						{
							continue;
						}
						double cost = nd.cost + w * stepCost(m, qx, qy, T, torT, seen, stay);
						if (!stay)
						{
							// If this click lands a tick late we're still on the old tile at T.
							cost += w * c.pLate * hazard(m, px, py, T, torT);
						}
						int timer = nd.timer;
						boolean under = Arena.underBoss(qx, qy, o.bx, o.by);
						if (stay && !under && timer == 0)
						{
							timer = speed - 1; // swung at T: ready again at T + speed
						}
						else
						{
							if (timer == 0)
							{
								cost += w * c.wIdle;
							}
							timer = Math.max(0, timer - 1);
						}
						int q = Arena.t(qx, qy);
						int first = k == 1 ? (stay ? -1 : q) : nd.first;
						long key = ((long) q * 8 + timer) * 1_000_003L + torHash + (k == 1 ? first * 7919L : 0);
						Node old = next.get(key);
						if (old == null || old.cost > cost)
						{
							int[] path = java.util.Arrays.copyOf(nd.path, k);
							path[k - 1] = q;
							next.put(key, new Node(q, timer, torT, cost, first, path));
						}
						res.nodes++;
					}
				}
			}
			layer = new ArrayList<>(next.values());
			layer.sort((a, b) -> Double.compare(a.cost, b.cost));
			if (layer.size() > c.beam)
			{
				layer = new ArrayList<>(layer.subList(0, c.beam));
			}
			w *= c.decay;
		}
		Node best = null;
		double bestCost = Double.MAX_VALUE;
		int Tend = t + c.horizon;
		for (Node nd : layer)
		{
			double cost = nd.cost + w * terminal(m, nd, Tend, seen);
			if (cost < bestCost)
			{
				bestCost = cost;
				best = nd;
			}
		}
		res.cost = bestCost;
		if (best != null && best.first >= 0)
		{
			res.x = Arena.x(best.first);
			res.y = Arena.y(best.first);
		}
		if (best != null)
		{
			res.path = best.path;
		}
		return res;
	}

	/**
	 * The planner's cost of one fixed route ({@code pos[k-1]} = our tile at tick t+k), scored the
	 * same way as in {@link #plan}. {@code click[k-1]}: a new walk click goes out for that tick (it
	 * may land a tick late); a tick of an earlier long run needs none.
	 */
	public double evalPath(FightModel m, int[] pos, boolean[] click)
	{
		FightObs o = m.o;
		int t = m.tick;
		int n = o.torN;
		int[] seen = new int[n];
		int[][] tor = new int[n][];
		for (int i = 0; i < n; i++)
		{
			seen[i] = t - m.tornadoAge(o.torIdx[i]);
			tor[i] = new int[]{Arena.t(o.torX[i], o.torY[i])};
		}
		int timer = m.readyTick < 0 ? 0 : Math.max(0, m.readyTick - (t + 1));
		int speed = m.attackSpeed(m.weaponToUse());
		int p = Arena.t(o.px, o.py);
		double cost = 0;
		double w = 1;
		for (int k = 1; k <= pos.length; k++)
		{
			int T = t + k;
			int[][] torT = advance(tor, p, T, seen);
			int px = Arena.x(p);
			int py = Arena.y(p);
			int q = pos[k - 1];
			int qx = Arena.x(q);
			int qy = Arena.y(q);
			boolean stay = q == p;
			cost += w * stepCost(m, qx, qy, T, torT, seen, stay);
			if (!stay && click[k - 1])
			{
				cost += w * c.pLate * hazard(m, px, py, T, torT);
			}
			if (stay && !Arena.underBoss(qx, qy, o.bx, o.by) && timer == 0)
			{
				timer = speed - 1;
			}
			else
			{
				if (timer == 0)
				{
					cost += w * c.wIdle;
				}
				timer = Math.max(0, timer - 1);
			}
			tor = torT;
			p = q;
			w *= c.decay;
		}
		return cost + w * terminal(m, new Node(p, timer, tor, 0, -1, pos), t + pos.length, seen);
	}

	/** Which ticks of a planned route need a new walk click (each tile change, clicked step by step). */
	public static boolean[] stepClicks(int start, int[] pos)
	{
		boolean[] out = new boolean[pos.length];
		int prev = start;
		for (int k = 0; k < pos.length; k++)
		{
			out[k] = pos[k] != prev;
			prev = pos[k];
		}
		return out;
	}

	/**
	 * One step of the game's path to (tx,ty) in the open arena: STRAIGHT along the longer axis
	 * until both distances are equal, then diagonal (1.8.1, from the 11-05 21:08 log: 3,1 -> 1,7
	 * went 3,3 / 3,5 / 1,7, straight through the boss; the planner had assumed diagonal first).
	 */
	public static int[] step(int x, int y, int tx, int ty)
	{
		int dx = tx - x;
		int dy = ty - y;
		int ax = Math.abs(dx);
		int ay = Math.abs(dy);
		if (ax > ay)
		{
			return new int[]{x + Integer.signum(dx), y};
		}
		if (ay > ax)
		{
			return new int[]{x, y + Integer.signum(dy)};
		}
		return new int[]{x + Integer.signum(dx), y + Integer.signum(dy)};
	}

	/**
	 * Our tile on each of the next {@code ticks} ticks after one click on (tx,ty), running: two
	 * {@link #step}s a tick. Stays on the tile once there.
	 */
	public static int[] walk(int sx, int sy, int tx, int ty, int ticks)
	{
		int[] out = new int[ticks];
		int x = sx;
		int y = sy;
		for (int k = 0; k < ticks; k++)
		{
			for (int s = 0; s < 2 && (x != tx || y != ty); s++)
			{
				int[] n = step(x, y, tx, ty);
				x = n[0];
				y = n[1];
			}
			out[k] = Arena.t(x, y);
		}
		return out;
	}

	/** Does the route ever END a tick under the boss (where a stomp can land)? */
	public static boolean landsUnderBoss(int[] pos, int bx, int by)
	{
		for (int q : pos)
		{
			if (Arena.underBoss(Arena.x(q), Arena.y(q), bx, by))
			{
				return true;
			}
		}
		return false;
	}

	/** Is tile {@code q} on the run from (sx,sy) to (tx,ty) (start and every single step included)? */
	public static boolean onWalk(int sx, int sy, int tx, int ty, int q)
	{
		int x = sx;
		int y = sy;
		while (true)
		{
			if (Arena.t(x, y) == q)
			{
				return true;
			}
			if (x == tx && y == ty)
			{
				return false;
			}
			int[] n = step(x, y, tx, ty);
			x = n[0];
			y = n[1];
		}
	}

	/**
	 * 1.8.1: a tile further along the plan that one click can run to, costing at most
	 * {@code runTolerance} more than walking the plan step by step; -1 if none. The furthest one wins.
	 */
	public int runTarget(FightModel m, Result r, double stepCost)
	{
		int[] path = r.path;
		FightObs o = m.o;
		int here = Arena.t(o.px, o.py);
		for (int j = path.length; j >= 2; j--)
		{
			int d = path[j - 1];
			int dist = Arena.cheb(o.px, o.py, Arena.x(d), Arena.y(d));
			if (dist < 3 || dist > c.runMaxTiles || (dist + 1) / 2 > j)
			{
				continue;
			}
			int[] run = walk(o.px, o.py, Arena.x(d), Arena.y(d), j);
			int[] pos = java.util.Arrays.copyOf(run, path.length);
			System.arraycopy(path, j, pos, j, path.length - j);
			boolean[] click = new boolean[path.length];
			click[0] = true;
			int prev = d;
			for (int k = j; k < path.length; k++)
			{
				click[k] = pos[k] != prev;
				prev = pos[k];
			}
			if (pos[0] == here || landsUnderBoss(run, o.bx, o.by))
			{
				continue; // 1.8.1: a run never ends a tick under the boss
			}
			if (evalPath(m, pos, click) <= stepCost + c.runTolerance)
			{
				return d;
			}
		}
		return -1;
	}

	private double stepCost(FightModel m, int qx, int qy, int T, int[][] torT, int[] seen, boolean stay)
	{
		FightObs o = m.o;
		double s = stay ? 0 : c.wMove;
		if (m.floorHit(qx, qy, T))
		{
			s += c.wFloor;
		}
		else
		{
			// The plan already leaves a tick early (a late click must still get off), so a lead of
			// n is n ticks before that.
			for (int j = 1; floorLead > 0 && j <= floorLead + 1; j++)
			{
				if (m.floorHit(qx, qy, T + j))
				{
					s += c.wFloorEarly; // 1.8.1: off it a little before the last safe tick
					break;
				}
			}
		}
		if (!stay && !Arena.underBoss(qx, qy, o.bx, o.by) && nextToBoss(qx, qy, o.bx, o.by))
		{
			s += c.wBossEdge;
		}
		if (Arena.underBoss(qx, qy, o.bx, o.by))
		{
			s += c.wUnderBoss;
			// The stomp check may use where we stand at the start or at the end of the attack tick.
			if (m.attackTickAt(T) || m.attackTickAt(T + 1))
			{
				s += c.wStomp;
			}
		}
		int q = Arena.t(qx, qy);
		for (int i = 0; i < torT.length; i++)
		{
			int[] set = torT[i];
			if (set.length == 0)
			{
				continue;
			}
			int near = 9;
			for (int tile : set)
			{
				if (tile == q)
				{
					s += c.wTornado * (set.length == 1 ? 1.0 : 0.6);
				}
				near = Math.min(near, Arena.cheb(qx, qy, Arena.x(tile), Arena.y(tile)));
			}
			if (T + 1 - seen[i] < TornadoStep.LIFETIME)
			{
				s += near == 1 ? c.wNear1 : near == 2 ? c.wNear2 : 0; // per tornado: a stack is worse
				if (near >= 2 && near <= torClear)
				{
					s += c.wClear; // 1.8.1: this phase's distance
				}
			}
		}
		return s;
	}

	/** A tile touching the boss's 5x5 (diagonals too). */
	static boolean nextToBoss(int x, int y, int bx, int by)
	{
		return x >= bx - 1 && x <= bx + 5 && y >= by - 1 && y <= by + 5;
	}

	/** Damage expected on tile (x,y) at tick T (floor + tornadoes), for the late-click case. */
	private double hazard(FightModel m, int x, int y, int T, int[][] torT)
	{
		double s = m.floorHit(x, y, T) ? c.wFloor : 0;
		if (Arena.underBoss(x, y, m.o.bx, m.o.by) && (m.attackTickAt(T) || m.attackTickAt(T + 1)))
		{
			s += c.wStomp;
		}
		int q = Arena.t(x, y);
		for (int[] set : torT)
		{
			for (int tile : set)
			{
				if (tile == q)
				{
					s += c.wTornado;
				}
			}
		}
		return s;
	}

	private double terminal(FightModel m, Node nd, int T, int[] seen)
	{
		int px = Arena.x(nd.p);
		int py = Arena.y(nd.p);
		double s = 0;
		for (int d = 1; d <= 3; d++)
		{
			if (m.floorHit(px, py, T + d))
			{
				s += c.wWarnSoon;
				break;
			}
		}
		int[][] tor1 = advance(nd.tor, nd.p, T + 1, seen);
		int free = 0;
		for (int dx = -2; dx <= 2; dx++)
		{
			for (int dy = -2; dy <= 2; dy++)
			{
				int qx = px + dx;
				int qy = py + dy;
				if (!Arena.in(qx, qy) || m.floorHit(qx, qy, T + 1) || Arena.underBoss(qx, qy, m.o.bx, m.o.by))
				{
					continue;
				}
				boolean ok = true;
				for (int[] set : tor1)
				{
					for (int tile : set)
					{
						if (Arena.cheb(qx, qy, Arena.x(tile), Arena.y(tile)) <= 1)
						{
							ok = false;
						}
					}
				}
				if (ok)
				{
					free++;
				}
			}
		}
		if (free == 0)
		{
			s += c.wTrap;
		}
		else if (free <= 2)
		{
			s += c.wCramped;
		}
		for (int[] set : tor1)
		{
			int dmin = 9;
			for (int tile : set)
			{
				dmin = Math.min(dmin, Arena.cheb(px, py, Arena.x(tile), Arena.y(tile)));
			}
			s += Math.max(0, 3 - dmin);
		}
		return s;
	}

	/** Tornado tiles at tick T, from their tiles at T-1 and our tile at T-1. */
	static int[][] advance(int[][] tor, int p, int T, int[] seen)
	{
		int px = Arena.x(p);
		int py = Arena.y(p);
		int[][] out = new int[tor.length][];
		for (int i = 0; i < tor.length; i++)
		{
			int age = T - seen[i];
			if (age >= TornadoStep.LIFETIME || tor[i].length == 0)
			{
				out[i] = new int[0];
				continue;
			}
			if (age < TornadoStep.MOVE_DELAY)
			{
				out[i] = tor[i];
				continue;
			}
			int[] buf = new int[tor[i].length * 2];
			int k = 0;
			for (int tile : tor[i])
			{
				int tx = Arena.x(tile);
				int ty = Arena.y(tile);
				int s = TornadoStep.step(tx, ty, px, py);
				if (s == TornadoStep.TIE)
				{
					k = addUnique(buf, k, TornadoStep.diag(tx, ty, px, py));
					k = addUnique(buf, k, TornadoStep.straight(tx, ty, px, py));
				}
				else
				{
					k = addUnique(buf, k, s);
				}
			}
			int[] set = Arrays.copyOf(buf, k);
			Arrays.sort(set);
			out[i] = set;
		}
		return out;
	}

	private static int addUnique(int[] buf, int k, int v)
	{
		for (int i = 0; i < k; i++)
		{
			if (buf[i] == v)
			{
				return k;
			}
		}
		buf[k] = v;
		return k + 1;
	}
}
