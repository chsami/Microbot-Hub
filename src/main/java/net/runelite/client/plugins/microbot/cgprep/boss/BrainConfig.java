package net.runelite.client.plugins.microbot.cgprep.boss;

/** Tunables of the fight brain (defaults tuned in the simulator, see tools/sim/FightBench). */
public final class BrainConfig
{
	public enum Offense
	{
		OFF, FLICK, ALWAYS
	}

	public Offense offense = Offense.ALWAYS;
	/** Eat at or below this HP no matter what. */
	public int eatBelow = 50;
	/** Eat at or below this HP when the next swing is still 3+ ticks away (the eat delay costs nothing then... less). */
	public int eatBelowSoft = 62;
	/** Drink a dose at or below this many prayer points. */
	public int drinkBelow = 22;
	/**
	 * 1.8.0: an escape (urgent move) is clicked before a protection prayer that can still wait a
	 * tick. The natural mouse gets about one panel click a tick (F-key + travel), so prayer-first
	 * left the dodge for the next tick: 4 tornadoes for 58 (log 11-02 22:05), 2 for 38 (11-01).
	 */
	public boolean dodgeFirst = true;
	/**
	 * 1.8.0: the 3 ticks between eats (and drinks) count from the click that really went out, not
	 * from the plan. A planned eat that didn't fit the tick was otherwise not planned again for
	 * 3 ticks (11-01: died at 7 HP with 7 fish).
	 */
	public boolean eatOnSend = true;

	// ---- movement planner
	public int horizon = 6;
	public int beam = 160;
	public double wFloor = 15;
	public double wTornado = 18;
	public double wStomp = 80;
	public double wUnderBoss = 6;
	public double wNear1 = 4;
	public double wNear2 = 1;
	/** Cost of a tick where our swing is ready but we're not standing to take it. */
	public double wIdle = 3.5;
	public double wMove = 0.35;
	public double wTrap = 25;
	public double wCramped = 5;
	public double wWarnSoon = 8;
	/** Chance a click lands a tick late, used to price moves that only just escape. */
	public double pLate = 0.15;
	/** Discount per tick into the future (later ticks are less certain). */
	public double decay = 0.92;

	// ---- 1.8.1 (user): less mechanical dodging
	/**
	 * Leave a warned floor tile 0 to this many ticks before it has to be left (drawn per floor
	 * pattern). Always leaving on the last safe tick looked scripted. 0 = always the last tick.
	 */
	public double floorLeadMax = 2;
	/** Cost per tick of standing on a tile that turns orange within the drawn lead. */
	public double wFloorEarly = 6;
	/**
	 * Distance kept from tornadoes, drawn per tornado phase: 1 tile (as before) with pClear1, 2 with
	 * pClear2, else 3. Always ending exactly 1 tile from them looked scripted. Soft: it gives way when
	 * the arena is tight.
	 */
	public double pClear1 = 0.4;
	public double pClear2 = 0.4;
	/** Cost per tornado within the drawn distance (2 or 3 tiles). */
	public double wClear = 1.5;
	/**
	 * One click to the end of a planned run (3+ tiles) instead of a 2-tile click every tick, when
	 * running there costs about the same as the step-by-step plan; the ticks without a walk click
	 * are free for food, prayer and weapon clicks. Single steps stay for precise dodging.
	 */
	public boolean longRuns = true;
	/** Chance a qualifying run is clicked as one long click (people don't always). */
	public double runChance = 0.85;
	/** Extra planner cost accepted for running straight to the end tile (or keeping on running). */
	public double runTolerance = 1.0;
	/** Longest run click, in tiles. */
	public double runMaxTiles = 8;
	/**
	 * Never end a tick under the boss unless already there (user, 1.8.1: a stomp hits up to ~50
	 * through any prayer; the planner used to accept it as a price when nothing was due to land).
	 */
	public boolean neverUnderBoss = true;
	/**
	 * Cost of clicking a tile right next to the boss: a misclick (about 1 in 10 in game) lands a
	 * tile off, possibly under it, and the boss also shifts a tile now and then (1.8.1).
	 */
	public double wBossEdge = 3;
}
