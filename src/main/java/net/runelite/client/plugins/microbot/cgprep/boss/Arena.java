package net.runelite.client.plugins.microbot.cgprep.boss;

/**
 * The Hunllef arena: 12 x 12 tiles, arena-relative coordinates (0,0) = south-west tile. In game the
 * arena starts 2 tiles into the 16 x 16 boss room (recon: x0 % 16 == 2 and y0 % 16 == 2 in all 7
 * recorded Corrupted fights). Tiles are packed as x + 12 * y.
 */
public final class Arena
{
	public static final int SIZE = 12;
	public static final int TILES = SIZE * SIZE;
	public static final int BOSS_SIZE = 5;

	private Arena()
	{
	}

	public static int t(int x, int y)
	{
		return x + SIZE * y;
	}

	public static int x(int t)
	{
		return t % SIZE;
	}

	public static int y(int t)
	{
		return t / SIZE;
	}

	public static boolean in(int x, int y)
	{
		return x >= 0 && y >= 0 && x < SIZE && y < SIZE;
	}

	public static int cheb(int ax, int ay, int bx, int by)
	{
		return Math.max(Math.abs(ax - bx), Math.abs(ay - by));
	}

	/** Is (x,y) under the 5 x 5 boss whose south-west tile is (bx,by)? */
	public static boolean underBoss(int x, int y, int bx, int by)
	{
		return x >= bx && x < bx + BOSS_SIZE && y >= by && y < by + BOSS_SIZE;
	}

	/** Tiles from (x,y) to the nearest tile of the boss footprint (0 = under it, 1 = next to it). */
	public static int distToBoss(int x, int y, int bx, int by)
	{
		int dx = Math.max(0, Math.max(bx - x, x - (bx + BOSS_SIZE - 1)));
		int dy = Math.max(0, Math.max(by - y, y - (by + BOSS_SIZE - 1)));
		return Math.max(dx, dy);
	}

	/**
	 * Ticks from a boss attack animation to the hit landing on us (recon, 7 fights, 200+ attacks):
	 * 1 tick when we're within 2 tiles of its footprint, 2 ticks from 3 tiles out.
	 */
	public static int bossHitDelay(int distToBoss)
	{
		return distToBoss <= 2 ? 1 : 2;
	}

	/** Arena origin from a raw scene coordinate inside the boss room (room boundaries are multiples of 16). */
	public static int originOf(int raw)
	{
		return (raw & ~15) + 2;
	}
}
