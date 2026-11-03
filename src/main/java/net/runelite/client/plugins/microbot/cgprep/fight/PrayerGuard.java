package net.runelite.client.plugins.microbot.cgprep.fight;

import java.util.List;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.cgprep.data.CgMode;
import net.runelite.client.plugins.microbot.cgprep.data.Monster;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Prayers during prep, re-decided every game tick (user, 0.3.13):
 *
 * DEFENSIVE = only while we're being attacked. A monster becomes "engaged" the moment it attacks
 * us: it targets us and is in reach (melee monsters within 2 tiles; demi-bosses at any range,
 * they hit with magic/ranged). An engaged monster stays engaged while we kite it, and is let go
 * only once it has been more than 10 tiles away for 5 ticks in a row (or died, or stopped
 * targeting us for 4 ticks). Monsters that merely come into view don't count until they attack;
 * when one does, it's simply added (the "being attacked right here" case).
 * Demi-bosses only while they're actually attacking (an attack in the last 5 ticks, or the Bear
 * next to us), see Engagement; when we attack one on purpose, its prayer stays up for the fight.
 * Style: a demi-boss's style if one is engaged (they hit up to 48), else Protect from Melee
 * (every other Gauntlet monster is melee). When we deliberately attack a demi-boss, its style
 * goes up as we start, so its first hit is blocked.
 *
 * OFFENSIVE = only while we're deliberately attacking something for its loot (the script sets
 * {@link #attackingOnPurpose} for its FIGHT goal) and only for that weapon's style. Off the
 * moment we stop.
 *
 * Points: none at all -> no clicks. Under 10% with no potion dose to spare: protection only
 * against a demi-boss (weak hits can be eaten through), no offensive prayer.
 */
public final class PrayerGuard
{
	/** How prayer clicks go out (natural mouse on the prayer tab); null = Rs2Prayer.toggle. */
	public interface Clicker
	{
		/** Click each prayer once (each click toggles it), opening the prayer tab first. Returns how many went out. */
		int toggle(java.util.List<Rs2PrayerEnum> prayers, boolean urgent) throws InterruptedException;

		/** No prayer clicks this tick: put the inventory back once nothing has needed praying for a moment. */
		boolean idle(boolean prayingNow) throws InterruptedException;
	}

	/** Set by the script: the natural prayer input, or null for direct toggles. */
	public volatile Clicker clicker;
	/** Set by the script: true for a (raw instance) tile in the start room, where monsters can't follow. */
	public volatile java.util.function.Predicate<net.runelite.api.coords.WorldPoint> safeRoom;
	/** A prayer click (prayer + on/off) sent recently: tick it went out. A 2nd click before it shows would undo it. */
	private final java.util.Map<String, Integer> sentAt = new java.util.HashMap<>();

	private final Engagement engagement = new Engagement();
	private final OffenseFlick flick = new OffenseFlick();
	private java.util.Map<Integer, int[]> prevAnims = new java.util.HashMap<>();
	/** Lazy-flick the offensive prayer (setting "lazyFlick"), set by the script. */
	public volatile boolean lazyFlick = true;
	private boolean flickLogged;
	private volatile Rs2PrayerEnum on;
	private volatile String lastReason = "-";
	private volatile Rs2PrayerEnum offensive;
	/** A demi-boss is within 12 tiles (for the planner/overlay). */
	public volatile boolean demiInSight;
	/** Monsters attacking us right now (in reach and targeting us). */
	public volatile int attackersNear;
	/** Monsters still engaged (attacking, or being kited). */
	public volatile int engagedCount;
	/** The demi-boss engaged with us (attacking or chasing), or null. */
	public volatile Monster demiOnUs;
	/** A potion dose can be spared during prep (set by the script). */
	public volatile boolean potionSpare;
	/** Set by the script: our FIGHT goal's target is being attacked by us right now. */
	public volatile boolean attackingOnPurpose;
	/** Set by the script: what we're deliberately fighting (null = nothing). */
	public volatile Monster fightTarget;
	/** Set by the script: the wielded weapon's offensive prayer family. */
	public volatile Style weaponStyle = Style.MELEE; // the starting sceptre

	/**
	 * Demi-bosses get their protection BEFORE their first attack (1.2.0, user: 30-45 hits landed
	 * on the way in, mostly while exploring: the Dark Beast / Dragon shot first and only then did
	 * the bot pray). The Dragon and the Dark Beast hit from ~10 tiles (Engagement.RANGED_DEMI_REACH);
	 * 2 more cover a tick of running in and the prayer click (tab key + move + click, ~0.5 s). The
	 * one we're walking in to fight: 16 tiles, for as long as it takes.
	 */
	static final int APPROACH_TILES = 12;
	static final int APPROACH_TILES_TARGET = 16;
	/**
	 * The Bear only hits from next to us and walks a tile a tick: 4 tiles leaves 3 ticks for the
	 * click (1.2.1: 1.2.0 prayed melee for a Bear 12 tiles off twice, on the last points).
	 */
	static final int BEAR_APPROACH_TILES = 4;
	/** Ticks a demi-boss we're NOT fighting keeps the prayer up without attacking (9 s). */
	static final int AHEAD_TICKS = 15;
	/** First tick each demi-boss came within reach (NPC index -> tick). */
	private final java.util.Map<Integer, Integer> aheadSince = new java.util.HashMap<>();

	public enum Style
	{
		MAGIC, RANGED, MELEE
	}

	public void reset()
	{
		on = null;
		offensive = null;
		demiInSight = false;
		attackersNear = 0;
		engagedCount = 0;
		demiOnUs = null;
		attackingOnPurpose = false;
		fightTarget = null;
		lastReason = "-";
		sentAt.clear();
		engagement.clear();
		prevAnims = new java.util.HashMap<>();
		aheadSince.clear();
		flick.reset();
		flickLogged = false;
	}

	public String status()
	{
		return on == null ? "off" : on.name() + " (" + lastReason + ")";
	}

	/**
	 * Re-decide; returns a description of what was toggled, or null. Call once per game tick, on
	 * the prayer thread (it clicks).
	 */
	public String tick(CgMode mode) throws InterruptedException
	{
		WorldScan scan = WorldScan.latest();
		if (scan == null || System.currentTimeMillis() - scan.atMs > 1500)
		{
			return null; // no fresh tick read (not logged in / between scenes)
		}
		// 1. Who is attacking us / still being kited (see Engagement). True tile distances.
		boolean demiNear = false;
		// 1.2.0 (user): a demi-boss close enough to reach us soon is prayed against BEFORE its first
		// hit (see APPROACH_TILES).
		Monster ahead = null;
		int aheadTiles = Integer.MAX_VALUE;
		Monster goal = fightTarget;
		java.util.Set<Integer> nearDemis = new java.util.HashSet<>();
		java.util.List<Engagement.Seen> seen = new java.util.ArrayList<>();
		java.util.Set<Integer> alive = new java.util.HashSet<>();
		java.util.Map<Integer, int[]> anims = new java.util.HashMap<>();
		for (WorldScan.Npc a : scan.npcs)
		{
			if (a.dead)
			{
				continue;
			}
			alive.add(a.index);
			Monster m = classify(mode, a.id);
			if (m == null)
			{
				continue;
			}
			demiNear |= Engagement.isDemi(m) && a.tiles <= 12;
			int reach = m == Monster.BEAR ? BEAR_APPROACH_TILES : m == goal ? APPROACH_TILES_TARGET : APPROACH_TILES;
			if (Engagement.isDemi(m) && a.tiles <= reach)
			{
				nearDemis.add(a.index);
				int first = aheadSince.computeIfAbsent(a.index, k -> scan.tick);
				// One we're not fighting that hasn't attacked after a while is left alone (0.3.25: a
				// Dragon in the next room kept the prayer on for a minute). Engagement takes over
				// the moment it attacks.
				boolean stale = m != goal && scan.tick - first > AHEAD_TICKS;
				if (!stale && a.tiles < aheadTiles)
				{
					ahead = m;
					aheadTiles = a.tiles;
				}
			}
			// An attack = its animation (re)starting this tick (walking isn't an animation).
			int[] prev = prevAnims.get(a.index);
			boolean started = a.anim != -1 && (prev == null || prev[0] != a.anim || a.animFrame < prev[1]);
			anims.put(a.index, new int[]{a.anim, a.animFrame});
			seen.add(new Engagement.Seen(a.index, m, a.tiles, a.targetingUs, started));
		}
		aheadSince.keySet().retainAll(nearDemis);
		engagement.tick(seen, alive);
		prevAnims = anims;
		demiInSight = demiNear;
		int now = engagement.attackingNow();
		attackersNear = now;
		engagedCount = engagement.engagedCount();
		demiOnUs = engagement.engagedDemi(null);

		int prayPct = scan.prayerPct();
		if (scan.prayer <= 0)
		{
			on = null;
			offensive = null;
			return null; // nothing to pray with (0.3.12 clicked every tick at 0)
		}
		boolean scarce = prayPct < 10 && !potionSpare;

		// 2. Defensive. A demi-boss on us always decides the protection, over every other rule (user).
		Rs2PrayerEnum want = null;
		String why = null;
		Monster target = fightTarget;
		Monster demiEngaged = engagement.engagedDemi(attackingOnPurpose ? target : null);
		int melee = engagement.engagedMelee();
		if (demiEngaged == null && target != null && target != Monster.WEAK && target != Monster.STRONG && attackingOnPurpose)
		{
			demiEngaged = target; // we started it: its first hit should already be blocked
		}
		if (demiEngaged != null)
		{
			want = demiEngaged.protection;
			why = demiEngaged + (now > 0 ? " attacking" : " engaged") + (melee > 0 ? " (+" + melee + " melee)" : "");
		}
		else if (ahead != null)
		{
			want = ahead.protection;
			why = ahead + " " + aheadTiles + " tiles away: up before it can hit" + (melee > 0 ? " (+" + melee + " melee)" : "");
		}
		else if (melee > 0 && !scarce)
		{
			want = Monster.WEAK.protection;
			why = melee + " melee engaged" + (now < melee ? ", " + (melee - now) + " being kited" : "");
		}

		// 3. Offensive: only while deliberately attacking, and with points for it. Lazy-flicked
		// (user, 0.3.24): on the tick before each swing, off on the swing tick (see OffenseFlick),
		// which costs so little that it's used down to 10% prayer.
		Rs2PrayerEnum wantOff = null;
		boolean flicking = false;
		Rs2PrayerEnum best = weaponStyle == Style.RANGED ? scan.bestRange : weaponStyle == Style.MELEE ? scan.bestMelee : scan.bestMage;
		if (attackingOnPurpose && target != null && !scarce && best != null && (lazyFlick ? prayPct >= 10 : prayPct >= 30 || potionSpare))
		{
			wantOff = best;
			if (lazyFlick)
			{
				flicking = true;
				int t = -1;
				for (WorldScan.Npc a : scan.npcs)
				{
					if (a.index == scan.interactingIndex)
					{
						t = a.tiles;
					}
				}
				boolean inRange = t >= 0 && (weaponStyle == Style.MELEE ? t <= 1 : t <= 9);
				if (!flick.onTick(scan.tick, scan.anim, scan.animFrame, scan.interactingIndex, inRange, weaponStyle))
				{
					wantOff = null;
				}
			}
		}
		else
		{
			flick.reset();
			flickLogged = false;
		}

		// 4. The start room: monsters can't come in, so nothing is prayed there and whatever is on
		// goes off (user, 0.4.7).
		java.util.function.Predicate<net.runelite.api.coords.WorldPoint> safe = safeRoom;
		boolean inSafeRoom = safe != null && scan.me != null && safe.test(scan.me);
		if (inSafeRoom)
		{
			want = null;
			why = null;
			wantOff = null;
			if (flicking)
			{
				flick.reset();
				flicking = false;
			}
		}

		// 5. Apply: the clicks needed to get from what's on to what's wanted. Each click toggles, so
		// one that went out in the last 2 ticks and doesn't show yet isn't sent again.
		StringBuilder did = new StringBuilder();
		java.util.List<Rs2PrayerEnum> clicks = new java.util.ArrayList<>();
		String offWhy = inSafeRoom ? "in the start room (monsters can't come in)" : "nothing engaged";
		if (want == null)
		{
			for (Rs2PrayerEnum p : new Rs2PrayerEnum[]{Rs2PrayerEnum.PROTECT_MELEE, Rs2PrayerEnum.PROTECT_RANGE, Rs2PrayerEnum.PROTECT_MAGIC})
			{
				if (scan.active.contains(p) && queue(clicks, p, false, scan.tick))
				{
					did.append("pray ").append(p.name()).append(" off: ").append(offWhy).append("; ");
				}
			}
		}
		else if (!scan.active.contains(want) && queue(clicks, want, true, scan.tick))
		{
			did.append("pray ").append(want.name()).append(" on: ").append(why).append("; "); // replaces the old protection
		}
		boolean urgent = !clicks.isEmpty() && want != null;
		on = want;
		lastReason = why == null ? "-" : why;
		for (Rs2PrayerEnum off : new Rs2PrayerEnum[]{scan.bestMage, scan.bestRange, scan.bestMelee})
		{
			if (off != null && off != wantOff && scan.active.contains(off) && queue(clicks, off, false, scan.tick) && !flicking)
			{
				did.append("pray ").append(off.name()).append(" off: ")
					.append(inSafeRoom ? offWhy : attackingOnPurpose ? "saving points (" + prayPct + "%)" : "not attacking").append("; ");
			}
		}
		if (wantOff != null && !scan.active.contains(wantOff) && queue(clicks, wantOff, true, scan.tick) && !flicking)
		{
			did.append("pray ").append(wantOff.name()).append(" on: attacking ").append(target).append("; ");
		}
		if (flicking && !flickLogged)
		{
			flickLogged = true;
			did.append("lazy-flicking ").append(best.name()).append(" on ").append(target).append(" (on the tick before each swing); ");
		}
		Clicker c = clicker;
		if (!clicks.isEmpty())
		{
			if (c != null)
			{
				int sent = c.toggle(clicks, urgent);
				if (sent < clicks.size())
				{
					did.append("(").append(clicks.size() - sent).append(" of the prayer clicks didn't go out); ");
				}
			}
			else
			{
				for (Rs2PrayerEnum p : clicks)
				{
					Rs2Prayer.toggle(p); // one click, no wait
				}
			}
		}
		else if (c != null)
		{
			boolean praying = want != null || wantOff != null || flicking || !scan.active.isEmpty();
			if (c.idle(praying))
			{
				did.append("prayer tab closed: nothing to pray against; ");
			}
		}
		offensive = wantOff;
		return did.length() == 0 ? null : did.substring(0, did.length() - 2);
	}

	/** Queue a click on this prayer unless the same click went out in the last 2 ticks. */
	private boolean queue(java.util.List<Rs2PrayerEnum> clicks, Rs2PrayerEnum p, boolean turnOn, int tick)
	{
		String key = p.name() + (turnOn ? "+" : "-");
		Integer at = sentAt.get(key);
		if (at != null && tick - at < 2 && tick >= at)
		{
			return false;
		}
		sentAt.put(key, tick);
		clicks.add(p);
		return true;
	}

	private static Monster classify(CgMode mode, int id)
	{
		for (Monster m : Monster.values())
		{
			for (int x : m.ids(mode))
			{
				if (x == id)
				{
					return m;
				}
			}
		}
		return null;
	}

}
