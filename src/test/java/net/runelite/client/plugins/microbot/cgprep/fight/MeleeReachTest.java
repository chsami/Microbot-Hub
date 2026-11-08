package net.runelite.client.plugins.microbot.cgprep.fight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 1.8.1: {@link MeleeReach} on drawn maps. '#' = blocked tile, 'P' = us, 'M' = the monster's
 * south-west tile; row 0 is the top (north), so y grows upwards like world coordinates.
 */
class MeleeReachTest
{
	private static final class Map implements MeleeReach.Collision
	{
		final String[] rows;
		int px, py, mx, my;

		Map(String... rows)
		{
			this.rows = rows;
			for (int r = 0; r < rows.length; r++)
			{
				for (int x = 0; x < rows[r].length(); x++)
				{
					char ch = rows[r].charAt(x);
					int y = rows.length - 1 - r;
					if (ch == 'P')
					{
						px = x;
						py = y;
					}
					else if (ch == 'M')
					{
						mx = x;
						my = y;
					}
				}
			}
		}

		boolean blocked(int x, int y)
		{
			int r = rows.length - 1 - y;
			return r < 0 || r >= rows.length || x < 0 || x >= rows[r].length() || rows[r].charAt(x) == '#';
		}

		boolean fits(int x, int y, int size)
		{
			for (int i = 0; i < size; i++)
			{
				for (int j = 0; j < size; j++)
				{
					if (blocked(x + i, y + j))
					{
						return false;
					}
				}
			}
			return true;
		}

		@Override
		public boolean canStep(int x, int y, int size, int dx, int dy)
		{
			if (dx != 0 && dy != 0)
			{
				// a diagonal step needs both straight steps free too (the game's corner rule)
				return fits(x + dx, y + dy, size) && fits(x + dx, y, size) && fits(x, y + dy, size);
			}
			return fits(x + dx, y + dy, size);
		}

		boolean reach(int size)
		{
			return MeleeReach.canReach(mx, my, size, px, py, this);
		}
	}

	@Test
	void openRoomReaches()
	{
		Map m = new Map(
			".......",
			"..M....",
			".......",
			".......",
			"....P..");
		assertTrue(m.reach(1));
		assertTrue(m.reach(2));
	}

	@Test
	void longWallBlocks()
	{
		Map m = new Map(
			"...M...",
			".......",
			"#######",
			".......",
			"...P...");
		assertFalse(m.reach(1));
	}

	@Test
	void wallWithAGapStraightAheadReaches()
	{
		Map m = new Map(
			"...M...",
			".......",
			"###.###",
			".......",
			"...P...");
		assertTrue(m.reach(1));
	}

	@Test
	void gapOffToTheSideTrapsAStraightWalker()
	{
		// It walks straight at us into the wall and never goes round to the gap.
		Map m = new Map(
			"...M.....",
			".........",
			"#######..",
			".........",
			"...P.....");
		assertFalse(m.reach(1));
	}

	@Test
	void oneTileGapStopsABigMonster()
	{
		Map m = new Map(
			"...M...",
			".......",
			"...#...",
			"###.###",
			".......",
			"...P...");
		// the M tile is the south-west corner: a 2x2 at (3,4)..(4,5) can't fit the 1-wide gap at x=3
		m.mx = 3;
		m.my = 4;
		assertFalse(m.reach(2));
		Map small = new Map(
			"...M...",
			".......",
			"#######",
			"###.###",
			".......",
			"...P...");
		assertFalse(small.reach(1)); // sanity: fully walled
	}

	@Test
	void narrowGapLetsASmallOneThroughButNotABigOne()
	{
		Map m = new Map(
			"...M....",
			"........",
			"........",
			"###.####",
			"........",
			"...P....");
		assertTrue(m.reach(1));
		m.mx = 3;
		m.my = 4;
		assertFalse(m.reach(2));
	}

	@Test
	void diagonalWithBothSidesBlockedCantHit()
	{
		Map m = new Map(
			".....",
			"..M#.",
			"..#P.",
			".....");
		assertFalse(m.reach(1));
	}

	@Test
	void nextToUsHits()
	{
		Map m = new Map(
			".....",
			"..MP.",
			".....");
		assertTrue(m.reach(1));
	}
}
