package net.runelite.client.plugins.microbot.cgprep.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Makes the natural fight input keep a person's rhythm instead of the game tick's. Pure (seeded
 * Random) so the simulator runs exactly this.
 *
 *  - Reaction: the first click of a tick starts after a log-normal reaction time, quicker when
 *    something is urgent (an escape step, a prayer that has to be up next tick), slower otherwise,
 *    and now and then distracted (+80-220 ms).
 *  - Slack: an action that isn't needed yet is sometimes left for a later tick, the way a player
 *    switches prayer somewhere in the 3-tick window after the boss's switch animation rather than
 *    on its first tick. Only when it would still be in time with a tick to spare: a prayer switch
 *    with 3+ ticks before the hit lands, a weapon swap / offensive prayer with 3+ / 2+ ticks before
 *    our next swing, a soft eat (above the hard HP limit), a drink above 12 prayer, Attack when the
 *    weapon isn't ready for 2+ ticks. Walks, prayer re-enables and anything urgent always go now.
 *  - Hover: the first click left for later (or the likely next one) gives the cursor somewhere to
 *    drift to between ticks, so moves don't all start on the tick.
 *  - Budget: how far into the tick a click may end varies a little per tick (520-570 ms).
 */
public final class Humanizer
{
	public static final class Tick
	{
		/** Clicks to send now, in order. */
		public final List<Click> now = new ArrayList<>();
		/** Clicks held back this tick (re-planned next tick). */
		public final List<Click> later = new ArrayList<>();
		/** Reaction before the first click (ms after the tick). */
		public double reactMs;
		/** Latest end of a click into the tick (ms). */
		public double budgetMs;

		/** Walks get a safer cut-off: one landing a tick late can walk into a tornado. */
		public double budgetFor(Click c)
		{
			return c.type == Click.Type.MOVE ? Math.min(budgetMs, 500) : budgetMs;
		}
		/** Where the cursor may drift once this tick's clicks are done (null = stay). */
		public Click hover;
		public boolean urgent;
		/** A human slip started this tick (for the log), else null. */
		public String slip;
	}

	public double delayChance = 0.25;
	/** Per overhead flip onto our weapon: one more swing with it before swapping (user, 0.4.7). */
	public double wrongWeaponChance = 0.03;
	/** Per offensive-prayer switch: keep the old one up for 1-2 more of our swings (user, 0.4.7). */
	public double lateOffenseChance = 0.07;
	private final Random r;

	// Slips. Never the protection prayers: those always go when they're due.
	private Style prevProtects;
	private boolean wieldSlip;
	private int wieldSlipTick;
	private int offenseSlipSwings;
	private int offenseSlipTick;
	private Style offenseSlipFor;
	private Style lastOffenseWant;

	public Humanizer(Random r)
	{
		this.r = r;
	}

	/** A new fight. */
	public void reset()
	{
		prevProtects = null;
		wieldSlip = false;
		offenseSlipSwings = 0;
		offenseSlipFor = null;
		lastOffenseWant = null;
	}

	/**
	 * The occasional human slip (user, 0.4.7), decided here so the simulator runs exactly this:
	 *  - the Hunllef's overhead flips onto the weapon in hand: 3% of the time the swap waits until
	 *    we've swung once more with the blocked weapon (or 6 ticks);
	 *  - the offensive prayer has to change style: 7% of the time the old one stays up for 1-2 more
	 *    of our swings (or 14 ticks).
	 * Returns the note for the log when one starts.
	 */
	private String slips(List<Click> clicks, FightObs o, int t)
	{
		String note = null;
		if (o.bossProtects != null && prevProtects != null && o.bossProtects != prevProtects && o.bossProtects == o.weapon
			&& r.nextDouble() < wrongWeaponChance)
		{
			wieldSlip = true;
			wieldSlipTick = t;
			note = "slip: one more swing with the blocked " + o.weapon;
		}
		prevProtects = o.bossProtects;
		// The swing that flipped the overhead can show on the flip tick itself: only a later one counts.
		if (wieldSlip && ((o.swung && t > wieldSlipTick) || t - wieldSlipTick > 6 || o.bossProtects != o.weapon))
		{
			wieldSlip = false;
		}
		Style want = null;
		for (Click c : clicks)
		{
			if (c.type == Click.Type.OFFENSE_ON)
			{
				want = c.style;
			}
		}
		if (!wieldSlip)
		{
			if (want != null && want != lastOffenseWant && o.offenseStyle != null && o.offenseStyle != want
				&& r.nextDouble() < lateOffenseChance)
			{
				offenseSlipSwings = 1 + r.nextInt(2);
				offenseSlipTick = t;
				offenseSlipFor = want;
				note = "slip: " + o.offenseStyle + " offensive prayer left on for " + offenseSlipSwings + " more swing(s)";
			}
			lastOffenseWant = want;
		}
		if (offenseSlipSwings > 0)
		{
			if (o.swung && t > offenseSlipTick)
			{
				offenseSlipSwings--;
			}
			if ((want != null && want != offenseSlipFor) || t - offenseSlipTick > 14)
			{
				offenseSlipSwings = 0;
			}
		}
		return note;
	}

