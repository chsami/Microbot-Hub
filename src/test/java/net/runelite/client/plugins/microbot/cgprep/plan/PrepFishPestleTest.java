package net.runelite.client.plugins.microbot.cgprep.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.cgprep.data.Resource;
import net.runelite.client.plugins.microbot.cgprep.map.GauntletMaze;
import org.junit.jupiter.api.Test;

/** 1.8.5 player feedback: fish before going home, the pestle dropped once its dust is made, monster-dropped fish looted. */
class PrepFishPestleTest
{
	private static Ledger bag(int free)
	{
		Ledger l = new Ledger();
		l.freeSlots = free;
		return l;
	}

	private static Needs needs(int fish)
	{
		Needs n = new Needs();
		for (Resource r : Resource.values())
		{
			n.deficit.put(r, 0);
		}
		n.deficit.put(Resource.RAW_FISH, fish);
		return n;
	}

	@Test
	void pestleDoneOnceEveryBrewHasItsDust()
	{
		Ledger l = bag(2);
		l.pestle = true;
		l.dust = 20;
		Needs n = needs(4);
		n.potionsToMake = 2;
		assertTrue(PrepPlanner.pestleDone(l, n));
		n.potionsToMake = 3;
		assertFalse(PrepPlanner.pestleDone(l, n));
	}

	@Test
	void theDroppingPestleAndAGrymIntoWaterCountAsRoomForFish()
	{
		Ledger l = bag(2);
		l.pestle = true;
		l.dust = 10;
		l.waterVials = 1;
		l.have.put(Resource.GRYM, 1);
		Needs n = needs(4);
		n.potionsToMake = 1;
		assertEquals(2, PrepPlanner.freeingSlots(l, n)); // the pestle + one herb step
		assertEquals(0, PrepPlanner.freeingSlots(bag(0), n)); // a full bag waits for the real room
	}

	@Test
	void onlyFishLeftIgnoresTheOptionalHerb()
	{
		Needs n = needs(4);
		assertTrue(PrepPlanner.onlyFishLeft(n, false));
		n.deficit.put(Resource.GRYM, 1);
		assertFalse(PrepPlanner.onlyFishLeft(n, false));
		assertTrue(PrepPlanner.onlyFishLeft(n, true));
		n.deficit.put(Resource.LINUM, 2);
		assertFalse(PrepPlanner.onlyFishLeft(n, true));
		assertFalse(PrepPlanner.onlyFishLeft(needs(0), false));
	}

	@Test
	void lootTakesFishTheGatheringCapLeftOut()
	{
		PrepPlanner.Inputs in = new PrepPlanner.Inputs();
		in.ledger = bag(3);
		in.needs = needs(4);
		in.gather = in.needs.withCap(Resource.RAW_FISH, 0);
		assertEquals(4, PrepPlanner.lootNeeds(in).get(Resource.RAW_FISH));
		in.ledger = bag(0);
		assertEquals(0, PrepPlanner.lootNeeds(in).get(Resource.RAW_FISH)); // no slot: the cap stands
	}

	@Test
	void aFishDroppedForRoomInTheStartRoomIsNotLootedBack()
	{
		GauntletMaze home = new GauntletMaze();
		home.restoreForReplay(3, 3, 3, 3, new WorldPoint(1900, 5700, 1), 1852, 5652, new boolean[49],
			new java.util.ArrayList<>(), new java.util.HashMap<>(), new java.util.HashSet<>());
		PrepPlanner.Inputs in = new PrepPlanner.Inputs();
		in.maze = home;
		in.ledger = bag(1);
		in.needs = needs(4);
		in.gather = in.needs.withCap(Resource.RAW_FISH, 0);
		assertTrue(home.inStartRoom());
		assertEquals(0, PrepPlanner.lootNeeds(in).get(Resource.RAW_FISH));
	}

	@Test
	void aFishJustDroppedIsNotLootedBack()
	{
		PrepPlanner.Inputs in = new PrepPlanner.Inputs();
		in.ledger = bag(3);
		in.needs = needs(4);
		in.gather = in.needs.withCap(Resource.RAW_FISH, 0);
		in.droppedRecently = java.util.EnumSet.of(Resource.RAW_FISH);
		assertEquals(0, PrepPlanner.lootNeeds(in).get(Resource.RAW_FISH));
	}
}
