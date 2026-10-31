package net.runelite.client.plugins.microbot.cgprep.boss;

/**
 * The fight decision for the coming tick, from the {@link FightModel}. Pure: no game calls, so the
 * simulator (tools/sim) runs exactly this.
 *
 *  1. Protection: the style of the next boss hit to land (in flight first, else the predicted style).
 *  2. Weapon: the one the boss's overhead doesn't block (staff vs Protect from Missiles, bow vs
 *     Protect from Magic); reactive to the overhead change on our 6th off-prayer swing.
 *  3. Movement: {@link MovePlanner} (floor, tornadoes, stomp vs. lost attack ticks).
 *  4. Food/potion thresholds.
 *  5. Offensive prayer: on for the tick of each swing only (lazy flick) by default.
 */
public final class HunllefBrain
{
	private final BrainConfig c;
	private final MovePlanner planner;
	private int lastEatTick = -100;
	private int lastDrinkTick = -100;
	public MovePlanner.Result lastMove;

	public HunllefBrain(BrainConfig c)
	{
		this.c = c;
		this.planner = new MovePlanner(c);
	}

	/** Would standing still on our tile risk damage in the next 2 ticks? */
	static boolean dangerHere(FightModel m, int t)
	{
		FightObs o = m.o;
		for (int k = 1; k <= 2; k++)
		{
			if (m.floorHit(o.px, o.py, t + k))
			{
				return true;
			}
			if (Arena.underBoss(o.px, o.py, o.bx, o.by) && m.attackTickAt(t + k))
			{
				return true;
			}
		}
		for (int i = 0; i < o.torN; i++)
		{
			if (Arena.cheb(o.px, o.py, o.torX[i], o.torY[i]) <= 2)
			{
				return true;
			}
		}
		return false;
	}

	public FightPlan decide(FightModel m)
	{
		FightObs o = m.o;
		int t = m.tick;
		FightPlan p = new FightPlan();

		p.protect = m.protectionFor(t + 1);
		p.weapon = m.weaponToUse();

		MovePlanner.Result r = planner.plan(m);
		lastMove = r;
		p.cost = r.cost;
		boolean under = Arena.underBoss(o.px, o.py, o.bx, o.by);
		if (r.x >= 0)
		{
			p.moveX = r.x;
			p.moveY = r.y;
			p.why = "planner";
			p.urgent = dangerHere(m, t);
		}
		else
		{
			p.attack = !o.attacking && !under;
		}

		int ready = m.readyTick < 0 ? t : m.readyTick;
		boolean swingNext = !p.moving() && !under && ready <= t + 1;

		// Food: paddlefish heal 20 and push our next swing back 3 ticks.
		if (o.fish > 0 && t - lastEatTick >= 3 && o.hp <= o.hpMax - 18)
		{
			boolean hard = o.hp <= c.eatBelow;
			boolean soft = o.hp <= c.eatBelowSoft && ready >= t + 3;
			if (hard || soft)
			{
				p.eat = true;
				lastEatTick = t;
			}
		}
		// Egniol dose: about 7 + level / 4 prayer.
		if (o.doses > 0 && t - lastDrinkTick >= 3 && o.prayer <= c.drinkBelow)
		{
			p.drink = true;
			lastDrinkTick = t;
		}

		switch (c.offense)
		{
			case ALWAYS:
				p.offense = o.prayer > 1 || p.drink;
				break;
			case FLICK:
				p.offense = swingNext && (o.prayer > 1 || p.drink);
				break;
			default:
				p.offense = false;
		}
		return p;
	}
}
