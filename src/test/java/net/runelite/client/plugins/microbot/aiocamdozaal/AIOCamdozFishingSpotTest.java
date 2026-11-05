package net.runelite.client.plugins.microbot.aiocamdozaal;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AIOCamdozFishingSpotTest
{
	@Test
	void spotNextToAnchorIsUsed()
	{
		assertTrue(AIOCamdozScript.isFishingSpotInArea(new WorldPoint(2929, 5777, 0)));
		assertTrue(AIOCamdozScript.isFishingSpotInArea(AIOCamdozScript.FISHING_LOCATION));
	}

	@Test
	void spotNorthOfAreaIsSkipped()
	{
		assertFalse(AIOCamdozScript.isFishingSpotInArea(new WorldPoint(2929, 5784, 0)));
		assertFalse(AIOCamdozScript.isFishingSpotInArea(new WorldPoint(2929, 5783, 0)));
	}

	@Test
	void spotOnAreaEdgeIsSkippedSoTheStandingTileStaysInside()
	{
		WorldPoint anchor = AIOCamdozScript.FISHING_LOCATION;
		int range = AIOCamdozScript.FISHING_AREA_RANGE;
		assertTrue(AIOCamdozScript.isFishingSpotInArea(new WorldPoint(anchor.getX(), anchor.getY() + range - 1, 0)));
		assertFalse(AIOCamdozScript.isFishingSpotInArea(new WorldPoint(anchor.getX(), anchor.getY() + range, 0)));
		assertFalse(AIOCamdozScript.isFishingSpotInArea(new WorldPoint(anchor.getX() - range, anchor.getY(), 0)));
	}

	@Test
	void missingOrOtherPlaneSpotIsSkipped()
	{
		assertFalse(AIOCamdozScript.isFishingSpotInArea(null));
		assertFalse(AIOCamdozScript.isFishingSpotInArea(new WorldPoint(2930, 5776, 1)));
	}
}
