package net.runelite.client.plugins.microbot.cgprep.log;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.cgprep.data.GearSlot;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.cgprep.data.PrepProfile;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;
import net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze;
import net.runelite.client.plugins.microbot.cgprep.plan.Ledger;
import net.runelite.client.plugins.microbot.cgprep.plan.Needs;
import net.runelite.client.plugins.microbot.cgprep.plan.PrepPlanner;

/**
 * Everything the planner saw for one decision, as one log line ("SNAP ..."), and back.
 *
 * The bot writes a SNAP line next to every GOAL line. The offline replay tool
 * (tools/sim/Replay.java) reads a run's log, rebuilds each moment and asks the CURRENT planner
 * what it would do, so a planner change can be checked against real runs before it goes in game.
 * Plain key=value tokens; lists use ';' and ':'. No client calls in here.
 */
public final class Snapshot
{
	/** Settings the planner depends on. */
	public static final class Cfg
	{
		public PrepProfile profile;
		public int fishTarget;
		public int potions;
		public boolean decant;
		public int shardBuffer;
		public int finishReserve;
		public int handoverReserve;
		public int demiBossBudget;
		public boolean skipDemi;
		public boolean vialsSmash;
	}

	public static final class Restored
	{
		public PrepPlanner.Inputs inputs;
		public Cfg cfg;
	}

	private Snapshot()
	{
	}

	/** Encode BEFORE planner.decide() (decide() adjusts finishReserve in place). */
	public static String encode(PrepPlanner.Inputs in, Cfg c, GauntletMaze m)
	{
		Ledger l = in.ledger;
		StringBuilder sb = new StringBuilder(1024);
		sb.append("t=").append(in.secondsLeft)
			.append(" hp=").append(in.hpPct).append(" pray=").append(in.prayPct)
			.append(" danger=").append(b(in.inDanger)).append(" flee=").append(b(in.flee))
			.append(" fbb=").append(b(in.fieldBrewBlocked))
			.append(" commit=").append(in.committedGather == null ? "-" : in.committedGather.name())
			.append(" weak=").append(b(in.weakNearby)).append(" tele=").append(b(in.hasTeleport))
			.append(" mobs=").append(in.weakTiles == Integer.MAX_VALUE ? -1 : in.weakTiles).append(",")
			.append(in.strongTiles == Integer.MAX_VALUE ? -1 : in.strongTiles).append(",").append(b(in.weakOnUs))
			.append(",").append(in.vialsMade)
			.append(",").append(in.demiFighting != null ? in.demiFighting.name() : in.mobFighting != null ? in.mobFighting.name() : "-")
			.append(" tools=").append(b(in.toolsMissing)).append(b(in.toolStorageKnown))
			.append(" seed=").append(in.runSeed);
		sb.append(" drop=");
		if (in.droppedRecently.isEmpty())
		{
			sb.append('-');
		}
		else
		{
			for (Resource r : in.droppedRecently)
			{
				sb.append(r.name()).append(';');
			}
		}
		sb.append(" cfg=").append(c.profile.name()).append(',').append(c.fishTarget).append(',').append(c.potions).append(',')
			.append(b(c.decant)).append(',').append(c.shardBuffer).append(',').append(c.finishReserve).append(',')
			.append(c.handoverReserve).append(',').append(c.demiBossBudget).append(',').append(b(c.skipDemi))
			.append(',').append(b(c.vialsSmash));
		sb.append(" have=");
		for (Resource r : Resource.values())
		{
			sb.append(r.name()).append(':').append(l.get(r)).append(',');
		}
		sb.setLength(sb.length() - 1);
		sb.append(" inv=").append(l.dust).append(',').append(l.cookedFish).append(',').append(l.emptyVials).append(',')
			.append(l.waterVials).append(',').append(l.unfinishedPotions).append(',').append(l.freeSlots).append(',')
			.append(l.dosesDrunk);
		sb.append(" doses=").append(l.doseCounts[1]).append(',').append(l.doseCounts[2]).append(',')
			.append(l.doseCounts[3]).append(',').append(l.doseCounts[4]);
		sb.append(" flags=").append(b(l.pickaxe)).append(b(l.axe)).append(b(l.harpoon)).append(b(l.pestle))
			.append(b(l.armourInInventory)).append(b(l.weaponEquipped)).append(b(l.gearOnGround));
		sb.append(" tier=");
		for (GearSlot g : GearSlot.values())
		{
			sb.append(g.name()).append(':').append(l.tier.getOrDefault(g, 0)).append(',');
		}
		sb.setLength(sb.length() - 1);
		WorldPoint me = m.playerRaw();
		sb.append(" start=").append(m.startRoomX()).append(',').append(m.startRoomZ())
			.append(" room=").append(m.roomX()).append(',').append(m.roomZ())
			.append(" me=").append(me == null ? "-" : me.getX() + "," + me.getY() + "," + me.getPlane())
			.append(" origin=").append(m.grid().originX()).append(',').append(m.grid().originY());
		sb.append(" found=");
		for (boolean f : m.foundRooms())
		{
			sb.append(f ? '1' : '0');
		}
		long now = System.currentTimeMillis();
		sb.append(" nodes=");
		int count = 0;
		for (GauntletMaze.Node n : m.allNodes())
		{
			long avoid = Math.max(0, n.avoidUntilMs - now);
			sb.append(n.type.name()).append(':').append(n.raw.getX()).append(':').append(n.raw.getY()).append(':')
				.append(n.raw.getPlane()).append(':').append(b(n.depleted)).append(':').append(avoid).append(';');
			count++;
		}
		if (count == 0)
		{
			sb.append('-');
		}
		sb.append(" demi=");
		count = 0;
		for (Map.Entry<Monster, GauntletMaze.Sighting> e : m.sightings().entrySet())
		{
			WorldPoint p = e.getValue().raw;
			sb.append(e.getKey().name()).append(':').append(p.getX()).append(':').append(p.getY()).append(':')
				.append(p.getPlane()).append(':').append(now - e.getValue().atMs).append(';');
			count++;
		}
		if (count == 0)
		{
			sb.append('-');
		}
		sb.append(" killed=");
		count = 0;
		for (Monster mo : new Monster[]{Monster.BEAR, Monster.DARK_BEAST, Monster.DRAGON})
		{
			if (m.isKilled(mo))
			{
				sb.append(mo.name()).append(';');
				count++;
			}
		}
		if (count == 0)
		{
			sb.append('-');
		}
		return sb.toString();
	}

