package net.runelite.client.plugins.microbot.cgprep.boss;

/**
 * How a tornado moves (fitted to 1,700 recorded tornado steps, 7 Corrupted fights). Each tick a
 * tornado steps ONE tile toward the player's tile at the start of that tick:
 *  - diagonally when 2 * min(|dx|,|dy|) > max(|dx|,|dy|),
 *  - along the major axis when 2 * min < max,
 *  - either, 50/50, when 2 * min == max (e.g. offset (1,2) or (2,4)): {@link #TIE}.
 * Tornadoes appear 3 ticks after the summon animation, start moving 2 ticks after they appear and
 * vanish 20 ticks after they appear. Damage: sharing the player's tile at the end of a tick.
 */
public final class TornadoStep
{
	public static final int SPAWN_DELAY = 3;
	public static final int MOVE_DELAY = 2;
	public static final int LIFETIME = 20;

	/** Result flag for an ambiguous step (both the diagonal and the straight step are possible). */
	public static final int TIE = -2;

	private TornadoStep()
	{
	}

	/** Packed next tile, or {@link #TIE} when both options are possible (use {@link #diag}/{@link #straight}). */
	public static int step(int tx, int ty, int px, int py)
	{
		int dx = px - tx;
		int dy = py - ty;
		if (dx == 0 && dy == 0)
		{
			return Arena.t(tx, ty);
		}
		int ax = Math.abs(dx);
		int ay = Math.abs(dy);
		int mn = Math.min(ax, ay);
		int mx = Math.max(ax, ay);
		if (mn > 0 && 2 * mn == mx)
		{
			return TIE;
		}
		if (mn > 0 && 2 * mn > mx)
		{
			return diag(tx, ty, px, py);
		}
		return straight(tx, ty, px, py);
	}

	public static int diag(int tx, int ty, int px, int py)
	{
		return Arena.t(tx + Integer.signum(px - tx), ty + Integer.signum(py - ty));
	}

	public static int straight(int tx, int ty, int px, int py)
	{
		int dx = px - tx;
		int dy = py - ty;
		if (Math.abs(dx) >= Math.abs(dy))
		{
			return Arena.t(tx + Integer.signum(dx), ty);
		}
		return Arena.t(tx, ty + Integer.signum(dy));
	}

	/** Tornadoes by boss HP: 2 above 666, 3 above 333, else 4 (recon: 18 summons, 7 fights). */
	public static int count(int bossHp)
	{
		return bossHp > 666 ? 2 : bossHp > 333 ? 3 : 4;
	}
}
