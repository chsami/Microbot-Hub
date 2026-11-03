package net.runelite.client.plugins.microbot.cgprep.nav;

import java.util.ArrayDeque;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Walking inside the Gauntlet instance. The global web walker (Rs2Walker.walkTo) knows nothing
 * about instances, so this does its own breadth-first search over the scene's collision flags and
 * clicks the minimap at the reachable tile that gets closest to the target, in short hops.
 *
 * Everything is in RAW world coords / scene coords; never mix with Rs2Player.getWorldLocation().
 *
 * TODO(recon): confirm Rs2Walker.walkMiniMap(raw WorldPoint) clicks the right tile inside an
 * instance (watch the CLICK line's p0/p1 vs the target). If it expects template coords, convert
 * with WorldPoint.fromLocalInstance before calling it.
 */
public final class InstanceNav
{
	public enum Result
	{
		ARRIVED, MOVING, CLICKED, NO_PATH, FAILED
	}

	/** Max hop length in tiles; minimap clicks further than ~13 tiles can land off the minimap. */
	private static final int HOP = 12;
	/**
	 * Path length searched. 0.3.11: was 40, and a linum node 42 tiles away (longer by path) made
	 * it walk to the tile nearest in a straight line, a dead end behind a wall, for 40 s. The scene
	 * is 104x104, so searching all of it is cheap and always finds the real path if there is one.
	 */
	private static final int BFS_RADIUS = 220;
	/** No step closer to the target for this long while trying = stuck: report NO_PATH. */
	private static final long NO_PROGRESS_MS = 6000;

	/** Which raw tiles may be walked on (lit, non-boss rooms). Null = anything walkable. */
	public interface TileFilter
	{
		boolean allowed(int rawX, int rawY);
	}

	private volatile TileFilter filter;

	public void setFilter(TileFilter f)
	{
		this.filter = f;
	}

	private WorldPoint lastTarget;
	private WorldPoint lastHop;
	private long lastClickMs;
	private int stuckChecks;
	private WorldPoint lastPos;
	private int bestDist = Integer.MAX_VALUE;
	private long progressMs;
	private long lastCallMs;

	public void reset()
	{
		lastTarget = null;
		lastHop = null;
		stuckChecks = 0;
		lastPos = null;
		bestDist = Integer.MAX_VALUE;
		progressMs = 0;
	}

	/**
	 * Take at most one movement action towards {@code targetRaw}.
	 *
	 * @param arriveDist Chebyshev distance that counts as arrived (use 1 for "adjacent to object")
	 */
	public Result step(WorldPoint targetRaw, int arriveDist)
	{
		TileFilter f = filter;
		if (!targetRaw.equals(hopFor))
		{
			hop = HOP;
			hopFor = targetRaw;
		}
		int hopNow = hop;
		Optional<Plan> plan = Microbot.getClientThread().runOnClientThreadOptional(() -> plan(targetRaw, arriveDist, f, hopNow));
		if (!plan.isPresent())
		{
			return Result.FAILED;
		}
		Plan p = plan.get();
		lastPathLen = p.arrived ? 0 : p.pathLen;
		lastPathAtMs = System.currentTimeMillis();
		if (p.arrived)
		{
			reset();
			return Result.ARRIVED;
		}
		if (p.hop == null)
		{
			return Result.NO_PATH;
		}

		// Progress watchdog: the WALKING distance to the target must keep shrinking (0.3.26: the
		// straight-line distance doesn't while the path goes round a corner, which tripped it
		// every 6 s). A gap in calls (a fight, looting) restarts the clock.
		long now = System.currentTimeMillis();
		int d = p.pathLen >= 0 ? p.pathLen : chebyshev(p.me, targetRaw);
		if (!targetRaw.equals(lastTarget) || now - lastCallMs > 2500 || d < bestDist)
		{
			bestDist = d;
			progressMs = now;
		}
		lastCallMs = now;
		if (now - progressMs > NO_PROGRESS_MS)
		{
			lastNote = "no progress for " + NO_PROGRESS_MS / 1000 + " s: me " + p.me.getX() + "," + p.me.getY()
				+ " target " + targetRaw.getX() + "," + targetRaw.getY() + " path " + p.pathLen + (p.reached ? "" : " (target unreachable, heading for the closest tile)")
				+ " hop " + (lastHop == null ? "-" : lastHop.getX() + "," + lastHop.getY()) + " hop length " + hop
				+ (p.moving ? " (moving)" : " (standing)");
			int shorter = Math.max(3, hop / 2);
			reset();
			hop = shorter; // shorter minimap clicks for this target from now on
			return Result.NO_PATH;
		}

		// Don't spam clicks while still walking to the previous hop.
		boolean sameTarget = targetRaw.equals(lastTarget);
		if (sameTarget && p.moving && lastHop != null && p.me.distanceTo2D(lastHop) > 2
			&& System.currentTimeMillis() - lastClickMs < 4000)
		{
			stuckChecks = 0;
			return Result.MOVING;
		}
		if (!p.moving && p.me.equals(lastPos))
		{
			stuckChecks++;
		}
		lastPos = p.me;

		boolean clicked = Rs2Walker.walkMiniMap(p.hop);
		lastTarget = targetRaw;
		lastHop = p.hop;
		lastClickMs = System.currentTimeMillis();
		return clicked ? Result.CLICKED : Result.FAILED;
	}

	/** Path distances from the player to the whole loaded scene (lit rooms only), or null. */
	public DistanceField distanceField()
	{
		return distanceFieldFrom(null);
	}

	/**
	 * Walking distances from {@code originRaw} (null = the player) to every tile of the scene. From
	 * a blocked tile (a doorway node, the bowl) the search starts on its walkable neighbours.
	 */
	public DistanceField distanceFieldFrom(WorldPoint originRaw)
	{
		TileFilter f = filter;
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Client client = Microbot.getClient();
			Player p = client.getLocalPlayer();
			WorldView wv = client.getTopLevelWorldView();
			if (p == null || wv == null || p.getLocalLocation() == null)
			{
				return null;
			}
			CollisionData[] maps = wv.getCollisionMaps();
			if (maps == null || maps[wv.getPlane()] == null)
			{
				return null;
			}
			int[][] flags = maps[wv.getPlane()].getFlags();
			int w = flags.length;
			int h = flags[0].length;
			int sx = p.getLocalLocation().getSceneX();
			int sy = p.getLocalLocation().getSceneY();
			WorldPoint me = p.getWorldLocation();
			int baseX = me.getX() - sx;
			int baseY = me.getY() - sy;
			int[][] dist = new int[w][h];
			int[][] parent = new int[w][h];
			for (int[] row : dist)
			{
				java.util.Arrays.fill(row, -1);
			}
			ArrayDeque<int[]> q = new ArrayDeque<>();
			if (originRaw == null)
			{
				dist[sx][sy] = 0;
				q.add(new int[]{sx, sy});
			}
			else
			{
				int ox = originRaw.getX() - baseX;
				int oy = originRaw.getY() - baseY;
				for (int r = 0; r <= 2 && q.isEmpty(); r++)
				{
					for (int dx = -r; dx <= r; dx++)
					{
						for (int dy = -r; dy <= r; dy++)
						{
							int x = ox + dx;
							int y = oy + dy;
							if (Math.max(Math.abs(dx), Math.abs(dy)) == r && x >= 0 && y >= 0 && x < w && y < h
								&& (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0
								&& (f == null || f.allowed(baseX + x, baseY + y)))
							{
								dist[x][y] = r;
								q.add(new int[]{x, y});
							}
						}
					}
				}
				if (q.isEmpty())
				{
					return null;
				}
			}
			while (!q.isEmpty())
			{
				int[] c = q.poll();
				int x = c[0];
				int y = c[1];
				tryMove(flags, dist, parent, q, x, y, 0, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, w, h, f, baseX, baseY);
				tryMove(flags, dist, parent, q, x, y, 0, -1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, w, h, f, baseX, baseY);
				tryMove(flags, dist, parent, q, x, y, 1, 0, CollisionDataFlag.BLOCK_MOVEMENT_EAST, w, h, f, baseX, baseY);
				tryMove(flags, dist, parent, q, x, y, -1, 0, CollisionDataFlag.BLOCK_MOVEMENT_WEST, w, h, f, baseX, baseY);
			}
			return new DistanceField(baseX, baseY, me.getPlane(), dist);
		}).orElse(null);
	}

	/** Consecutive idle-and-not-moved checks; callers treat >= 5 as stuck. */
	public int stuckChecks()
	{
		return stuckChecks;
	}

	private static final class Plan
	{
		WorldPoint me;
		WorldPoint hop;
		boolean arrived;
		boolean moving;
		/** Walking steps from us to the goal (or to the closest reachable tile). */
		int pathLen = -1;
		/** The goal tile was reachable (false = heading for the closest tile instead). */
		boolean reached;
	}

	/** Walking steps left to the current target as of the last step() call (-1 = unknown). */
	public volatile int lastPathLen = -1;
	public volatile long lastPathAtMs;

	/** What the last stall looked like (for the log), cleared by the caller. */
	public volatile String lastNote;
	/** Hop length for the current target: halved after each stall (12 -> 6 -> 3). */
	private int hop = HOP;
	private WorldPoint hopFor;

	/** Client thread only. */
	private static Plan plan(WorldPoint target, int arriveDist, TileFilter filter, int hopLen)
	{
		Client client = Microbot.getClient();
		Plan out = new Plan();
		Player p = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (p == null || wv == null)
		{
			return out;
		}
		WorldPoint me = p.getWorldLocation();
		out.me = me;
		out.moving = p.getPoseAnimation() != p.getIdlePoseAnimation();
		if (chebyshev(me, target) <= arriveDist && me.getPlane() == target.getPlane())
		{
			out.arrived = true;
			return out;
		}
		LocalPoint meL = p.getLocalLocation();
		CollisionData[] maps = wv.getCollisionMaps();
		if (meL == null || maps == null || maps[wv.getPlane()] == null)
		{
			return out;
		}
		int[][] flags = maps[wv.getPlane()].getFlags();
		int sx = meL.getSceneX();
		int sy = meL.getSceneY();
		int w = flags.length;
		int h = flags[0].length;

		LocalPoint tL = LocalPoint.fromWorld(wv, target);
		// Target may be outside the loaded scene: aim at it anyway by straight-line distance.
		int tx = tL == null ? sx + (target.getX() - me.getX()) : tL.getSceneX();
		int ty = tL == null ? sy + (target.getY() - me.getY()) : tL.getSceneY();

		int[][] dist = new int[w][h];
		int[][] parent = new int[w][h];
		for (int[] row : dist)
		{
			java.util.Arrays.fill(row, -1);
		}
		int baseX = me.getX() - sx;
		int baseY = me.getY() - sy;
		ArrayDeque<int[]> q = new ArrayDeque<>();
		dist[sx][sy] = 0;
		parent[sx][sy] = -1;
		q.add(new int[]{sx, sy});
		// BFS over walkable tiles in LIT rooms only. 0.3.7: unlit rooms aren't in the scene, so
		// their collision flags read as "free"; the old search planned through a dark room and
		// kept clicking a doorway you can't pass (stuck next to the boss room).
		int goalX = -1;
		int goalY = -1;
		int bestX = -1;
		int bestY = -1;
		int bestScore = Integer.MAX_VALUE;
		while (!q.isEmpty())
		{
			int[] c = q.poll();
			int x = c[0];
			int y = c[1];
			int d = dist[x][y];
			int score = Math.max(Math.abs(tx - x), Math.abs(ty - y));
			if (score <= Math.max(0, arriveDist))
			{
				goalX = x;
				goalY = y;
				break;
			}
			if (score < bestScore)
			{
				bestScore = score;
				bestX = x;
				bestY = y;
			}
			if (d >= BFS_RADIUS)
			{
				continue;
			}
			tryMove(flags, dist, parent, q, x, y, 0, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, w, h, filter, baseX, baseY);
			tryMove(flags, dist, parent, q, x, y, 0, -1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, w, h, filter, baseX, baseY);
			tryMove(flags, dist, parent, q, x, y, 1, 0, CollisionDataFlag.BLOCK_MOVEMENT_EAST, w, h, filter, baseX, baseY);
			tryMove(flags, dist, parent, q, x, y, -1, 0, CollisionDataFlag.BLOCK_MOVEMENT_WEST, w, h, filter, baseX, baseY);
		}
		out.reached = goalX >= 0;
		if (goalX < 0)
		{
			goalX = bestX;
			goalY = bestY;
		}
		if (goalX >= 0)
		{
			out.pathLen = dist[goalX][goalY];
		}
		if (goalX < 0 || (goalX == sx && goalY == sy))
		{
			return out; // no progress possible from here
		}
		// Walk the real shortest path back from the goal; hop to the tile HOP steps along it.
		java.util.List<int[]> path = new java.util.ArrayList<>();
		int cx = goalX;
		int cy = goalY;
		while (!(cx == sx && cy == sy))
		{
			path.add(0, new int[]{cx, cy});
			int pp = parent[cx][cy];
			cx = pp / h;
			cy = pp % h;
		}
		int[] hopTile = path.get(Math.min(hopLen, path.size()) - 1);
		bestX = hopTile[0];
		bestY = hopTile[1];
		out.hop = WorldPoint.fromScene(wv, bestX, bestY, wv.getPlane());
		return out;
	}

	private static void tryMove(int[][] flags, int[][] dist, int[][] parent, ArrayDeque<int[]> q, int x, int y, int dx, int dy,
		int blockFlag, int w, int h, TileFilter filter, int baseX, int baseY)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (nx < 0 || ny < 0 || nx >= w || ny >= h || dist[nx][ny] != -1)
		{
			return;
		}
		if ((flags[x][y] & blockFlag) != 0)
		{
			return;
		}
		if ((flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
		{
			return;
		}
		if (filter != null && !filter.allowed(baseX + nx, baseY + ny))
		{
			return;
		}
		dist[nx][ny] = dist[x][y] + 1;
		parent[nx][ny] = x * h + y;
		q.add(new int[]{nx, ny});
	}


	public static int chebyshev(WorldPoint a, WorldPoint b)
	{
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
	}
}
