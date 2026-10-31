package net.runelite.client.plugins.microbot.arceuuslibrary;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryTravelTest
{
	private static final WorldPoint TARGET = LibraryTravel.ENTRANCE;

	@Test
	void libraryBounds()
	{
		assertTrue(LibraryTravel.isInsideLibrary(new WorldPoint(1627, 3801, 0)));
		assertTrue(LibraryTravel.isInsideLibrary(new WorldPoint(1610, 3790, 2)));
		assertFalse(LibraryTravel.isInsideLibrary(new WorldPoint(1625, 3305, 0)), "Varlamore");
		assertFalse(LibraryTravel.isInsideLibrary(new WorldPoint(1627, 3801, 3)));
		assertFalse(LibraryTravel.isInsideLibrary(new WorldPoint(1661, 3801, 0)));
		assertFalse(LibraryTravel.isInsideLibrary(null));
	}

	@Test
	void backoffGrowsAndCaps()
	{
		assertEquals(0, LibraryTravel.backoffMillis(0));
		assertEquals(20_000, LibraryTravel.backoffMillis(1));
		assertEquals(40_000, LibraryTravel.backoffMillis(2));
		assertEquals(60_000, LibraryTravel.backoffMillis(3));
		assertEquals(60_000, LibraryTravel.backoffMillis(50));
	}

	@Test
	void walkDedupAndBackoff()
	{
		assertTrue(LibraryTravel.shouldStartWalk(null, TARGET, 1_000, 0));
		assertFalse(LibraryTravel.shouldStartWalk(TARGET, TARGET, 1_000, 0), "walk already in flight");
		assertFalse(LibraryTravel.shouldStartWalk(null, TARGET, 1_000, 5_000), "backing off");
		assertTrue(LibraryTravel.shouldStartWalk(null, TARGET, 5_000, 5_000), "back-off elapsed");
		assertFalse(LibraryTravel.shouldStartWalk(null, null, 1_000, 0));
	}
}
