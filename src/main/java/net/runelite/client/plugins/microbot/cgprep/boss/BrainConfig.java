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
}
