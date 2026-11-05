package net.runelite.client.plugins.microbot.cgprep.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;

/**
 * What the bot knows about this run's maze: where the player is (room + raw tile), which rooms are
 * found, every resource node it has seen (live or depleted), and where demi-bosses were last seen.
 *
 * Refreshed from the script thread via {@link #refresh(CgMode)}. All positions are RAW instance
 * coordinates; see RoomGrid for why.
 */
public final class GauntletMaze implements net.runelite.client.plugins.microbot.cgprep.plan.ExplorePlanner.MapView
{
	public static final class Node
	{
		public final Resource type;
		public final WorldPoint raw;
		public volatile boolean depleted;
		public volatile long lastSeenMs;
		/** Couldn't path to it recently (e.g. behind a wall from here): skip until this time. */
		public volatile long avoidUntilMs;

		public boolean usable()
		{
			return !depleted && avoidUntilMs < System.currentTimeMillis();
		}

		Node(Resource type, WorldPoint raw)
		{
			this.type = type;
			this.raw = raw;
		}
	}

	public static final class Sighting
	{
		public final WorldPoint raw;
		public final long atMs;

		Sighting(WorldPoint raw, long atMs)
		{
			this.raw = raw;
			this.atMs = atMs;
		}
	}

	private final RoomGrid grid = new RoomGrid();
	private final Map<String, Node> nodes = new ConcurrentHashMap<>();
	private final Map<Monster, Sighting> demiBosses = new ConcurrentHashMap<>();
	private final Map<Monster, Boolean> demiBossKilled = new ConcurrentHashMap<>();

	private volatile WorldPoint playerRaw;
	private volatile int roomX = -1;
	private volatile int roomZ = -1;
	/** Room the player was in when this run was first observed = the start room. */
	private volatile int startRoomX = -1;
	private volatile int startRoomZ = -1;
	private volatile boolean inGauntlet;
	private volatile boolean bossStarted;
	private volatile boolean[] roomFound = new boolean[CgIds.ROOM_GRID * CgIds.ROOM_GRID];

	// Scanning every object in the scene each loop cost 0.3-0.8 s (0.2.9 PERF lines) and grows
	// with every lit room. Now: rescan nodes only when the plugin saw a node object spawn/despawn
	// (markDirty), a room was lit, or every few seconds as a safety net.
	private volatile boolean nodesDirty = true;
	private long lastNodeScanMs;
	private long lastDemiScanMs;
	private int lastFoundCount = -1;
	private static final long NODE_RESCAN_MS = 15000;
	private static final long DEMI_RESCAN_MS = 1500;
	/** Rooms we failed to light recently (unreachable doorway etc.): key x*7+z -> until (ms). */
	private final Map<Integer, Long> exploreBlocked = new ConcurrentHashMap<>();

	/** A node object spawned/despawned (plugin event, client thread): rescan on the next refresh. */
	public void markDirty()
	{
		nodesDirty = true;
	}