	private boolean slipping(Click c)
	{
		return (c.type == Click.Type.WIELD && wieldSlip)
			|| (c.type == Click.Type.OFFENSE_ON && (wieldSlip || offenseSlipSwings > 0));
	}

	public Tick plan(List<Click> clicks, FightModel m, FightPlan p)
	{
		Tick out = new Tick();
		int t = m.tick;
		FightObs o = m.o;
		out.slip = slips(clicks, o, t);
		for (Click c : clicks)
		{
			if (slipping(c))
			{
				out.later.add(c);
				continue;
			}
			int slack = slack(c, m, p);
			if (slack == 0 && isUrgent(c, m, p))
			{
				out.urgent = true;
			}
			if (slack >= 1 && r.nextDouble() < delayChance)
			{
				out.later.add(c);
			}
			else
			{
				out.now.add(c);
			}
		}
		out.hover = out.later.isEmpty() ? null : out.later.get(0);
		if (out.hover != null && InputTiming.zoneFor(out.hover) == InputTiming.Zone.PANEL && (o.torN > 0 || HunllefBrain.dangerHere(m, t)))
		{
			out.hover = null; // keep the hand over the game view while dodging
		}
		if (out.urgent)
		{
			out.reactMs = clamp(36 * Math.exp(r.nextGaussian() * 0.4), 10, 110);
		}
		else
		{
			out.reactMs = clamp(85 * Math.exp(r.nextGaussian() * 0.5), 20, 260);
			if (r.nextDouble() < 0.12)
			{
				out.reactMs += 80 + r.nextDouble() * 140;
			}
		}
		out.budgetMs = 520 + r.nextDouble() * 50;
		return out;
	}

	/** How many ticks this click could wait and still be in time with a tick to spare (0 = now). */
	static int slack(Click c, FightModel m, FightPlan p)
	{
		FightObs o = m.o;
		int t = m.tick;
		int ready = m.readyTick < 0 ? t : m.readyTick;
		switch (c.type)
		{
			case PROTECT:
			{
				if (o.activeProtection() == null || o.prayersDisabled)
				{
					return 0; // re-enable at once
				}
				int land = Integer.MAX_VALUE;
				for (FightModel.Pending h : m.pending)
				{
					if (h.landTick >= t + 1)
					{
						land = Math.min(land, h.landTick); // a hit already in flight
					}
				}
				int next = m.nextAttackTick();
				if (next < 0)
				{
					return 0;
				}
				land = Math.min(land, next + 1); // the earliest the next hit can land
				return Math.max(0, land - (t + 1) - 2);
			}
			case WIELD:
				return Math.max(0, ready - (t + 1) - 2);
			case OFFENSE_ON:
				return Math.max(0, ready - (t + 1) - 1);
			case EAT:
				return o.hp > (p == null ? 50 : softEatFloor(o)) ? 1 : 0;
			case DRINK:
				return o.prayer > 12 ? 1 : 0;
			case ATTACK:
				return Math.max(0, ready - (t + 1) - 2);
			default:
				return 0;
		}
	}

	private static int softEatFloor(FightObs o)
	{
		return Math.max(35, o.hpMax / 2);
	}

	private static boolean isUrgent(Click c, FightModel m, FightPlan p)
	{
		switch (c.type)
		{
			case MOVE:
				return true; // walking is always time-critical here (tornadoes, floor)
			case PROTECT:
				return true;
			case EAT:
				return m.o.hp <= softEatFloor(m.o);
			default:
				return false;
		}
	}

	private static double clamp(double v, double lo, double hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
