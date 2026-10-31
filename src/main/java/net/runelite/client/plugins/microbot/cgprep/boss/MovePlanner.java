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
	}

	private static final class Node
	{
		final int p;
		final int timer;
		final int[][] tor;
		final double cost;
		final int first;

		Node(int p, int timer, int[][] tor, double cost, int first)
		{
			this.p = p;
			this.timer = timer;
			this.tor = tor;
			this.cost = cost;
			this.first = first;
		}
	}

	private final BrainConfig c;

	public MovePlanner(BrainConfig c)
	{
		this.c = c;
	}

	public Result plan(FightModel m)
	{
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
		layer.add(new Node(Arena.t(o.px, o.py), timer0, tor0, 0, -2));
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
							next.put(key, new Node(q, timer, torT, cost, first));
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
		return res;
	}

	private double stepCost(FightModel m, int qx, int qy, int T, int[][] torT, int[] seen, boolean stay)
	{
		FightObs o = m.o;
		double s = stay ? 0 : c.wMove;
		if (m.floorHit(qx, qy, T))
		{
			s += c.wFloor;
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
			}
		}
		return s;
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
