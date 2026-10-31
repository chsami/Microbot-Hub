package net.runelite.client.plugins.microbot.cgprep.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * The user's fixed route (2026-10-28 drawing), as room cells. Pure geometry, no game state.
 *
 * The start room is always next to the boss room (3,3). Let d = the step from the boss to the
 * start ("away from the boss") and p = the sideways step (perpendicular to d). Then:
 *
 *  RED loop (loop 1): the 5 rooms around the start that aren't the boss room, in one sweep:
 *    side(-p), diagonal(d-p), forward(d), diagonal(d+p), side(+p)
 *  with a PEEK into the outer-ring room beyond each of the three front rooms (start + 2d - p,
 *  start + 2d, start + 2d + p): lit from the doorway to see if a demi-boss is there, not entered.
 *
 *  BLUE path (start of loop 2): straight sideways from the start to the outer ring:
 *    start + p, start + 2p, start + 3p   (or the -p side)
 *
 *  GREEN loop: the 24 outer-ring rooms in order, starting at the blue path's ring room and going
 *  AWAY from the peeked rooms first (direction -d), all the way round.
 */
public final class Route
{
	public static final int N = 7;
	public static final int BOSS = 3;

	public enum Kind
	{
		RED, PEEK
	}

	public static final class Step
	{
		public final int x;
		public final int z;
		public final Kind kind;

		Step(int x, int z, Kind kind)
		{
			this.x = x;
			this.z = z;
			this.kind = kind;
		}

		@Override
		public String toString()
		{
			return kind + " " + x + "," + z;
		}
	}

	private Route()
	{
	}

	/** {dx, dz}: from the boss room towards the start room. */
	public static int[] away(int sx, int sz)
	{
		return new int[]{Integer.signum(sx - BOSS), Integer.signum(sz - BOSS)};
	}

	/** {px, pz}: sideways, perpendicular to away(). */
	public static int[] side(int sx, int sz)
	{
		int[] d = away(sx, sz);
		return new int[]{Math.abs(d[1]), Math.abs(d[0])};
	}

	/**
	 * Loop 1 in order. {@code firstSign} = -1 starts on the -p side, +1 on the +p side.
	 * Peeks come right after the front room they're lit from.
	 */
	public static List<Step> red(int sx, int sz, int firstSign)
	{
		int[] d = away(sx, sz);
		int[] p = side(sx, sz);
		int a = firstSign < 0 ? -1 : 1;
		List<Step> out = new ArrayList<>();
		add(out, sx + a * p[0], sz + a * p[1], Kind.RED);
		add(out, sx + d[0] + a * p[0], sz + d[1] + a * p[1], Kind.RED);
		add(out, sx + 2 * d[0] + a * p[0], sz + 2 * d[1] + a * p[1], Kind.PEEK);
		add(out, sx + d[0], sz + d[1], Kind.RED);
		add(out, sx + 2 * d[0], sz + 2 * d[1], Kind.PEEK);
		add(out, sx + d[0] - a * p[0], sz + d[1] - a * p[1], Kind.RED);
		add(out, sx + 2 * d[0] - a * p[0], sz + 2 * d[1] - a * p[1], Kind.PEEK);
		add(out, sx - a * p[0], sz - a * p[1], Kind.RED);
		return out;
	}

	/** The straight path from the start to the outer ring on side {@code sign} (+1 / -1). */
	public static List<int[]> blue(int sx, int sz, int sign)
	{
		int[] p = side(sx, sz);
		List<int[]> out = new ArrayList<>();
		for (int k = 1; k <= 3; k++)
		{
			int x = sx + sign * k * p[0];
			int z = sz + sign * k * p[1];
			if (inGrid(x, z))
			{
				out.add(new int[]{x, z});
			}
		}
		return out;
	}

	/** All 24 outer-ring rooms, from {@code entry} going first in direction {@code first} {dx,dz}. */
	public static List<int[]> ring(int[] entry, int[] first)
	{
		List<int[]> cycle = perimeter();
		int i = indexOf(cycle, entry[0], entry[1]);
		if (i < 0)
		{
			return cycle;
		}
		int size = cycle.size();
		int[] next = cycle.get((i + 1) % size);
		boolean forward = next[0] - entry[0] == first[0] && next[1] - entry[1] == first[1];
		List<int[]> out = new ArrayList<>();
		for (int k = 0; k < size; k++)
		{
			out.add(cycle.get(Math.floorMod(forward ? i + k : i - k, size)));
		}
		return out;
	}

	/** The ring loop for the blue path on side {@code sign}: away from the peeked rooms first. */
	public static List<int[]> green(int sx, int sz, int sign)
	{
		List<int[]> b = blue(sx, sz, sign);
		int[] entry = b.get(b.size() - 1);
		int[] d = away(sx, sz);
		return ring(entry, new int[]{-d[0], -d[1]});
	}

	/** Outer ring, one way round: (0,0)..(6,0), (6,1)..(6,6), (5,6)..(0,6), (0,5)..(0,1). */
	static List<int[]> perimeter()
	{
		List<int[]> c = new ArrayList<>();
		for (int x = 0; x < N; x++)
		{
			c.add(new int[]{x, 0});
		}
		for (int z = 1; z < N; z++)
		{
			c.add(new int[]{N - 1, z});
		}
		for (int x = N - 2; x >= 0; x--)
		{
			c.add(new int[]{x, N - 1});
		}
		for (int z = N - 2; z >= 1; z--)
		{
			c.add(new int[]{0, z});
		}
		return c;
	}

	public static boolean onRing(int x, int z)
	{
		return x == 0 || z == 0 || x == N - 1 || z == N - 1;
	}

	/** Side of a room relative to the start: +1 / -1 along p, 0 if in line with the start. */
	public static int sideOf(int sx, int sz, int x, int z)
	{
		int[] p = side(sx, sz);
		return Integer.signum((x - sx) * p[0] + (z - sz) * p[1]);
	}

	/** In a straight line from the start, at most 2 rooms away (the user's "close to home"). */
	public static boolean nearStartStraight(int sx, int sz, int x, int z)
	{
		int dx = Math.abs(x - sx);
		int dz = Math.abs(z - sz);
		return (dx == 0 || dz == 0) && dx + dz >= 1 && dx + dz <= 2 && !(x == BOSS && z == BOSS);
	}

	/** Within 2 rooms of the start (straight or one diagonal step), not the boss room. */
	public static boolean nearStart(int sx, int sz, int x, int z)
	{
		int d = Math.abs(x - sx) + Math.abs(z - sz);
		return d >= 1 && d <= 2 && !(x == BOSS && z == BOSS);
	}

	static boolean inGrid(int x, int z)
	{
		return x >= 0 && z >= 0 && x < N && z < N;
	}

	private static void add(List<Step> out, int x, int z, Kind k)
	{
		if (inGrid(x, z) && !(x == BOSS && z == BOSS))
		{
			out.add(new Step(x, z, k));
		}
	}

	private static int indexOf(List<int[]> c, int x, int z)
	{
		for (int i = 0; i < c.size(); i++)
		{
			if (c.get(i)[0] == x && c.get(i)[1] == z)
			{
				return i;
			}
		}
		return -1;
	}
}
