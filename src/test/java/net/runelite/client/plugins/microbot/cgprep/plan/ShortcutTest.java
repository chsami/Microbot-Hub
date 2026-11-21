package net.runelite.client.plugins.microbot.cgprep.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/**
 * 1.8.3: the room-level way home / to a target may go through unlit rooms. Maps are 7 rows, top =
 * z 6; 'L' = lit, '.' = unlit, 'B' = the boss room, 'x' = an unlit room we failed to light.
 */
class ShortcutTest
{
	private static BiPredicate<Integer, Integer> at(String[] rows, char c)
	{
		return (x, z) -> rows[6 - z].charAt(x) == c;
	}

	private static int[] path(String[] rows, int fx, int fz, int tx, int tz, boolean unlit, boolean demiUnknown)
	{
		return PrepPlanner.roomPath(at(rows, 'L'), at(rows, 'x'), fx, fz, tx, tz, unlit, demiUnknown);
	}

	// A snake of lit rooms from the start (3,2) round to (4,4): lit-only goes the long way.
	private static final String[] SNAKE = {
		".......",
		".......",
		"..LLL..",
		"..LBL..",
		"..LLL..",
		".......",
		"......."};

	@Test
	void litOnlyFollowsTheLitRooms()
	{
		int[] p = path(SNAKE, 4, 4, 3, 2, false, false);
		assertEquals(-1, p[0]);
		assertEquals(3 * 16, p[2]); // 4,4 -> 4,3 -> 4,2 -> 3,2
	}

	@Test
	void anUnlitRoomCanBeTheShorterWay()
	{
		// Home at 3,6 (top middle): lit way round doesn't exist, through the unlit 3,5 it's 2 rooms.
		String[] m = {
			"...L...",
			".......",
			"..LLL..",
			"..LBL..",
			"..LLL..",
			".......",
			"......."};
		int[] lit = path(m, 3, 4, 3, 6, false, false);
		int[] any = path(m, 3, 4, 3, 6, true, false);
		assertNull(lit);
		assertEquals(3, any[0]);
		assertEquals(5, any[1]);
		assertEquals(16 + 8 + 16, any[2]);
	}

	@Test
	void lightingCostsSoAShortLitWayStays()
	{
		// lit way 2 rooms; an unlit way of 2 rooms would cost more, so the lit one wins
		String[] m = {
			".......",
			".......",
			"..LL...",
			"...B...",
			".......",
			".......",
			"......."};
		int[] any = path(m, 2, 4, 3, 4, true, false);
		assertEquals(-1, any[0]);
		assertEquals(16, any[2]);
	}

	@Test
	void outerRoomsCostMoreWhileADemiIsUnaccountedFor()
	{
		String[] m = {
			"L.L....",
			".......",
			".......",
			"...B...",
			".......",
			".......",
			"......."};
		int[] known = path(m, 0, 6, 2, 6, true, false);
		int[] unknown = path(m, 0, 6, 2, 6, true, true);
		assertTrue(unknown[2] > known[2]);
	}

	@Test
	void neverThroughTheBossRoomOrAFailedRoom()
	{
		String[] m = {
			".......",
			".......",
			"...L...",
			"..xB...",
			"...L...",
			".......",
			"......."};
		int[] any = path(m, 3, 4, 3, 2, true, false);
		// straight down is the boss room, left is a room we failed to light: round the right side
		assertEquals(4, any[0]);
	}
}
