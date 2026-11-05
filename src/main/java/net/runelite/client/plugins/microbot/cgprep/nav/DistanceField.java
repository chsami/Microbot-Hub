package net.runelite.client.plugins.microbot.cgprep.nav;

import net.runelite.api.coords.WorldPoint;

/**
 * Walking distance (tiles, by the real path through lit rooms) from the player to every tile of
 * the loaded scene, from one breadth-first search. 0.3.15 picked a fishing spot "10 tiles away"
 * that was 10 tiles in a straight line but behind a wall, and ran around for 11 s to reach it.
 * Node choice and the explore planner's costs now use this instead of straight-line distance.
 */
public final class DistanceField
{
	public static final int OUTSIDE = -2;
	public static final int UNREACHABLE = -1;

	private final int baseX;
	private final int baseY;
	private final int plane;
	private final int[][] dist;

	DistanceField(int baseX, int baseY, int plane, int[][] dist)
	{
		this.baseX = baseX;
		this.baseY = baseY;
		this.plane = plane;
		this.dist = dist;
	}

	/** Steps to this exact tile (or next to it if it's blocked), or a negative code. */
	public int toTile(WorldPoint raw)
	{
		if (raw == null || raw.getPlane() != plane)
		{
			return OUTSIDE;
		}
		int sx = raw.getX() - baseX;
		int sy = raw.getY() - baseY;
		if (sx >= 0 && sy >= 0 && sx < dist.length && sy < dist[0].length && dist[sx][sy] >= 0)
		{
			return dist[sx][sy];
		}
		return toObject(raw);
	}

	/** Steps to stand next to an object on this tile (objects block their own tile). */
	public int toObject(WorldPoint raw)
	{
		if (raw == null || raw.getPlane() != plane)
		{
			return OUTSIDE;
		}
		int sx = raw.getX() - baseX;
		int sy = raw.getY() - baseY;
		if (sx < 0 || sy < 0 || sx >= dist.length || sy >= dist[0].length)
		{
			return OUTSIDE;
		}
		int best = Integer.MAX_VALUE;
		// Ring of radius 1, then 2 (fishing spots and some nodes are bigger than one tile).
		for (int r = 1; r <= 2 && best == Integer.MAX_VALUE; r++)
		{
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r)
					{
						continue;
					}
					int x = sx + dx;
					int y = sy + dy;
					if (x >= 0 && y >= 0 && x < dist.length && y < dist[0].length && dist[x][y] >= 0)
					{
						best = Math.min(best, dist[x][y]);
					}
				}
			}
		}
		return best == Integer.MAX_VALUE ? UNREACHABLE : best;
	}
}
