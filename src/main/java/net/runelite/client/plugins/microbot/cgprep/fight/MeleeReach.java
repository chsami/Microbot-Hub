package net.runelite.client.plugins.microbot.cgprep.fight;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Can a melee monster actually get to us (1.8.1, user)? Gauntlet monsters don't path-find: each
 * tick they step straight at their target, and one stuck behind a wall corner, or too wide for the
 * gap between it and us, never gets a swing in. Protect from Melee for it only burns points.
 *
 * Pure: the collision checks come in through {@link Collision}, so this is unit-tested with drawn
 * maps. The exact order a monster tries its steps in (diagonal, then x, then y) isn't certain, so
 * every greedy step is tried and the monster counts as stuck only if none of them gets it to a
 * tile it can hit us from. When in doubt it can reach us, and the prayer goes up.
 */
public final class MeleeReach
{
	/** Collision for the area around us, in world tiles (x,y = a footprint's south-west tile). */
	public interface Collision
	{
		/** Can a size x size footprint at (x,y) step one tile by (dx,dy)? Monsters in the way don't count. */
		boolean canStep(int x, int y, int size, int dx, int dy);
	}

	/** How far ahead the monster's steps are followed (it walks a tile a tick). */
	public static final int STEPS = 16;

	private MeleeReach()
	{
	}

	/**
	 * True if a size x size monster with its south-west tile at (nx,ny), stepping straight at us on
	 * (px,py) every tick, can end up next to us (not diagonally) with nothing between it and us.
	 */
	public static boolean canReach(int nx, int ny, int size, int px, int py, Collision c)
	{
		ArrayDeque<int[]> open = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		open.add(new int[]{nx, ny, 0});
		seen.add(key(nx, ny));
		while (!open.isEmpty())
		{
			int[] cur = open.poll();
			int x = cur[0];
			int y = cur[1];
			int dx = px < x ? -1 : px > x + size - 1 ? 1 : 0;
			int dy = py < y ? -1 : py > y + size - 1 ? 1 : 0;
			if (dx == 0 && dy == 0)
			{
				return true; // on top of us: it steps out and hits, count it
			}
			if (canHit(x, y, size, px, py, c))
			{
				return true;
			}
			if (cur[2] >= STEPS)
			{
				continue;
			}
			int[][] steps = dx != 0 && dy != 0 ? new int[][]{{dx, dy}, {dx, 0}, {0, dy}} : new int[][]{{dx, dy}};
			for (int[] s : steps)
			{
				int tx = x + s[0];
				int ty = y + s[1];
				if (covers(tx, ty, size, px, py) || !c.canStep(x, y, size, s[0], s[1]) || !seen.add(key(tx, ty)))
				{
					continue; // it can't step onto us, or into a wall
				}
				open.add(new int[]{tx, ty, cur[2] + 1});
			}
		}
		return false;
	}

	/**
	 * Next to us on a side (melee doesn't reach diagonally) with no wall between its edge tile and
	 * our tile.
	 */
	static boolean canHit(int x, int y, int size, int px, int py, Collision c)
	{
		int ax = px < x ? x - px : px > x + size - 1 ? px - (x + size - 1) : 0;
		int ay = py < y ? y - py : py > y + size - 1 ? py - (y + size - 1) : 0;
		if (ax + ay != 1)
		{
			return false;
		}
		int ex = Math.max(x, Math.min(x + size - 1, px)); // its tile next to us
		int ey = Math.max(y, Math.min(y + size - 1, py));
		return c.canStep(ex, ey, 1, px - ex, py - ey);
	}

	private static boolean covers(int x, int y, int size, int px, int py)
	{
		return px >= x && px < x + size && py >= y && py < y + size;
	}

	private static long key(int x, int y)
	{
		return ((long) x << 32) ^ (y & 0xffffffffL);
	}
}
