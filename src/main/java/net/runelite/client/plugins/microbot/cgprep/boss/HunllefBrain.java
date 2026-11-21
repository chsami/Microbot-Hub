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
	private int lastEatSent = -100;
	private int lastDrinkSent = -100;
	public MovePlanner.Result lastMove;
	private final java.util.Random rnd;
	/** 1.8.1 long run: the end tile being run to (-1 = none), where the click went out from, our tile last tick. */
	private int runDest = -1;
	private int runFrom = -1;
	private int runLast = -1;
	/** A run click this tick's plan wants (confirmed in {@link #sent}). */
	private int runWanted = -1;
	private int runWantedFrom = -1;

	public HunllefBrain(BrainConfig c)
	{
		this(c, new java.util.Random());
	}

	public HunllefBrain(BrainConfig c, java.util.Random rnd)
	{
		this.c = c;
		this.rnd = rnd;
		this.planner = new MovePlanner(c, rnd);
	}

	public MovePlanner planner()
	{
		return planner;
	}

	/** A click really went out (the executor/sim reports it): eats and drinks are spaced from these. */
	public void sent(Click k, int tick)
	{
		if (k.type == Click.Type.MOVE)
		{
			int q = Arena.t(k.x, k.y);
			if (q == runWanted)
			{
				runDest = q;
				runFrom = runWantedFrom;
				runLast = runWantedFrom;
			}
			else
			{
				runDest = -1;
			}
		}
		else if (k.type == Click.Type.ATTACK)
		{
			runDest = -1; // attacking stops the run
		}
		if (k.type == Click.Type.EAT)
		{
			lastEatSent = tick;
		}
		else if (k.type == Click.Type.DRINK)
		{
			lastDrinkSent = tick;
		}
	}

	/**
	 * Ticks a protection click for {@code want} can wait and still be up in time: it must be on by
	 * the next attack's animation tick, or by the earliest landing of a hit already in flight that
	 * it covers. A click at tick t is on from t + 1.
	 */
	static int protectSlack(FightModel m, Style want)
	{
		FightObs o = m.o;
		int t = m.tick;
		if (want == null || want == o.activeProtection())
		{
			return 99;
		}
		int deadline = Integer.MAX_VALUE;
		for (FightModel.Pending h : m.pending)
		{
			if (h.style == want && h.holdUntil() >= t + 1)
			{
				deadline = Math.min(deadline, Math.max(t + 1, h.animTick + 1));
			}
		}
		int next = m.nextAttackTick();
		if (next < 0)
		{
			return 0;
		}
		while (next < t + 1)
		{
			next += 5;
		}
		if (m.nextStyle == want)
		{
			deadline = Math.min(deadline, next);
		}
		if (deadline == Integer.MAX_VALUE)
		{
			deadline = next;
		}
		return Math.max(0, deadline - (t + 1));
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
		int here = Arena.t(o.px, o.py);
		runWanted = -1;
		boolean keepRunning = false;
		if (c.longRuns && runDest >= 0 && r.path.length > 0)
		{
			// Still running to the last long click's tile? Off its line (a misclick), not moving (a
			// lost click) or there already: the run is over. Otherwise keep running while it costs
			// about what the new plan does: no walk click this tick.
			int dx = Arena.x(runDest);
			int dy = Arena.y(runDest);
			boolean ok = here != runDest && here != runLast
				&& MovePlanner.onWalk(Arena.x(runFrom), Arena.y(runFrom), dx, dy, here);
			if (ok)
			{
				double plan = planner.evalPath(m, r.path, MovePlanner.stepClicks(here, r.path));
				int[] rest = MovePlanner.walk(o.px, o.py, dx, dy, r.path.length);
				double run = planner.evalPath(m, rest, new boolean[r.path.length]);
				ok = run <= plan + c.runTolerance && !MovePlanner.landsUnderBoss(rest, o.bx, o.by);
			}
			if (ok)
			{
				keepRunning = true;
				p.moveX = dx;
				p.moveY = dy;
				p.continueRun = true;
				p.why = "running";
				p.urgent = dangerHere(m, t);
			}
			else
			{
				runDest = -1;
			}
		}
		runLast = here;
		if (keepRunning)
		{
			// the move is set
		}
		else if (r.x >= 0)
		{
			p.moveX = r.x;
			p.moveY = r.y;
			p.why = "planner";
			p.urgent = dangerHere(m, t);
			if (c.longRuns && rnd.nextDouble() < c.runChance)
			{
				int run = planner.runTarget(m, r, planner.evalPath(m, r.path, MovePlanner.stepClicks(here, r.path)));
				if (run >= 0)
				{
					p.moveX = Arena.x(run);
					p.moveY = Arena.y(run);
					p.why = "run";
					runWanted = run;
					runWantedFrom = here;
				}
			}
		}
		else
		{
			// 1.8.3: with the sceptre (no bow / staff), only from a tile next to the boss's side: an
			// Attack click from further out makes the game walk us in, through whatever is in the way.
			boolean meleeOk = m.weaponToUse() != Style.MELEE || Arena.meleeTile(o.px, o.py, o.bx, o.by);
			p.attack = !o.attacking && !under && meleeOk;
		}
		p.protectSlack = protectSlack(m, p.protect);
		p.moveFirst = c.dodgeFirst && p.moving() && p.urgent && p.protectSlack >= 1;

		int ready = m.readyTick < 0 ? t : m.readyTick;
		boolean swingNext = !p.moving() && !under && ready <= t + 1;

		// Food: paddlefish heal 20 and push our next swing back 3 ticks.
		int sinceEat = t - (c.eatOnSend ? lastEatSent : lastEatTick);
		int sinceDrink = t - (c.eatOnSend ? lastDrinkSent : lastDrinkTick);
		if (o.fish > 0 && sinceEat >= 3 && o.hp <= o.hpMax - 18)
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
		if (o.doses > 0 && sinceDrink >= 3 && o.prayer <= c.drinkBelow)
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
