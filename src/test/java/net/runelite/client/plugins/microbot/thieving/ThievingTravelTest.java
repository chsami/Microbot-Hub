package net.runelite.client.plugins.microbot.thieving;

import net.runelite.client.plugins.microbot.thieving.ThievingTravel.Decision;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThievingTravelTest
{
	@Test
	void npcNearThieves()
	{
		assertEquals(Decision.THIEVE, ThievingTravel.decide(true, true, true, true, 0));
		assertEquals(Decision.THIEVE, ThievingTravel.decide(true, false, true, false, 500));
	}

	@Test
	void farFromTargetWalksInsteadOfHopping()
	{
		assertEquals(Decision.WALK, ThievingTravel.decide(false, true, true, true, 300));
		assertEquals(Decision.WALK, ThievingTravel.decide(false, true, true, true, Integer.MAX_VALUE));
		assertEquals(Decision.WALK, ThievingTravel.decide(false, true, true, true, ThievingTravel.TARGET_RADIUS + 1));
	}

	@Test
	void onSiteWithoutNpcHops()
	{
		assertEquals(Decision.HOP, ThievingTravel.decide(false, true, true, true, ThievingTravel.TARGET_RADIUS));
		assertEquals(Decision.HOP, ThievingTravel.decide(false, true, true, true, 0));
	}

	@Test
	void noKnownTargetStopsInsteadOfHopping()
	{
		assertEquals(Decision.STOP, ThievingTravel.decide(false, true, true, false, Integer.MAX_VALUE));
	}

	@Test
	void walkDisabledKeepsLegacyHop()
	{
		assertEquals(Decision.HOP, ThievingTravel.decide(false, false, true, false, Integer.MAX_VALUE));
	}

	@Test
	void wealthyCitizenNeverHops()
	{
		assertEquals(Decision.THIEVE, ThievingTravel.decide(false, false, false, false, Integer.MAX_VALUE));
		assertEquals(Decision.THIEVE, ThievingTravel.decide(false, true, false, true, 0));
	}

	@Test
	void locationNamesUseSpawnDataCasing()
	{
		assertEquals(List.of("Man", "man"), ThievingTravel.locationNames(ThievingNpc.MEN, null));
		assertEquals("Warrior (Thieving)", ThievingTravel.locationNames(ThievingNpc.WARRIOR, null).get(0));
		assertTrue(ThievingTravel.locationNames(ThievingNpc.MASTER_FARMER, null).isEmpty());
		assertTrue(ThievingTravel.locationNames(ThievingNpc.NONE, null).isEmpty());
		assertEquals("Master Farmer", ThievingTravel.titleCase("master farmer"));
		assertEquals("Fremennik Guard", ThievingTravel.locationNames(ThievingNpc.NONE, "Fremennik Guard").get(0));
	}

	@Test
	void wildernessTilesAreDetected()
	{
		assertTrue(ThievingTravel.isWilderness(3100, 3900, 0)); // Mage Arena area
		assertTrue(ThievingTravel.isWilderness(3200, 10000, 0)); // wilderness dungeon
		assertTrue(!ThievingTravel.isWilderness(3200, 3500, 0)); // Varrock/Edgeville side
		assertTrue(!ThievingTravel.isWilderness(3200, 3968, 0));
		assertTrue(!ThievingTravel.isWilderness(2900, 3600, 0));
		assertTrue(ThievingTravel.isWildernessLocation("Deep Wilderness", 0, 0, 0));
		assertTrue(!ThievingTravel.isWildernessLocation("Draynor Village", 3100, 3250, 0));
	}
}
