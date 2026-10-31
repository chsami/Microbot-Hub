package net.runelite.client.plugins.microbot.cgprep.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What we know about the fight, updated once per game tick from a {@link FightObs}. Pure Java: the
 * same code runs in game and in the simulator.
 *
 * Boss (recon-confirmed, 7 Corrupted fights): an attack animation every 5 ticks; the first style is
 * RANGED; after 4 attacks (a tornado summon or a prayer-disable orb counts) it switches style and
 * plays a transition animation 2 ticks after the 4th (authoritative here). A standard hit lands
 * {@link Arena#bossHitDelay} ticks after the animation. Its overhead switches to the style of our
 * 6th off-prayer swing, on the tick of that swing.
 */
public final class FightModel
{
	/** A boss hit on its way to us. */
	public static final class Pending
	{
		public final int animTick;
		public final int landTick;
		public Style style;
		public boolean disable;

		Pending(int animTick, int landTick, Style style)
		{
			this.animTick = animTick;
			this.landTick = landTick;
			this.style = style;
		}

		/**
		 * Last tick the protection for this hit must stay up. The hit lands 1 or 2 ticks after the
		 * animation depending on where we stood when it was thrown (we may have just moved), so the
		 * prayer is held for 2 ticks regardless: the next attack can't land before anim + 6.
		 */
		public int holdUntil()
		{
			return animTick + 2;
		}
	}

	public FightObs o;
	public int tick = -1;
	/** Our tile at the end of the previous tick (= at the start of this one). */
	public int prevPx;
	public int prevPy;

	// ---- boss attack cycle
	public int lastAttackTick = -1;
	public int firstAttackTick = -1;
	public Style nextStyle = Style.RANGED;
	public int attacksThisStyle;
	public int attacksTotal;
	public int lastSummonTick = -100;
	public final List<Pending> pending = new ArrayList<>();
	/** Prediction bookkeeping (style we expected vs projectile seen). */
	public int styleChecks;
	public int styleMisses;

	// ---- overhead and our swings
	public Style protects;
	public int offHits;
	public int readyTick = -1;
	public int lastSwingTick = -100;
	public int swings;
	private int prevFish = -1;
	private int lastEatTick = -100;
	private Style lastSwingWeapon;
	/** Ticks between swings per weapon: 4 (staff, bow on Rapid); learned as 5 if the bow is on Accurate. */
	private final int[] speed = {4, 4, 4};
	private final int[] slowSeen = new int[3];

	// ---- tornadoes: NPC index -> tick first seen
	public final Map<Integer, Integer> torSeen = new HashMap<>();

	// ---- floor
	public int warnStart = -1;
	public int warnDur;
	public int hitStart = -1;
	public int hitDur;
	public int patterns;
	private boolean hadWarn;
	private boolean hadHit;

	public void update(FightObs ob)
	{
		if (o == null)
		{
			prevPx = ob.px;
			prevPy = ob.py;
		}
		else
		{
			prevPx = o.px;
			prevPy = o.py;
		}
		o = ob;
		tick = ob.tick;
		boss(ob);
		us(ob);
		tornadoes(ob);
		floor(ob);
	}

	private void boss(FightObs ob)
	{
		pending.removeIf(p -> p.holdUntil() < tick);
		int a = ob.bossAnim;
		if (a == FightObs.ANIM_ATTACK || a == FightObs.ANIM_TORNADO)
		{
			if (firstAttackTick < 0)
			{
				firstAttackTick = tick;
			}
			lastAttackTick = tick;
			attacksTotal++;
			if (a == FightObs.ANIM_ATTACK)
			{
				int d = Arena.distToBoss(prevPx, prevPy, ob.bx, ob.by); // where we stood when it was thrown
				pending.add(new Pending(tick, tick + Arena.bossHitDelay(d), nextStyle));
			}
			else
			{
				lastSummonTick = tick;
			}
			if (++attacksThisStyle >= 4)
			{
				nextStyle = nextStyle.flip();
				attacksThisStyle = 0;
			}
		}
		else if (a == FightObs.ANIM_STOMP)
		{
			lastAttackTick = tick; // a stomp replaces the attack and doesn't count toward the 4
		}
		else if (a == FightObs.ANIM_TO_MAGIC || a == FightObs.ANIM_TO_RANGED)
		{
			nextStyle = a == FightObs.ANIM_TO_MAGIC ? Style.MAGIC : Style.RANGED;
			attacksThisStyle = 0;
		}
		Style seen = ob.projMagic + ob.projDisable > 0 ? Style.MAGIC : ob.projRanged > 0 ? Style.RANGED : null;
		if (seen != null)
		{
			Pending p = null;
			for (Pending q : pending)
			{
				if (q.animTick == tick - 1 || q.animTick == tick)
				{
					p = q;
				}
			}
			if (p != null)
			{
				styleChecks++;
				if (p.style != seen)
				{
					styleMisses++;
					// Out of sync: trust the game and restart the count from this attack.
					p.style = seen;
					nextStyle = seen;
					attacksThisStyle = 1;
				}
				p.disable |= ob.projDisable > 0;
			}
		}
		if (ob.bossProtects != protects)
		{
			protects = ob.bossProtects;
			offHits = 0;
		}
	}

	private void us(FightObs ob)
	{
		if (ob.swung)
		{
			int w = ob.weapon.ordinal();
			int gap = tick - lastSwingTick;
			if (lastSwingWeapon == ob.weapon && lastEatTick < lastSwingTick && gap == 5)
			{
				if (++slowSeen[w] >= 2)
				{
					speed[w] = 5;
				}
			}
			else if (lastSwingWeapon == ob.weapon && lastEatTick < lastSwingTick && gap == 4)
			{
				slowSeen[w] = 0;
				speed[w] = 4;
			}
			lastSwingWeapon = ob.weapon;
			lastSwingTick = tick;
			readyTick = tick + speed[w];
			swings++;
			if (protects != null && ob.weapon != protects)
			{
				offHits++;
			}
		}
		if (prevFish >= 0 && ob.fish < prevFish)
		{
			lastEatTick = tick;
			readyTick = Math.max(readyTick, tick) + 3; // eating food delays the next attack 3 ticks
		}
		prevFish = ob.fish;
	}

	private void tornadoes(FightObs ob)
	{
		Map<Integer, Integer> keep = new HashMap<>();
		for (int i = 0; i < ob.torN; i++)
		{
			Integer seen = torSeen.get(ob.torIdx[i]);
			keep.put(ob.torIdx[i], seen == null ? tick : seen);
		}
		torSeen.clear();
		torSeen.putAll(keep);
	}

	private void floor(FightObs ob)
	{
		boolean warn = false;
		boolean hit = false;
		for (byte b : ob.floor)
		{
			warn |= b == FightObs.FLOOR_WARN;
			hit |= b == FightObs.FLOOR_HIT;
		}
		int ph = FloorPatterns.phase(ob.bossHp);
		// Warnings and hits never overlap in time: a pattern warns (old hits clear the same tick),
		// then all its warnings turn into hits at once.
		if (warn && !hadWarn)
		{
			warnStart = tick;
			warnDur = patterns == 0 ? FloorPatterns.FIRST_WARN_TICKS : FloorPatterns.warnTicks(ph);
			patterns++;
		}
		if (hit && !hadHit)
		{
			hitStart = tick;
			hitDur = FloorPatterns.hitTicks(ph);
			if (warnStart >= 0)
			{
				warnDur = tick - warnStart; // measured
			}
		}
		hadWarn = warn;
		hadHit = hit;
	}

	// ------------------------------------------------------------------ reads for the brain

	/** Tick of the boss's next attack animation, -1 before its first. */
	public int nextAttackTick()
	{
		return lastAttackTick < 0 ? -1 : lastAttackTick + 5;
	}

	/** Is tick T a boss attack tick (a stomp lands on us if we're under it then)? */
	public boolean attackTickAt(int t)
	{
		int n = nextAttackTick();
		if (n < 0)
		{
			return true; // before its first attack we don't know the rhythm yet
		}
		return t >= n && (t - n) % 5 == 0;
	}

	/** The protection prayer that must be up during tick T (>= now + 1). */
	public Style protectionFor(int t)
	{
		Pending best = null;
		for (Pending p : pending)
		{
			if (p.holdUntil() >= t && (best == null || p.holdUntil() < best.holdUntil()))
			{
				best = p;
			}
		}
		return best != null ? best.style : nextStyle;
	}

	/** Is a prayer-disable orb on its way (prayers go off when it lands)? */
	public boolean disableIncoming()
	{
		for (Pending p : pending)
		{
			if (p.disable)
			{
				return true;
			}
		}
		return false;
	}

	/** Floor hazard: will tile (x,y) be an orange (hit) tile during tick T? Certain hazards only. */
	public boolean floorHit(int x, int y, int t)
	{
		byte b = o.floor[Arena.t(x, y)];
		if (b == FightObs.FLOOR_HIT)
		{
			return t < hitStart + hitDur || hitStart < 0;
		}
		if (b == FightObs.FLOOR_WARN)
		{
			int start = warnStart + warnDur;
			int ph = FloorPatterns.phase(o.bossHp);
			return t >= start && t < start + FloorPatterns.hitTicks(ph);
		}
		return false;
	}

	/** Ticks the tornado with this NPC index has existed. */
	public int tornadoAge(int idx)
	{
		Integer s = torSeen.get(idx);
		return s == null ? 0 : tick - s;
	}

	/** Learned ticks between swings with this weapon (5 = bow not on Rapid). */
	public int attackSpeed(Style w)
	{
		return speed[w.ordinal()];
	}

	/** Our next swing can happen at this tick (if we're attacking then). */
	public int readyTick()
	{
		return readyTick;
	}

	/** Weapon style the boss is NOT protecting against (staff when unknown/melee). */
	public Style weaponToUse()
	{
		return protects == Style.MAGIC ? Style.RANGED : Style.MAGIC;
	}

	public int pendingCount()
	{
		return pending.size();
	}

	public Iterator<Pending> pendingIt()
	{
		return pending.iterator();
	}
}
