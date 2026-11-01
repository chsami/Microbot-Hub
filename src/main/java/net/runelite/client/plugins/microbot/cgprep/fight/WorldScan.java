package net.runelite.client.plugins.microbot.cgprep.fight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * One read of everything the per-tick logic needs, taken ON THE CLIENT THREAD once per game tick
 * (plugin onGameTick). 0.3.16 PERF: observe took 300-700 ms per loop because every Microbot NPC /
 * skill / prayer call made from the script thread waits for the client thread's next frame
 * (runOnClientThreadOptional runs inline only when already on it); the demi-boss scan alone took
 * 236-360 ms. Reading it all here costs one frame's worth of work and no waiting.
 *
 * Distances are true tiles to the NPC's footprint (Chebyshev; next to any size of NPC = 1).
 * Microbot's Rs2NpcModel.getDistanceFromPlayer() is in LOCAL units (128 per tile), which is why
 * the 0.3.14-0.3.16 prayer logic never saw a melee monster "within 2 tiles".
 */
public final class WorldScan
{
	public static final class Npc
	{
		public final int index;
		public final int id;
		public final WorldPoint raw;
		public final int size;
		/** Tiles from the player to the nearest tile of the NPC (1 = next to it). */
		public final int tiles;
		public final boolean targetingUs;
		public final boolean dead;
		/** Its animation and frame this tick (attack starts: see PrayerGuard). */
		public int anim = -1;
		public int animFrame;

		Npc(int index, int id, WorldPoint raw, int size, int tiles, boolean targetingUs, boolean dead)
		{
			this.index = index;
			this.id = id;
			this.raw = raw;
			this.size = size;
			this.tiles = tiles;
			this.targetingUs = targetingUs;
			this.dead = dead;
		}
	}

	public final List<Npc> npcs;
	public final WorldPoint me;
	public final int hp;
	public final int hpMax;
	public final int prayer;
	public final int prayerMax;
	public final Set<Rs2PrayerEnum> active;
	public final Rs2PrayerEnum bestMage;
	public final Rs2PrayerEnum bestRange;
	public final Rs2PrayerEnum bestMelee;
	public final long atMs;
	/** Game tick, our animation (+ frame) and the NPC index we're interacting with (-1 = none). */
	public int tick;
	public int anim = -1;
	public int animFrame;
	public int interactingIndex = -1;
	/** Run energy 0-100 and whether run is switched on. */
	public int runEnergy = 100;
	public boolean runOn = true;

	private static volatile WorldScan latest;
	/** Last time (ms) we saw the player move or animate (see {@link #lastActiveMs()}). */
	private static volatile long lastActiveMs;
	private static WorldPoint prevMe;

	private WorldScan(List<Npc> npcs, WorldPoint me, int hp, int hpMax, int prayer, int prayerMax, Set<Rs2PrayerEnum> active,
		Rs2PrayerEnum bestMage, Rs2PrayerEnum bestRange, Rs2PrayerEnum bestMelee)
	{
		this.npcs = npcs;
		this.me = me;
		this.hp = hp;
		this.hpMax = hpMax;
		this.prayer = prayer;
		this.prayerMax = prayerMax;
		this.active = active;
		this.bestMage = bestMage;
		this.bestRange = bestRange;
		this.bestMelee = bestMelee;
		this.atMs = System.currentTimeMillis();
	}

	/** The last scan (null before the first tick). */
	public static WorldScan latest()
	{
		return latest;
	}

	public int hpPct()
	{
		return hp * 100 / Math.max(1, hpMax);
	}

	public int prayerPct()
	{
		return prayer * 100 / Math.max(1, prayerMax);
	}