	/** Rebuild planner inputs (with a restored maze) from a SNAP line's body. */
	public static Restored decode(String body)
	{
		Map<String, String> kv = new HashMap<>();
		for (String tok : body.trim().split(" "))
		{
			int i = tok.indexOf('=');
			if (i > 0)
			{
				kv.put(tok.substring(0, i), tok.substring(i + 1));
			}
		}
		Cfg c = new Cfg();
		String[] cf = kv.get("cfg").split(",");
		c.profile = PrepProfile.valueOf(cf[0]);
		c.fishTarget = Integer.parseInt(cf[1]);
		c.potions = Integer.parseInt(cf[2]);
		c.decant = cf[3].equals("1");
		c.shardBuffer = Integer.parseInt(cf[4]);
		c.finishReserve = Integer.parseInt(cf[5]);
		c.handoverReserve = Integer.parseInt(cf[6]);
		c.demiBossBudget = Integer.parseInt(cf[7]);
		c.skipDemi = cf[8].equals("1");
		c.vialsSmash = cf.length > 9 && cf[9].equals("1"); // added in 0.3.20

		Ledger l = new Ledger();
		for (String p : kv.get("have").split(","))
		{
			String[] a = p.split(":");
			l.have.put(Resource.valueOf(a[0]), Integer.parseInt(a[1]));
		}
		int[] inv = ints(kv.get("inv"));
		l.dust = inv[0];
		l.cookedFish = inv[1];
		l.emptyVials = inv[2];
		l.waterVials = inv[3];
		l.unfinishedPotions = inv[4];
		l.freeSlots = inv[5];
		l.dosesDrunk = inv[6];
		int[] d = ints(kv.get("doses"));
		l.potions = 0;
		l.potionDoses = 0;
		for (int i = 0; i < 4; i++)
		{
			l.doseCounts[i + 1] = d[i];
			l.potions += d[i];
			l.potionDoses += d[i] * (i + 1);
		}
		String f = kv.get("flags");
		l.pickaxe = f.charAt(0) == '1';
		l.axe = f.charAt(1) == '1';
		l.harpoon = f.charAt(2) == '1';
		l.pestle = f.charAt(3) == '1';
		l.armourInInventory = f.charAt(4) == '1';
		l.weaponEquipped = f.charAt(5) == '1';
		l.gearOnGround = f.charAt(6) == '1';
		for (String p : kv.get("tier").split(","))
		{
			String[] a = p.split(":");
			l.tier.put(GearSlot.valueOf(a[0]), Integer.parseInt(a[1]));
		}

		int[] start = ints(kv.get("start"));
		int[] room = ints(kv.get("room"));
		int[] origin = ints(kv.get("origin"));
		String meS = kv.get("me");
		WorldPoint me = meS.equals("-") ? null : pt(ints(meS));
		String fs = kv.get("found");
		boolean[] found = new boolean[fs.length()];
		for (int i = 0; i < fs.length(); i++)
		{
			found[i] = fs.charAt(i) == '1';
		}
		List<GauntletMaze.Node> nodes = new ArrayList<>();
		if (!kv.get("nodes").equals("-"))
		{
			for (String p : kv.get("nodes").split(";"))
			{
				if (p.isEmpty())
				{
					continue;
				}
				String[] a = p.split(":");
				nodes.add(GauntletMaze.replayNode(Resource.valueOf(a[0]),
					new WorldPoint(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3])),
					a[4].equals("1"), Long.parseLong(a[5])));
			}
		}
		Map<Monster, GauntletMaze.Sighting> seen = new EnumMap<>(Monster.class);
		if (!kv.get("demi").equals("-"))
		{
			for (String p : kv.get("demi").split(";"))
			{
				if (p.isEmpty())
				{
					continue;
				}
				String[] a = p.split(":");
				seen.put(Monster.valueOf(a[0]), GauntletMaze.replaySighting(
					new WorldPoint(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3])), Long.parseLong(a[4])));
			}
		}
		Set<Monster> killed = EnumSet.noneOf(Monster.class);
		if (!kv.get("killed").equals("-"))
		{
			for (String p : kv.get("killed").split(";"))
			{
				if (!p.isEmpty())
				{
					killed.add(Monster.valueOf(p));
				}
			}
		}
		GauntletMaze maze = new GauntletMaze();
		maze.restoreForReplay(start[0], start[1], room[0], room[1], me, origin[0], origin[1], found, nodes, seen, killed);

		PrepPlanner.Inputs in = new PrepPlanner.Inputs();
		in.ledger = l;
		in.needs = Needs.compute(l, PrepPlanner.profileNow(l, c.profile), PrepPlanner.fishTargetNow(l, c.profile, c.fishTarget), c.shardBuffer, c.potions, c.decant, c.vialsSmash);
		in.potionTarget = c.potions;
		in.fishLater = PrepPlanner.fishTargetNow(l, c.profile, c.fishTarget) < c.fishTarget;
		in.maze = maze;
		in.profile = PrepPlanner.profileNow(l, c.profile);
		in.armourLater = in.profile != c.profile;
		in.secondsLeft = Integer.parseInt(kv.get("t"));
		in.finishReserve = c.finishReserve;
		in.handoverReserve = c.handoverReserve;
		in.demiBossBudget = c.demiBossBudget;
		in.skipDemiBosses = c.skipDemi;
		in.hpPct = Integer.parseInt(kv.get("hp"));
		in.prayPct = Integer.parseInt(kv.get("pray"));
		in.inDanger = kv.get("danger").equals("1");
		in.flee = kv.get("flee").equals("1");
		in.fieldBrewBlocked = kv.get("fbb").equals("1");
		in.committedGather = kv.get("commit").equals("-") ? null : Resource.valueOf(kv.get("commit"));
		in.weakNearby = kv.get("weak").equals("1");
		String mobs = kv.get("mobs");
		if (mobs != null)
		{
			String[] mv = mobs.split(",");
			int wt = Integer.parseInt(mv[0]);
			int st = Integer.parseInt(mv[1]);
			in.weakTiles = wt < 0 ? Integer.MAX_VALUE : wt;
			in.strongTiles = st < 0 ? Integer.MAX_VALUE : st;
			in.weakOnUs = mv[2].equals("1");
			in.vialsMade = mv.length > 3 ? Integer.parseInt(mv[3]) : 0;
			if (mv.length > 4 && !mv[4].equals("-"))
			{
				Monster fm = Monster.valueOf(mv[4]);
				if (fm == Monster.WEAK || fm == Monster.STRONG)
				{
					in.mobFighting = fm;
				}
				else
				{
					in.demiFighting = fm;
				}
			}
		}
		else
		{
			in.weakTiles = in.weakNearby ? 10 : Integer.MAX_VALUE; // older logs: only "within 15"
			in.vialsMade = 0;
		}
		in.hasTeleport = kv.get("tele").equals("1");
		String tools = kv.get("tools");
		in.toolsMissing = tools.charAt(0) == '1';
		in.toolStorageKnown = tools.charAt(1) == '1';
		in.runSeed = Integer.parseInt(kv.get("seed"));
		String drop = kv.get("drop");
		if (drop != null && !drop.equals("-"))
		{
			Set<Resource> dropped = EnumSet.noneOf(Resource.class);
			for (String p : drop.split(";"))
			{
				if (!p.isEmpty())
				{
					dropped.add(Resource.valueOf(p));
				}
			}
			in.droppedRecently = dropped;
		}
		Restored r = new Restored();
		r.inputs = in;
		r.cfg = c;
		return r;
	}

	private static String b(boolean v)
	{
		return v ? "1" : "0";
	}

	private static int[] ints(String s)
	{
		String[] a = s.split(",");
		int[] out = new int[a.length];
		for (int i = 0; i < a.length; i++)
		{
			out[i] = Integer.parseInt(a[i]);
		}
		return out;
	}

	private static WorldPoint pt(int[] a)
	{
		return new WorldPoint(a[0], a[1], a[2]);
	}
}