	/**
	 * A node object spawned (plugin event, client thread). Keeps the node map current without
	 * scanning the scene: 0.3.12 showed the scan taking up to 560 ms, and it ran after every
	 * harvest (each yield swaps a node's object). Lighting a room re-spawns every object in the
	 * scene, so new rooms arrive the same way. Despawns are ignored: the depleted object's spawn
	 * says the same thing, and a scene rebuild despawns everything without anything changing.
	 */
	public void onNodeSpawned(CgMode m, int id, WorldPoint raw)
	{
		// Only RAW instance tiles inside this run's grid (the same coordinates the scan uses); anything
		// else (before calibration, template coords) is left to the safety-net scan.
		if (raw == null || !grid.isCalibrated() || grid.roomOf(raw) == null)
		{
			return;
		}
		Resource type = null;
		boolean dead = false;
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.RAW_FISH})
		{
			if (id == m.nodeFor(r))
			{
				type = r;
			}
		}
		if (type == null)
		{
			dead = true;
			type = id == m.objRockDepleted ? Resource.ORE : id == m.objTreeDepleted ? Resource.BARK
				: id == m.objLinumDepleted ? Resource.LINUM : id == m.objHerbDepleted ? Resource.GRYM
				: id == m.objPondDepleted ? Resource.RAW_FISH : null;
		}
		if (type == null)
		{
			return;
		}
		Resource t = type;
		Node n = nodes.computeIfAbsent(key(raw), k -> new Node(t, raw));
		n.depleted = dead;
		n.lastSeenMs = System.currentTimeMillis();
	}

	/**
	 * Lookahead (0.3.24): while planning what comes AFTER the node being harvested, that node is
	 * treated as gone. Script thread only, set and cleared around one planner call.
	 */
	private volatile WorldPoint pretendGone;

	public void pretendGone(WorldPoint raw)
	{
		pretendGone = raw;
	}

	private boolean alive(Node n)
	{
		WorldPoint g = pretendGone;
		return !n.depleted && (g == null || !g.equals(n.raw));
	}

	private boolean usable(Node n)
	{
		WorldPoint g = pretendGone;
		java.util.function.Predicate<WorldPoint> h = hazard;
		return n.usable() && (g == null || !g.equals(n.raw)) && (h == null || !h.test(n.raw));
	}

	/**
	 * Tiles to keep away from (set by the script each loop): around a live demi-boss we aren't
	 * fighting. Nodes there aren't offered (0.4.7: walked to ore next to a Dragon at 51% HP with no
	 * cooked food and took a second big hit).
	 */
	public volatile java.util.function.Predicate<WorldPoint> hazard;

	/** Last refresh timings (ms) for the PERF line: client snapshot, node scan, demi scan. */
	public volatile long perfSnapMs;
	public volatile long perfNodesMs;
	public volatile long perfDemiMs;

	/** Don't try to light this room again for a while (its doorway couldn't be reached/clicked). */
	public void blockExplore(int x, int z, long forMs)
	{
		exploreBlocked.put(x * CgIds.ROOM_GRID + z, System.currentTimeMillis() + forMs);
	}

	@Override
	public boolean exploreBlocked(int x, int z)
	{
		Long until = exploreBlocked.get(x * CgIds.ROOM_GRID + z);
		return until != null && until > System.currentTimeMillis();
	}

	public void resetForNewRun()
	{
		grid.reset();
		nodes.clear();
		exploreBlocked.clear();
		nodesDirty = true;
		lastFoundCount = -1;
		demiBosses.clear();
		demiBossKilled.clear();
		roomX = -1;
		roomZ = -1;
		startRoomX = -1;
		startRoomZ = -1;
	}

	/** Re-observe everything. Call from the script thread once per loop. */
	public void refresh(CgMode mode)
	{
		Client client = Microbot.getClient();
		long t0 = System.nanoTime();
		Optional<Object[]> snap = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Player p = client.getLocalPlayer();
			boolean[] found = new boolean[CgIds.ROOM_GRID * CgIds.ROOM_GRID];
			for (int z = 0; z < CgIds.ROOM_GRID; z++)
			{
				for (int x = 0; x < CgIds.ROOM_GRID; x++)
				{
					found[z * CgIds.ROOM_GRID + x] = client.getVarbitValue(CgIds.roomFoundVarbit(x, z)) != 0;
				}
			}
			return new Object[]{
				p == null ? null : p.getWorldLocation(), // RAW, deliberately not Rs2Player.getWorldLocation()
				client.getVarbitValue(CgIds.VB_START_ROOM_X),
				client.getVarbitValue(CgIds.VB_START_ROOM_Z),
				// Recon: the varbit flips one tick BEFORE the instance loads (player still in the lobby),
				// so also require the instanced scene, or calibration would use lobby coordinates.
				client.getVarbitValue(CgIds.VB_PLAYER_IN_GAUNTLET) != 0
					&& client.getTopLevelWorldView() != null && client.getTopLevelWorldView().isInstance(),
				client.getVarbitValue(CgIds.VB_BOSS_STARTED) != 0,
				found};
		});
		if (!snap.isPresent())
		{
			return;
		}
		perfSnapMs = (System.nanoTime() - t0) / 1_000_000;
		Object[] s = snap.get();
		playerRaw = (WorldPoint) s[0];
		int sx = (Integer) s[1];
		int sz = (Integer) s[2];
		inGauntlet = (Boolean) s[3];
		bossStarted = (Boolean) s[4];
		roomFound = (boolean[]) s[5];
		if (inGauntlet && playerRaw != null)
		{
			// Varbits 9289/9290 = START room (recon-confirmed; set on entry, never change). The first
			// raw tile we see inside the run is in the start room, which calibrates the grid exactly.
			if (!grid.isCalibrated() && !bossStarted && sx >= 0 && sz >= 0 && roomFound.length > 0
				&& roomFound[sz * CgIds.ROOM_GRID + sx])
			{
				startRoomX = sx;
				startRoomZ = sz;
				grid.calibrate(sx, sz, playerRaw);
			}
			int[] cell = grid.roomOf(playerRaw);
			roomX = cell == null ? -1 : cell[0];
			roomZ = cell == null ? -1 : cell[1];
		}
		if (inGauntlet)
		{
			long now = System.currentTimeMillis();
			// Node events keep the map current; the full scan is only a safety net (first look at a
			// run, and every 15 s).
			if (lastFoundCount < 0 || now - lastNodeScanMs > NODE_RESCAN_MS)
			{
				nodesDirty = false;
				lastFoundCount = foundRoomCount();
				lastNodeScanMs = now;
				long t1 = System.nanoTime();
				scanNodes(mode);
				perfNodesMs = (System.nanoTime() - t1) / 1_000_000;
			}
			else
			{
				perfNodesMs = 0;
			}
			if (now - lastDemiScanMs > DEMI_RESCAN_MS)
			{
				lastDemiScanMs = now;
				long t2 = System.nanoTime();
				scanDemiBosses(mode);
				perfDemiMs = (System.nanoTime() - t2) / 1_000_000;
			}
			else
			{
				perfDemiMs = 0;
			}
		}
	}

	private void scanNodes(CgMode m)
	{
		long now = System.currentTimeMillis();
		Map<Integer, Resource> live = new java.util.HashMap<>();
		Map<Integer, Resource> dead = new java.util.HashMap<>();
		for (Resource r : new Resource[]{Resource.ORE, Resource.BARK, Resource.LINUM, Resource.GRYM, Resource.RAW_FISH})
		{
			live.put(m.nodeFor(r), r);
		}
		dead.put(m.objRockDepleted, Resource.ORE);
		dead.put(m.objTreeDepleted, Resource.BARK);
		dead.put(m.objLinumDepleted, Resource.LINUM);
		dead.put(m.objHerbDepleted, Resource.GRYM);
		dead.put(m.objPondDepleted, Resource.RAW_FISH);

		int[] ids = new int[live.size() + dead.size()];
		int i = 0;
		for (int id : live.keySet())
		{
			ids[i++] = id;
		}
		for (int id : dead.keySet())
		{
			ids[i++] = id;
		}
		List<Rs2TileObjectModel> objs = Microbot.getRs2TileObjectCache().query().withIds(ids).toList();
		for (Rs2TileObjectModel o : objs)
		{
			WorldPoint raw = o.getWorldLocation();
			if (raw == null)
			{
				continue;
			}
			Resource type = live.containsKey(o.getId()) ? live.get(o.getId()) : dead.get(o.getId());
			if (type == null)
			{
				continue;
			}
			Node n = nodes.computeIfAbsent(key(raw), k -> new Node(type, raw));
			n.depleted = dead.containsKey(o.getId());
			n.lastSeenMs = now;
		}
	}

	private void scanDemiBosses(CgMode m)
	{
		long now = System.currentTimeMillis();
		// From the per-tick client-thread read (0.3.16: three NPC cache queries here took 236-360 ms).
		net.runelite.client.plugins.microbot.cgprep.fight.WorldScan scan = net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.latest();
		if (scan == null)
		{
			return;
		}
		for (Monster mon : new Monster[]{Monster.BEAR, Monster.DARK_BEAST, Monster.DRAGON})
		{
			net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.Npc best = null;
			for (net.runelite.client.plugins.microbot.cgprep.fight.WorldScan.Npc n : scan.npcs)
			{
				if (!n.dead && n.raw != null && contains(mon.ids(m), n.id) && (best == null || n.tiles < best.tiles))
				{
					best = n;
				}
			}
			if (best != null)
			{
				demiBosses.put(mon, new Sighting(best.raw, now));
			}
		}
	}

	private static boolean contains(int[] ids, int id)
	{
		for (int x : ids)
		{
			if (x == id)
			{
				return true;
			}
		}
		return false;
	}

	public void markKilled(Monster m)
	{
		demiBossKilled.put(m, true);
		demiBosses.remove(m);
	}

	public boolean isKilled(Monster m)
	{
		return demiBossKilled.getOrDefault(m, false);
	}

	public Sighting lastSeen(Monster m)
	{
		return demiBosses.get(m);
	}

	private volatile net.runelite.client.plugins.microbot.cgprep.nav.DistanceField paths;
	/** Walking distances from where the current movement goal is heading (a doorway, home). */
	private volatile net.runelite.client.plugins.microbot.cgprep.nav.DistanceField routePaths;
	private volatile WorldPoint routeTarget;

	public void setRoute(WorldPoint target, net.runelite.client.plugins.microbot.cgprep.nav.DistanceField f)
	{
		routeTarget = target;
		routePaths = f;
	}

	public WorldPoint routeTarget()
	{
		return routeTarget;
	}

	/**
	 * Extra walking a node adds to where we're heading anyway (user, 0.3.27): (us -> node) +
	 * (node -> target) - (us -> target). A node beside our path costs a few tiles however far it
	 * is from us; one behind us costs double its distance. -1 when there's no route to compare.
	 */
	public int detourTiles(Node n)
	{
		net.runelite.client.plugins.microbot.cgprep.nav.DistanceField r = routePaths;
		WorldPoint me = playerRaw;
		if (r == null || me == null || routeTarget == null)
		{
			return -1;
		}
		int toNode = walkTiles(n);
		int nodeToTarget = r.toObject(n.raw);
		int meToTarget = r.toTile(me);
		if (toNode == Integer.MAX_VALUE || nodeToTarget < 0 || meToTarget < 0)
		{
			return -1;
		}
		return Math.max(0, toNode + nodeToTarget - meToTarget);
	}

	/** Walking steps from a node to the route target, or -1. */
	public int toRouteTarget(Node n)
	{
		net.runelite.client.plugins.microbot.cgprep.nav.DistanceField r = routePaths;
		if (r == null)
		{
			return -1;
		}
		int d = r.toObject(n.raw);
		return d < 0 ? -1 : d;
	}

	/** Live, usable nodes of a type (copies). */
	public java.util.List<Node> liveNodeList(Resource type)
	{
		java.util.List<Node> out = new java.util.ArrayList<>();
		for (Node n : nodes.values())
		{
			if (n.type == type && usable(n))
			{
				out.add(n);
			}
		}
		return out;
	}

	/** The known node on this tile, or null. */
	public Node nodeAt(WorldPoint raw)
	{
		return raw == null ? null : nodes.get(key(raw));
	}

	/** Path distances from the player (set by the script each loop). */
	public void setPaths(net.runelite.client.plugins.microbot.cgprep.nav.DistanceField f)
	{
		paths = f;
	}

	/**
	 * Walking distance to a node: the real path when it's in the loaded scene, else straight-line
	 * x 1.4 (rooms connect through doorways, so paths run longer than the crow flies).
	 */
	public int walkTiles(Node n)
	{
		WorldPoint me = playerRaw;
		if (me == null)
		{
			return Integer.MAX_VALUE;
		}
		net.runelite.client.plugins.microbot.cgprep.nav.DistanceField f = paths;
		int d = f == null ? net.runelite.client.plugins.microbot.cgprep.nav.DistanceField.OUTSIDE : f.toObject(n.raw);
		if (d >= 0)
		{
			return d;
		}
		int straight = n.raw.distanceTo2D(me);
		return d == net.runelite.client.plugins.microbot.cgprep.nav.DistanceField.UNREACHABLE ? straight + 40 : (int) Math.round(straight * 1.4);
	}

	/** Room cell {x, z} of a node, or null. */
	public int[] roomOf(Node n)
	{
		return grid.roomOf(n.raw);
	}

	/** Live nodes of a type in a room. */
	public int liveNodesInRoom(Resource type, int x, int z)
	{
		int c = 0;
		for (Node n : nodes.values())
		{
			int[] r = grid.roomOf(n.raw);
			if (n.type == type && alive(n) && r != null && r[0] == x && r[1] == z)
			{
				c++;
			}
		}
		return c;
	}

	/** A live node of this type in any room matching the filter (room x, z), or null. */
	public Node liveNodeWhere(Resource type, java.util.function.BiPredicate<Integer, Integer> room)
	{
		for (Node n : nodes.values())
		{
			int[] r = grid.roomOf(n.raw);
			if (n.type == type && usable(n) && r != null && room.test(r[0], r[1]))
			{
				return n;
			}
		}
		return null;
	}

	/**
	 * Nearest fishing spot by walking distance, live OR fished out: vials fill at either
	 * (recon). The user fills vials at fishing spots only, never at the pump (0.3.22).
	 */
	public Node nearestFishingSpot()
	{
		WorldPoint me = playerRaw;
		if (me == null)
		{
			return null;
		}
		return nodes.values().stream()
			.filter(n -> n.type == Resource.RAW_FISH && n.avoidUntilMs < System.currentTimeMillis() && n.raw.getPlane() == me.getPlane())
			.min(Comparator.comparingInt(this::walkTiles))
			.orElse(null);
	}

	/** Nearest known live node of a type in the rooms {@code room} accepts, by walking distance (1.2.0). */
	public Node nearestLiveNodeWhere(Resource type, java.util.function.BiPredicate<Integer, Integer> room)
	{
		WorldPoint me = playerRaw;
		if (me == null)
		{
			return null;
		}
		return nodes.values().stream()
			.filter(n ->
			{
				int[] r = grid.roomOf(n.raw);
				return n.type == type && usable(n) && n.raw.getPlane() == me.getPlane() && r != null && room.test(r[0], r[1]);
			})
			.min(Comparator.comparingInt(this::walkTiles))
			.orElse(null);
	}

	/** Nearest known live node of a type, by walking distance from the player. */
	public Node nearestLiveNode(Resource type)
	{
		WorldPoint me = playerRaw;
		if (me == null)
		{
			return null;
		}
		return nodes.values().stream()
			.filter(n -> n.type == type && usable(n) && n.raw.getPlane() == me.getPlane())
			.min(Comparator.comparingInt(this::walkTiles))
			.orElse(null);
	}

	public Map<Resource, Integer> liveNodeCounts()
	{
		Map<Resource, Integer> m = new EnumMap<>(Resource.class);
		for (Node n : nodes.values())
		{
			if (alive(n))
			{
				m.merge(n.type, 1, Integer::sum);
			}
		}
		return m;
	}

	/**
	 * Nearest found-adjacent unexplored room (BFS order isn't needed: the grid is fully connected
	 * per wiki; Manhattan distance is a good-enough heuristic). Null if everything is explored.
	 */
	public int[] nearestUnexploredRoom()
	{
		if (roomX < 0)
		{
			return null;
		}
		List<int[]> candidates = new ArrayList<>();
		boolean[] found = roomFound;
		for (int z = 0; z < CgIds.ROOM_GRID; z++)
		{
			for (int x = 0; x < CgIds.ROOM_GRID; x++)
			{
				if (found[z * CgIds.ROOM_GRID + x] || (x == CgIds.BOSS_ROOM_X && z == CgIds.BOSS_ROOM_Z))
				{
					continue; // known already, or the boss room (never explore into it)
				}
				// must border a found room, otherwise we can't path there yet
				if (isFound(x - 1, z) || isFound(x + 1, z) || isFound(x, z - 1) || isFound(x, z + 1))
				{
					candidates.add(new int[]{x, z});
				}
			}
		}
		return candidates.stream()
			.min(Comparator.comparingInt(c -> Math.abs(c[0] - roomX) + Math.abs(c[1] - roomZ)))
			.orElse(null);
	}

	// ------------------------------------------------------------------ ExplorePlanner.MapView

	@Override
	public boolean found(int x, int z)
	{
		return isFound(x, z);
	}

	@Override
	public int startX()
	{
		return startRoomX;
	}

	@Override
	public int startZ()
	{
		return startRoomZ;
	}

	@Override
	public int liveNodes(Resource r)
	{
		return (int) nodes.values().stream().filter(n -> n.type == r && alive(n)).count();
	}

	@Override
	public int liveNodesWithin(Resource r, int tiles)
	{
		int c = 0;
		for (Node n : nodes.values())
		{
			if (n.type == r && alive(n))
			{
				int w = walkTiles(n);
				if (w >= 0 && w <= tiles)
				{
					c++;
				}
			}
		}
		return c;
	}

	@Override
	public int tilesToNearestLiveNode(Resource r, boolean includeOuter)
	{
		WorldPoint me = playerRaw;
		if (me == null)
		{
			return -1;
		}
		return nodes.values().stream()
			.filter(n -> n.type == r && usable(n) && n.raw.getPlane() == me.getPlane())
			.filter(n -> includeOuter || !inOuterRing(n.raw))
			.mapToInt(this::walkTiles)
			.min().orElse(-1);
	}

	private boolean inOuterRing(WorldPoint raw)
	{
		int[] c = grid.roomOf(raw);
		return c != null && net.runelite.client.plugins.microbot.cgprep.plan.RoomPriors.ring(c[0], c[1])
			== net.runelite.client.plugins.microbot.cgprep.plan.RoomPriors.Ring.OUTER;
	}

	/** Is this a resource node id (live or depleted) for the mode? Used by the plugin's spawn events. */
	public static boolean isNodeObject(CgMode m, int id)
	{
		return id == m.nodeFor(Resource.ORE) || id == m.nodeFor(Resource.BARK) || id == m.nodeFor(Resource.LINUM)
			|| id == m.nodeFor(Resource.GRYM) || id == m.nodeFor(Resource.RAW_FISH)
			|| id == m.objRockDepleted || id == m.objTreeDepleted || id == m.objLinumDepleted
			|| id == m.objHerbDepleted || id == m.objPondDepleted;
	}

	private boolean isFound(int x, int z)
	{
		if (x < 0 || z < 0 || x >= CgIds.ROOM_GRID || z >= CgIds.ROOM_GRID)
		{
			return false;
		}
		return roomFound[z * CgIds.ROOM_GRID + x];
	}

	public boolean inStartRoom()
	{
		return startRoomX >= 0 && roomX == startRoomX && roomZ == startRoomZ;
	}

	public int startRoomX()
	{
		return startRoomX;
	}

	public int startRoomZ()
	{
		return startRoomZ;
	}

	public RoomGrid grid()
	{
		return grid;
	}

	public WorldPoint playerRaw()
	{
		return playerRaw;
	}

	public int roomX()
	{
		return roomX;
	}

	public int roomZ()
	{
		return roomZ;
	}

	public boolean inGauntlet()
	{
		return inGauntlet;
	}

	public boolean bossStarted()
	{
		return bossStarted;
	}

	public int foundRoomCount()
	{
		int c = 0;
		for (boolean b : roomFound)
		{
			if (b)
			{
				c++;
			}
		}
		return c;
	}

	// ------------------------------------------------------------------ snapshots (log + offline replay)

	public java.util.Collection<Node> allNodes()
	{
		return nodes.values();
	}

	public Map<Monster, Sighting> sightings()
	{
		return demiBosses;
	}

	public boolean[] foundRooms()
	{
		return roomFound.clone();
	}

	/**
	 * OFFLINE REPLAY ONLY: rebuild the maze from a SNAP log line (no client involved). Never called
	 * by the live bot.
	 */
	public void restoreForReplay(int startX, int startZ, int roomX, int roomZ, WorldPoint me, int originX, int originY,
		boolean[] found, List<Node> restoredNodes, Map<Monster, Sighting> seen, java.util.Set<Monster> killed)
	{
		resetForNewRun();
		startRoomX = startX;
		startRoomZ = startZ;
		this.roomX = roomX;
		this.roomZ = roomZ;
		playerRaw = me;
		inGauntlet = true;
		roomFound = found;
		lastFoundCount = foundRoomCount();
		lastNodeScanMs = Long.MAX_VALUE / 2;
		grid.calibrate(startX, startZ, new WorldPoint(originX + startX * CgIds.ROOM_SIZE_TILES, originY + startZ * CgIds.ROOM_SIZE_TILES, 0));
		for (Node n : restoredNodes)
		{
			nodes.put(key(n.raw), n);
		}
		demiBosses.putAll(seen);
		for (Monster m : killed)
		{
			demiBossKilled.put(m, true);
		}
	}

	/** OFFLINE REPLAY ONLY. */
	public static Node replayNode(Resource type, WorldPoint raw, boolean depleted, long avoidForMs)
	{
		Node n = new Node(type, raw);
		n.depleted = depleted;
		n.avoidUntilMs = avoidForMs > 0 ? System.currentTimeMillis() + avoidForMs : 0;
		return n;
	}

	/** OFFLINE REPLAY ONLY. */
	public static Sighting replaySighting(WorldPoint raw, long ageMs)
	{
		return new Sighting(raw, System.currentTimeMillis() - ageMs);
	}

	private static String key(WorldPoint p)
	{
		return p.getX() + "," + p.getY() + "," + p.getPlane();
	}
}