	/** CLIENT THREAD ONLY (plugin onGameTick). */
	public static WorldScan capture(Client client)
	{
		Player p = client.getLocalPlayer();
		if (p == null || client.getTopLevelWorldView() == null)
		{
			return latest;
		}
		WorldPoint me = p.getWorldLocation(); // RAW instance coords
		List<Npc> list = new ArrayList<>();
		for (NPC n : client.getTopLevelWorldView().npcs())
		{
			if (n == null)
			{
				continue;
			}
			WorldPoint w = n.getWorldLocation();
			NPCComposition c = n.getTransformedComposition();
			int size = c == null ? 1 : Math.max(1, c.getSize());
			int tiles = Integer.MAX_VALUE;
			if (w != null && me != null && w.getPlane() == me.getPlane())
			{
				int dx = Math.max(0, Math.max(w.getX() - me.getX(), me.getX() - (w.getX() + size - 1)));
				int dy = Math.max(0, Math.max(w.getY() - me.getY(), me.getY() - (w.getY() + size - 1)));
				tiles = Math.max(dx, dy);
			}
			Npc x = new Npc(n.getIndex(), n.getId(), w, size, tiles, n.getInteracting() == p, n.isDead());
			x.anim = n.getAnimation();
			x.animFrame = n.getAnimationFrame();
			list.add(x);
		}
		Set<Rs2PrayerEnum> active = EnumSet.noneOf(Rs2PrayerEnum.class);
		Rs2PrayerEnum mage = null;
		Rs2PrayerEnum range = null;
		Rs2PrayerEnum melee = null;
		try
		{
			for (Rs2PrayerEnum e : new Rs2PrayerEnum[]{Rs2PrayerEnum.PROTECT_MELEE, Rs2PrayerEnum.PROTECT_RANGE, Rs2PrayerEnum.PROTECT_MAGIC})
			{
				if (Rs2Prayer.isPrayerActive(e))
				{
					active.add(e);
				}
			}
			// The player's picks (settings, 1.3.0), falling back to the best unlocked.
			mage = OffensePrayers.mage();
			range = OffensePrayers.range();
			melee = OffensePrayers.melee();
			for (Rs2PrayerEnum e : new Rs2PrayerEnum[]{mage, range, melee})
			{
				if (e != null && Rs2Prayer.isPrayerActive(e))
				{
					active.add(e);
				}
			}
		}
		catch (Exception ignored)
		{
			// prayer book not available this tick: no prayer info
		}
		WorldScan s = new WorldScan(Collections.unmodifiableList(list), me,
			client.getBoostedSkillLevel(Skill.HITPOINTS), client.getRealSkillLevel(Skill.HITPOINTS),
			client.getBoostedSkillLevel(Skill.PRAYER), client.getRealSkillLevel(Skill.PRAYER),
			Collections.unmodifiableSet(active), mage, range, melee);
		s.runEnergy = Math.max(0, Math.min(100, client.getEnergy() / 100));
		s.tick = client.getTickCount();
		s.anim = p.getAnimation();
		s.animFrame = p.getAnimationFrame();
		net.runelite.api.Actor target = p.getInteracting();
		s.interactingIndex = target instanceof NPC ? ((NPC) target).getIndex() : -1;
		try
		{
			s.runOn = net.runelite.client.plugins.microbot.util.player.Rs2Player.isRunEnabled();
		}
		catch (Exception ignored)
		{
			// keep the default
		}
		if (me != null && !me.equals(prevMe) || s.anim != -1)
		{
			lastActiveMs = s.atMs;
		}
		prevMe = me;
		latest = s;
		return s;
	}

	/**
	 * The last game tick (ms) on which the player moved a tile or had an animation going. A wait
	 * for a walk-to-object click can stop as soon as this goes stale: the walk was interrupted
	 * (0.4.6: an inventory click cancelled the walk to a doorway and the loop waited out 10 s).
	 */
	public static long lastActiveMs()
	{
		return lastActiveMs;
	}

	/** Testing only. */
	public static WorldScan forTest(List<Npc> npcs, int hpPct, int prayerPct)
	{
		WorldScan s = new WorldScan(npcs, null, hpPct, 100, prayerPct, 100, EnumSet.noneOf(Rs2PrayerEnum.class), null, null, null);
		latest = s;
		return s;
	}

	/** Testing only. */
	public static Npc testNpc(int index, int id, int tiles, boolean targetingUs)
	{
		return new Npc(index, id, null, 1, tiles, targetingUs, false);
	}
}
